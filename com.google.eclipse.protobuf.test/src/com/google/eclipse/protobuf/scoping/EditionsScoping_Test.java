package com.google.eclipse.protobuf.scoping;

import static com.google.eclipse.protobuf.junit.core.IntegrationTestModule.integrationTestModule;
import static com.google.eclipse.protobuf.junit.core.XtextRule.overrideRuntimeModuleWith;
import static com.google.eclipse.protobuf.protobuf.ProtobufPackage.Literals.ANY_FIELD_NAME__EMBEDDED_TYPE;
import static com.google.eclipse.protobuf.protobuf.ProtobufPackage.Literals.OPTION_FIELD__TARGET;
import static com.google.eclipse.protobuf.protobuf.ProtobufPackage.Literals.OPTION_SOURCE__TARGET;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.diagnostics.Diagnostic;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.eclipse.xtext.validation.Issue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.eclipse.protobuf.junit.core.XtextRule;
import com.google.eclipse.protobuf.model.util.FeatureSets;
import com.google.eclipse.protobuf.protobuf.AnyFieldName;
import com.google.eclipse.protobuf.protobuf.ComplexTypeLink;
import com.google.eclipse.protobuf.protobuf.Import;
import com.google.eclipse.protobuf.protobuf.Message;
import com.google.eclipse.protobuf.protobuf.MessageField;
import com.google.eclipse.protobuf.protobuf.Protobuf;
import com.google.eclipse.protobuf.protobuf.Rpc;
import com.google.eclipse.protobuf.protobuf.TypeExtension;
import com.google.eclipse.protobuf.validation.EditionValidator;
import com.google.eclipse.protobuf.validation.Messages;
import com.google.inject.Inject;

/** File-backed parser/linker/validator contracts, including the identities behind option member scopes. */
public class EditionsScoping_Test {
  @Rule public XtextRule xtext = overrideRuntimeModuleWith(integrationTestModule());
  @Rule public TemporaryFolder temporary = new TemporaryFolder();
  @Inject private IResourceValidator validator;
  @Inject private ImportVisibility visibility;
  @Inject private FeatureSets features;
  @Inject private ProtoDescriptorProvider descriptors;
  @Inject private ProtobufImportedNamespaceAwareLocalScopeProvider localScopes;

  private static final String DESCRIPTOR = "import 'google/protobuf/descriptor.proto'; ";
  private static final String ANY_IMPORT = "import '" + new File(
      "resources/protobuf-java-4.36.2/google/protobuf/any.proto").toURI() + "'; ";

  @Test public void should_apply_provider_visibility_to_editions_and_both_legacy_consumers() throws Exception {
    for (String dialect : new String[] {"2024", "proto2", "proto3"}) {
      Corpus corpus = corpus();
      corpus.write("provider.proto", header("2024") + "package provider; "
          + "message Top { message Nested {} local message PrivateNested {} "
          + "export message PublicNested {} } local message Hidden {} "
          + "message SameFile { Hidden hidden=1; Top.Nested nested=2; Top.PrivateNested private_nested=3; }");
      corpus.write("consumer.proto", header(dialect) + corpus.importOf("", "provider.proto")
          + "message Consumer { " + label(dialect) + "provider.Top top=1; "
          + label(dialect) + "provider.Top.Nested nested=2; "
          + label(dialect) + "provider.Hidden hidden=3; "
          + label(dialect) + "provider.Top.PrivateNested private_nested=4; "
          + label(dialect) + "provider.Top.PublicNested public_nested=5; }");
      Protobuf provider = corpus.load("provider.proto");
      assertClean(provider);
      assertFieldTarget(provider, "hidden", named(provider, "Hidden", Message.class));
      assertFieldTarget(provider, "nested", named(provider, "Nested", Message.class));
      assertFieldTarget(provider, "private_nested", named(provider, "PrivateNested", Message.class));
      Protobuf consumer = corpus.load("consumer.proto");
      List<Issue> issues = issues(consumer);
      assertCount(issues, Diagnostic.LINKING_DIAGNOSTIC, 3);
      assertFieldTarget(consumer, "top", named(provider, "Top", Message.class));
      assertFieldTarget(consumer, "public_nested", named(provider, "PublicNested", Message.class));
      for (String name : new String[] {"nested", "hidden", "private_nested"}) {
        assertUnresolvedField(consumer, name);
        assertOn(issues, Diagnostic.LINKING_DIAGNOSTIC, named(consumer, name, MessageField.class));
      }
      assertNoCode(issues, EditionValidator.EDITION_VISIBILITY);
      assertNoCode(issues, EditionValidator.EDITIONS_FEATURES_UNAVAILABLE);
    }
  }

  @Test public void should_use_each_effective_visibility_default_without_hiding_explicit_exports() throws Exception {
    for (String mode : new String[] {"EXPORT_ALL", "EXPORT_TOP_LEVEL", "LOCAL_ALL", "STRICT"}) {
      Corpus corpus = corpus();
      corpus.write("provider.proto", header("2024") + "package provider; "
          + "option features.default_symbol_visibility=" + mode + "; "
          + "message Top { message Nested {} enum NestedEnum { NESTED_ZERO=0; } } "
          + "enum TopEnum { TOP_ZERO=0; } export message Explicit {} local message Hidden {}");
      corpus.write("consumer.proto", header("2024") + corpus.importOf("", "provider.proto")
          + "message Consumer { provider.Top top=1; provider.Top.Nested nested=2; "
          + "provider.Top.NestedEnum nested_enum=3; provider.TopEnum top_enum=4; "
          + "provider.Explicit explicit_value=5; provider.Hidden hidden=6; }");
      Protobuf provider = corpus.load("provider.proto");
      assertClean(provider);
      Protobuf consumer = corpus.load("consumer.proto");
      boolean top = "EXPORT_ALL".equals(mode) || "EXPORT_TOP_LEVEL".equals(mode);
      boolean nested = "EXPORT_ALL".equals(mode);
      assertVisibility(consumer, "top", named(provider, "Top", Message.class), top);
      assertVisibility(consumer, "top_enum", named(provider, "TopEnum", com.google.eclipse.protobuf.protobuf.Enum.class), top);
      assertVisibility(consumer, "nested", named(provider, "Nested", Message.class), nested);
      assertVisibility(consumer, "nested_enum", named(provider, "NestedEnum", com.google.eclipse.protobuf.protobuf.Enum.class), nested);
      assertFieldTarget(consumer, "explicit_value", named(provider, "Explicit", Message.class));
      assertUnresolvedField(consumer, "hidden");
      assertCount(issues(consumer), Diagnostic.LINKING_DIAGNOSTIC, 1 + (top ? 0 : 2) + (nested ? 0 : 2));
    }
  }

  @Test public void should_expose_nested_exports_independently_of_a_local_ancestor() throws Exception {
    Corpus corpus = corpus();
    corpus.write("provider.proto", header("2024") + "package provider; local message Namespace { "
        + "export message PublicChild {} export enum Choice { CHOICE_ZERO=0; } message PrivateChild {} }");
    corpus.write("consumer.proto", header("2024") + corpus.importOf("", "provider.proto")
        + "message Consumer { provider.Namespace.PublicChild child=1; provider.Namespace.Choice choice=2; "
        + "provider.Namespace namespace_value=3; provider.Namespace.PrivateChild private_child=4; }");
    Protobuf provider = corpus.load("provider.proto");
    assertClean(provider);
    Protobuf consumer = corpus.load("consumer.proto");
    assertFieldTarget(consumer, "child", named(provider, "PublicChild", Message.class));
    assertFieldTarget(consumer, "choice", named(provider, "Choice", com.google.eclipse.protobuf.protobuf.Enum.class));
    assertUnresolvedField(consumer, "namespace_value");
    assertUnresolvedField(consumer, "private_child");
    assertCount(issues(consumer), Diagnostic.LINKING_DIAGNOSTIC, 2);
  }

  @Test public void should_not_hide_types_declared_before_2024_even_with_invalid_local_modifiers() throws Exception {
    for (String dialect : new String[] {"2023", "proto2", "proto3"}) {
      Corpus corpus = corpus();
      corpus.write("provider.proto", header(dialect)
          + "package provider; local message Top { local message Nested {} }");
      corpus.write("consumer.proto", header("2024") + corpus.importOf("", "provider.proto")
          + "message Consumer { provider.Top top=1; provider.Top.Nested nested=2; }");
      Protobuf provider = corpus.load("provider.proto");
      assertCount(issues(provider), EditionValidator.EDITION_VISIBILITY, 2);
      Protobuf consumer = corpus.load("consumer.proto");
      assertClean(consumer);
      assertFieldTarget(consumer, "top", named(provider, "Top", Message.class));
      assertFieldTarget(consumer, "nested", named(provider, "Nested", Message.class));
    }
  }

  @Test public void should_allow_strict_enum_namespaces_with_explicit_or_default_local_containers() throws Exception {
    Corpus corpus = corpus();
    corpus.write("provider.proto", header("2024") + "package provider; "
        + "option features.default_symbol_visibility=STRICT; "
        + "message Namespace { reserved 1 to max; export enum Choice { CHOICE_ZERO=0; } } "
        + "local message ExplicitNamespace { reserved 1 to max; export enum OtherChoice { OTHER_ZERO=0; } } "
        + "export message Public {} message SameFile { Namespace local_type=1; Namespace.Choice local_enum=2; }");
    corpus.write("consumer.proto", header("2024") + corpus.importOf("", "provider.proto")
        + "message Consumer { provider.Namespace.Choice choice=1; "
        + "provider.ExplicitNamespace.OtherChoice other_choice=2; provider.Public public_value=3; "
        + "provider.Namespace namespace_value=4; }");
    Protobuf provider = corpus.load("provider.proto");
    assertClean(provider);
    assertFieldTarget(provider, "local_type", named(provider, "Namespace", Message.class));
    Protobuf consumer = corpus.load("consumer.proto");
    assertFieldTarget(consumer, "choice", named(provider, "Choice", com.google.eclipse.protobuf.protobuf.Enum.class));
    assertFieldTarget(consumer, "other_choice", named(provider, "OtherChoice", com.google.eclipse.protobuf.protobuf.Enum.class));
    assertFieldTarget(consumer, "public_value", named(provider, "Public", Message.class));
    assertUnresolvedField(consumer, "namespace_value");
    assertCount(issues(consumer), Diagnostic.LINKING_DIAGNOSTIC, 1);
  }

  @Test public void should_reject_strict_nested_exports_outside_the_single_full_range_enum_namespace() throws Exception {
    for (String body : new String[] {
        "message Outer { export message Forbidden {} }",
        "message Outer { export enum Forbidden { ZERO=0; } }",
        "message Outer { reserved 2 to max; export enum Forbidden { ZERO=0; } }",
        "message Outer { reserved 1 to 10, 11 to max; export enum Forbidden { ZERO=0; } }",
        "message Outer { message Inner { reserved 1 to max; export enum Forbidden { ZERO=0; } } }",
        "export message Outer { reserved 1 to max; export enum Forbidden { ZERO=0; } }"}) {
      Corpus corpus = corpus();
      corpus.write("provider.proto", header("2024") + "option features.default_symbol_visibility=STRICT; " + body);
      Protobuf provider = corpus.load("provider.proto");
      List<Issue> issues = issues(provider);
      assertCount(issues, EditionValidator.EDITION_VISIBILITY, 1);
      EObject forbidden = body.contains("export message Forbidden")
          ? named(provider, "Forbidden", Message.class)
          : named(provider, "Forbidden", com.google.eclipse.protobuf.protobuf.Enum.class);
      assertOn(issues, EditionValidator.EDITION_VISIBILITY, forbidden);
      assertNoCode(issues, Diagnostic.LINKING_DIAGNOSTIC);
    }
  }

  @Test public void should_reject_both_visibility_modifiers_before_2024_at_the_declarations() throws Exception {
    for (String dialect : new String[] {"2023", "proto2", "proto3"}) {
      Corpus corpus = corpus();
      corpus.write("provider.proto", header(dialect) + "export message Exported {} local message Hidden {} "
          + "export enum ExportedEnum { EXPORTED_ZERO=0; } local enum HiddenEnum { HIDDEN_ZERO=0; }");
      Protobuf provider = corpus.load("provider.proto");
      List<Issue> issues = issues(provider);
      assertCount(issues, EditionValidator.EDITION_VISIBILITY, 4);
      for (Message message : EcoreUtil2.getAllContentsOfType(provider, Message.class)) {
        assertOn(issues, EditionValidator.EDITION_VISIBILITY, message);
      }
      for (com.google.eclipse.protobuf.protobuf.Enum enumeration : EcoreUtil2.getAllContentsOfType(
          provider, com.google.eclipse.protobuf.protobuf.Enum.class)) {
        assertOn(issues, EditionValidator.EDITION_VISIBILITY, enumeration);
      }
      assertNoCode(issues, Diagnostic.LINKING_DIAGNOSTIC);
    }
  }

  @Test public void should_link_option_only_custom_features_and_genuine_payload_members_but_not_ordinary_types() throws Exception {
    for (String mode : new String[] {"option", ""}) {
      Corpus corpus = corpus();
      corpus.write("options.proto", optionDefinitions());
      corpus.write("consumer.proto", header("2024") + corpus.importOf(mode, "options.proto")
          + "option (settings.config).count=7; "
          + "option (settings.config)={detail:{text:'hello'} mode:MODE_ONE}; "
          + "option features.(settings.custom).enabled=false; "
          + "message Consumer { option features={[settings.custom]:{enabled:true}}; settings.Data data=1; } "
          + "service Echo { rpc Call(settings.Data) returns (settings.Data); } "
          + "extend settings.Extensible { int32 extra=100; }");
      Protobuf provider = corpus.load("options.proto");
      assertClean(provider);
      Protobuf consumer = corpus.load("consumer.proto");
      List<Issue> issues = issues(consumer);
      assertCount(issues, Diagnostic.LINKING_DIAGNOSTIC, "option".equals(mode) ? 4 : 0);
      assertNoCode(issues, EditionValidator.EDITION_IMPORT);
      assertNoCode(issues, EditionValidator.EDITION_FEATURE);
      assertNoCode(issues, EditionValidator.INVALID_FEATURE_DEFINITION);
      for (String name : new String[] {"config", "count", "detail", "text", "mode", "custom", "enabled"}) {
        assertReferenced(consumer, named(provider, name, MessageField.class));
      }
      assertReferenced(consumer, named(provider, "MODE_ONE", com.google.eclipse.protobuf.protobuf.Literal.class));
      assertDirectScope(consumer, OPTION_SOURCE__TARGET, named(provider, "config", MessageField.class), "settings.config");
      assertDirectScope(consumer, OPTION_FIELD__TARGET, named(provider, "custom", MessageField.class), "settings.custom");
      List<FeatureSets.Assignment> assignments = features.assignmentsIn(consumer);
      assertEquals(1, assignments.size());
      assertSame(named(provider, "custom", MessageField.class), assignments.get(0).path.get(0));
      assertSame(named(provider, "enabled", MessageField.class), assignments.get(0).path.get(1));
      assertEquals("false", features.explicitValue(consumer, assignments.get(0).path));
      assertEquals("true", features.explicitValue(named(consumer, "Consumer", Message.class), assignments.get(0).path));
      Rpc rpc = named(consumer, "Call", Rpc.class);
      TypeExtension extension = EcoreUtil2.getAllContentsOfType(consumer, TypeExtension.class).get(0);
      if ("option".equals(mode)) {
        assertUnresolvedField(consumer, "data");
        assertUnresolved(rpc.getArgType().getTarget());
        assertUnresolved(rpc.getReturnType().getTarget());
        assertUnresolved(extension.getType().getTarget());
        assertOn(issues, Diagnostic.LINKING_DIAGNOSTIC, rpc.getArgType());
        assertOn(issues, Diagnostic.LINKING_DIAGNOSTIC, rpc.getReturnType());
        assertOn(issues, Diagnostic.LINKING_DIAGNOSTIC, extension.getType());
      } else {
        Message data = named(provider, "Data", Message.class);
        assertFieldTarget(consumer, "data", data);
        assertSame(data, rpc.getArgType().getTarget());
        assertSame(data, rpc.getReturnType().getTarget());
        assertSame(named(provider, "Extensible", Message.class), extension.getType().getTarget());
        assertClean(consumer);
      }
    }
  }

  @Test public void should_link_any_embedded_types_through_option_imports_and_retain_their_source_identity() throws Exception {
    Corpus corpus = corpus();
    corpus.write("options.proto", header("2024") + "package settings; " + DESCRIPTOR + ANY_IMPORT
        + "local message Payload { google.protobuf.Any any=1; } "
        + "extend google.protobuf.FileOptions { Payload config=50001; }");
    corpus.write("embedded.proto", header("2024") + "package embedded; message Embedded { int32 id=1; }");
    corpus.write("consumer.proto", header("2024") + corpus.importOf("option", "options.proto")
        + corpus.importOf("option", "embedded.proto")
        + "option (settings.config)={any:{[example.test/embedded.Embedded]{id:7}}}; "
        + "message Consumer { embedded.Embedded forbidden=1; }");
    Protobuf provider = corpus.load("options.proto");
    Protobuf embedded = corpus.load("embedded.proto");
    assertClean(provider);
    assertClean(embedded);
    Protobuf consumer = corpus.load("consumer.proto");
    List<Issue> issues = issues(consumer);
    assertCount(issues, Diagnostic.LINKING_DIAGNOSTIC, 1);
    AnyFieldName any = EcoreUtil2.getAllContentsOfType(consumer, AnyFieldName.class).get(0);
    assertSame(named(embedded, "Embedded", Message.class), any.getEmbeddedType());
    assertEquals(corpus.uri("embedded.proto"), any.getEmbeddedType().eResource().getURI());
    assertDirectScope(consumer, ANY_FIELD_NAME__EMBEDDED_TYPE,
        named(embedded, "Embedded", Message.class), "embedded.Embedded");
    assertReferenced(consumer, named(provider, "any", MessageField.class));
    assertReferenced(consumer, named(embedded, "id", MessageField.class));
    assertUnresolvedField(consumer, "forbidden");
  }

  @Test public void should_follow_only_public_closures_after_ordinary_and_option_edges_even_when_private_files_are_loaded() throws Exception {
    for (String mode : new String[] {"", "option"}) {
      Corpus corpus = corpus();
      corpus.write("tail.proto", boolOptionFile("tail", "Tail", 50004));
      corpus.write("leaf.proto", header("2024") + "package leaf; " + DESCRIPTOR
          + corpus.importOf("public", "tail.proto")
          + "message Leaf {} extend google.protobuf.FileOptions { bool flag=50001; }");
      corpus.write("private.proto", boolOptionFile("private_file", "Private", 50002));
      corpus.write("secret.proto", boolOptionFile("secret", "Secret", 50003));
      corpus.write("bridge.proto", header("2024") + corpus.importOf("public", "leaf.proto")
          + corpus.importOf("", "private.proto") + corpus.importOf("option", "secret.proto") + "message Bridge {}");
      corpus.write("consumer.proto", header("2024") + corpus.importOf(mode, "bridge.proto")
          + "option (leaf.flag)=true; option (tail.flag)=true; "
          + "option (private_file.flag)=true; option (secret.flag)=true; "
          + "message Consumer { leaf.Leaf leaf_value=1; tail.Tail tail_value=2; "
          + "private_file.Private private_value=3; secret.Secret secret_value=4; }");
      Protobuf leaf = corpus.load("leaf.proto");
      Protobuf tail = corpus.load("tail.proto");
      corpus.load("private.proto");
      corpus.load("secret.proto");
      assertClean(corpus.load("bridge.proto"));
      Protobuf consumer = corpus.load("consumer.proto");
      List<Issue> issues = issues(consumer);
      List<com.google.eclipse.protobuf.protobuf.CustomOption> sourceOptions = EcoreUtil2.getAllContentsOfType(
          consumer, com.google.eclipse.protobuf.protobuf.CustomOption.class);
      for (com.google.eclipse.protobuf.protobuf.CustomOption option : sourceOptions.subList(2, 4)) {
        assertUnresolved(option.getSource().getTarget());
        assertOn(issues, Diagnostic.LINKING_DIAGNOSTIC, option.getSource());
      }
      assertReferenced(consumer, named(leaf, "flag", MessageField.class));
      assertReferenced(consumer, named(tail, "flag", MessageField.class));
      if (mode.isEmpty()) {
        assertFieldTarget(consumer, "leaf_value", named(leaf, "Leaf", Message.class));
        assertFieldTarget(consumer, "tail_value", named(tail, "Tail", Message.class));
      } else {
        assertUnresolvedField(consumer, "leaf_value");
        assertUnresolvedField(consumer, "tail_value");
      }
      assertUnresolvedField(consumer, "private_value");
      assertUnresolvedField(consumer, "secret_value");
      for (boolean options : new boolean[] {false, true}) {
        Set<URI> imported = visibility.importedUris(consumer, options);
        assertEquals(mode.isEmpty() || options, imported.contains(corpus.uri("leaf.proto")));
        assertEquals(mode.isEmpty() || options, imported.contains(corpus.uri("tail.proto")));
        assertFalse(imported.toString(), imported.contains(corpus.uri("private.proto")));
        assertFalse(imported.toString(), imported.contains(corpus.uri("secret.proto")));
      }
    }
  }

  @Test public void should_terminate_public_cycles_exclude_the_root_and_keep_same_file_locals() throws Exception {
    for (String mode : new String[] {"", "option"}) {
      Corpus corpus = corpus();
      corpus.write("a.proto", header("2024") + "package a; " + DESCRIPTOR
          + corpus.importOf("public", "b.proto") + "message A {} "
          + "extend google.protobuf.FileOptions { bool flag=50001; }");
      corpus.write("b.proto", header("2024") + "package b; " + corpus.importOf("public", "a.proto")
          + "message B {}");
      corpus.write("consumer.proto", header("2024") + corpus.importOf(mode, "a.proto")
          + "option (a.flag)=true; local message Local {} "
          + "message Consumer { Local own=1; a.A a_value=2; b.B b_value=3; }");
      Protobuf consumer = corpus.load("consumer.proto");
      List<Issue> issues = issues(consumer);
      assertCount(issues, Diagnostic.LINKING_DIAGNOSTIC, "option".equals(mode) ? 2 : 0);
      assertFieldTarget(consumer, "own", named(consumer, "Local", Message.class));
      assertReferenced(consumer, named(corpus.load("a.proto"), "flag", MessageField.class));
      Set<URI> ordinary = visibility.importedUris(consumer, false);
      Set<URI> options = visibility.importedUris(consumer, true);
      assertEquals(mode.isEmpty(), ordinary.contains(corpus.uri("a.proto")));
      assertEquals(mode.isEmpty(), ordinary.contains(corpus.uri("b.proto")));
      assertTrue(options.toString(), options.contains(corpus.uri("a.proto")));
      assertTrue(options.toString(), options.contains(corpus.uri("b.proto")));
      assertFalse(options.toString(), options.contains(corpus.uri("consumer.proto")));
      Protobuf a = corpus.load("a.proto");
      assertClean(a);
      assertFalse(visibility.importedUris(a, false).contains(corpus.uri("a.proto")));
      assertTrue(visibility.importedUris(a, false).contains(corpus.uri("b.proto")));
    }
  }

  @Test public void should_keep_ordinary_reachability_when_the_same_resource_is_also_option_imported() throws Exception {
    Corpus corpus = corpus();
    corpus.write("provider.proto", boolOptionFile("provider", "Data", 50001));
    corpus.write("consumer.proto", header("2024") + corpus.importOf("", "provider.proto")
        + corpus.importOf("option", "provider.proto")
        + "option (provider.flag)=true; message Consumer { provider.Data data=1; }");
    Protobuf provider = corpus.load("provider.proto");
    Protobuf consumer = corpus.load("consumer.proto");
    List<Issue> issues = issues(consumer);
    assertCount(issues, EditionValidator.EDITION_IMPORT, 1);
    assertNoCode(issues, Diagnostic.LINKING_DIAGNOSTIC);
    assertOn(issues, EditionValidator.EDITION_IMPORT, imports(consumer).get(1));
    assertFieldTarget(consumer, "data", named(provider, "Data", Message.class));
    assertReferenced(consumer, named(provider, "flag", MessageField.class));
  }

  @Test public void should_normalize_alias_imports_to_one_real_resource_identity_and_diagnose_the_duplicate() throws Exception {
    Corpus corpus = corpus();
    corpus.write("provider.proto", boolOptionFile("provider", "Data", 50001));
    corpus.write("alias.proto", "syntax='proto3';");
    corpus.resources.getURIConverter().getURIMap().put(corpus.uri("alias.proto"), corpus.uri("provider.proto"));
    corpus.write("consumer.proto", header("2024") + corpus.importOf("", "provider.proto")
        + corpus.importOf("option", "alias.proto")
        + "option (provider.flag)=true; message Consumer { provider.Data data=1; }");
    Protobuf provider = corpus.load("provider.proto");
    Protobuf consumer = corpus.load("consumer.proto");
    List<Issue> issues = issues(consumer);
    assertCount(issues, EditionValidator.EDITION_IMPORT, 1);
    assertNoCode(issues, Diagnostic.LINKING_DIAGNOSTIC);
    assertFieldTarget(consumer, "data", named(provider, "Data", Message.class));
    assertReferenced(consumer, named(provider, "flag", MessageField.class));
    assertSame(provider.eResource(), corpus.resources.getResource(corpus.uri("alias.proto"), true));
    for (boolean options : new boolean[] {false, true}) {
      Set<URI> imported = visibility.importedUris(consumer, options);
      assertTrue(imported.toString(), imported.contains(corpus.uri("provider.proto")));
      assertFalse(imported.toString(), imported.contains(corpus.uri("alias.proto")));
    }
  }

  @Test public void should_reject_option_imports_before_2024_without_losing_the_import_node() throws Exception {
    for (String dialect : new String[] {"2023", "proto2", "proto3"}) {
      Corpus corpus = corpus();
      corpus.write("provider.proto", header("2024") + "message Data {}");
      corpus.write("consumer.proto", header(dialect) + corpus.importOf("option", "provider.proto"));
      Protobuf consumer = corpus.load("consumer.proto");
      List<Issue> issues = issues(consumer);
      assertCount(issues, EditionValidator.EDITION_IMPORT, 1);
      assertOn(issues, EditionValidator.EDITION_IMPORT, imports(consumer).get(0));
      assertNoCode(issues, Diagnostic.LINKING_DIAGNOSTIC);
      assertFalse(((XtextResource) consumer.eResource()).getParseResult().hasSyntaxErrors());
    }
  }

  @Test public void should_diagnose_each_ordinary_import_after_the_first_option_import() throws Exception {
    for (String mode : new String[] {"", "public", "weak"}) {
      Corpus corpus = corpus();
      corpus.write("options.proto", header("2024") + "message Options {}");
      corpus.write("data.proto", header("2024") + "message Data {}");
      corpus.write("consumer.proto", header("2024") + corpus.importOf("option", "options.proto")
          + corpus.importOf(mode, "data.proto") + "message Consumer { Data data=1; }");
      Protobuf consumer = corpus.load("consumer.proto");
      List<Issue> issues = issues(consumer);
      assertCount(issues, EditionValidator.EDITION_IMPORT, "weak".equals(mode) ? 2 : 1);
      assertOn(issues, EditionValidator.EDITION_IMPORT, imports(consumer).get(1));
      assertNoCode(issues, Diagnostic.LINKING_DIAGNOSTIC);
      assertFieldTarget(consumer, "data", named(corpus.load("data.proto"), "Data", Message.class));
    }
  }

  @Test public void should_allow_weak_imports_in_2023_but_reject_them_in_2024() throws Exception {
    for (String year : new String[] {"2023", "2024"}) {
      Corpus corpus = corpus();
      corpus.write("provider.proto", header("2023") + "message Data {}");
      corpus.write("consumer.proto", header(year) + corpus.importOf("weak", "provider.proto")
          + "message Consumer { Data data=1; }");
      Protobuf consumer = corpus.load("consumer.proto");
      List<Issue> issues = issues(consumer);
      assertCount(issues, EditionValidator.EDITION_IMPORT, "2024".equals(year) ? 1 : 0);
      assertNoCode(issues, Diagnostic.LINKING_DIAGNOSTIC);
      assertFieldTarget(consumer, "data", named(corpus.load("provider.proto"), "Data", Message.class));
      if ("2023".equals(year)) assertClean(consumer);
      else assertOn(issues, EditionValidator.EDITION_IMPORT, imports(consumer).get(0));
    }
  }

  @Test public void should_keep_missing_import_diagnostics_on_both_import_modes() throws Exception {
    Corpus corpus = corpus();
    corpus.write("consumer.proto", header("2024") + corpus.importOf("", "missing.proto")
        + corpus.importOf("option", "missing-options.proto"));
    Protobuf consumer = corpus.load("consumer.proto");
    List<Issue> issues = issues(consumer);
    assertNoCode(issues, EditionValidator.EDITION_IMPORT);
    assertNoCode(issues, Diagnostic.LINKING_DIAGNOSTIC);
    assertMessageOn(issues, String.format(Messages.importNotFound, corpus.uri("missing.proto")), imports(consumer).get(0));
    assertMessageOn(issues, String.format(Messages.importNotFound, corpus.uri("missing-options.proto")), imports(consumer).get(1));
    assertTrue(visibility.importedUris(consumer, false).isEmpty());
    assertTrue(visibility.importedUris(consumer, true).isEmpty());
  }

  @Test public void should_preserve_collisions_with_loaded_hidden_and_option_imported_declarations() throws Exception {
    for (String mode : new String[] {"", "option"}) {
      Corpus corpus = corpus();
      corpus.write("provider.proto", header("2024") + "package shared; local message Duplicate {}");
      corpus.write("consumer.proto", header("2024") + "package shared; "
          + corpus.importOf(mode, "provider.proto") + "message Duplicate {}");
      Protobuf provider = corpus.load("provider.proto");
      assertClean(provider);
      Protobuf consumer = corpus.load("consumer.proto");
      List<Issue> issues = issues(consumer);
      String conflict = String.format(Messages.duplicateNameConflict, "Duplicate",
          String.format(Messages.conflictingDuplicateName, "Duplicate"));
      assertMessageOn(issues, conflict, named(consumer, "Duplicate", Message.class));
      assertNoCode(issues, Diagnostic.LINKING_DIAGNOSTIC);
      assertNoCode(issues, EditionValidator.EDITION_IMPORT);
      assertFalse(visibility.isVisible(named(provider, "Duplicate", Message.class), consumer));
      assertSame(provider.eResource(), named(provider, "Duplicate", Message.class).eResource());
    }
  }

  @Test public void should_keep_native_option_and_feature_members_on_the_authoritative_descriptor_with_an_option_import() throws Exception {
    Corpus corpus = corpus();
    corpus.write("consumer.proto", header("2024") + "import option 'google/protobuf/descriptor.proto'; "
        + "option java_package='example'; option features.field_presence=IMPLICIT; "
        + "message Consumer { int32 count=1; google.protobuf.FileOptions forbidden=2; }");
    Protobuf consumer = corpus.load("consumer.proto");
    List<Issue> issues = issues(consumer);
    assertCount(issues, Diagnostic.LINKING_DIAGNOSTIC, 1);
    assertNoCode(issues, EditionValidator.EDITIONS_FEATURES_UNAVAILABLE);
    Resource descriptor = corpus.resources.getResource(descriptors.primaryDescriptor(consumer.eResource()).location, true);
    EObject descriptorRoot = descriptor.getContents().get(0);
    assertReferenced(consumer, named(descriptorRoot, "java_package", MessageField.class));
    Message fileOptions = named(descriptorRoot, "FileOptions", Message.class);
    assertReferenced(consumer, named(fileOptions, "features", MessageField.class));
    Message featureSet = named(descriptorRoot, "FeatureSet", Message.class);
    assertReferenced(consumer, named(featureSet, "field_presence", MessageField.class));
    assertEquals("IMPLICIT", features.coreValue(named(consumer, "count", MessageField.class), "field_presence"));
    assertUnresolvedField(consumer, "forbidden");
  }

  private String optionDefinitions() {
    return header("2024") + "package settings; " + DESCRIPTOR
        + "local message Payload { int32 count=1; Detail detail=2; Mode mode=3; "
        + "message Detail { string text=1; } enum Mode { MODE_ZERO=0; MODE_ONE=1; } } "
        + "extend google.protobuf.FileOptions { Payload config=50001; } "
        + "local message FeatureSettings { bool enabled=1 [targets=TARGET_TYPE_FILE, targets=TARGET_TYPE_MESSAGE, "
        + "feature_support={edition_introduced:EDITION_2023}, edition_defaults={edition:EDITION_LEGACY,value:'true'}]; } "
        + "extend google.protobuf.FeatureSet { FeatureSettings custom=9995; } "
        + "message Data {} message Extensible { extensions 100 to 199; }";
  }

  private String boolOptionFile(String namespace, String type, int number) {
    return header("2024") + "package " + namespace + "; " + DESCRIPTOR + "message " + type + " {} "
        + "extend google.protobuf.FileOptions { bool flag=" + number + "; }";
  }

  private String header(String dialect) {
    return dialect.startsWith("proto") ? "syntax='" + dialect + "'; " : "edition='" + dialect + "'; ";
  }

  private String label(String dialect) {
    return "proto2".equals(dialect) ? "optional " : "";
  }

  private Corpus corpus() throws IOException {
    return new Corpus(temporary.newFolder());
  }

  private final class Corpus {
    final File directory;
    final XtextResourceSet resources = xtext.injector().getInstance(XtextResourceSet.class);

    Corpus(File directory) {
      this.directory = directory;
      resources.setClasspathURIContext(getClass());
    }

    URI uri(String name) {
      return URI.createURI(new File(directory, name).toURI().toString());
    }

    void write(String name, String source) throws IOException {
      Files.write(new File(directory, name).toPath(), source.getBytes(StandardCharsets.UTF_8));
    }

    String importOf(String mode, String name) {
      return "import " + (mode.isEmpty() ? "" : mode + " ") + "'" + uri(name) + "'; ";
    }

    Protobuf load(String name) {
      XtextResource resource = (XtextResource) resources.getResource(uri(name), true);
      assertNotNull(resource.getParseResult());
      assertFalse(resource.getParseResult().getRootNode().getText(), resource.getParseResult().hasSyntaxErrors());
      return (Protobuf) resource.getContents().get(0);
    }
  }

  private List<Issue> issues(Protobuf root) {
    EcoreUtil.resolveAll(root.eResource());
    return validator.validate(root.eResource(), CheckMode.ALL, CancelIndicator.NullImpl);
  }

  private void assertClean(Protobuf root) {
    List<Issue> issues = issues(root);
    assertTrue(issues.toString(), issues.isEmpty());
    assertTrue(root.eResource().getErrors().toString(), root.eResource().getErrors().isEmpty());
  }

  private List<Import> imports(Protobuf root) {
    return EcoreUtil2.getAllContentsOfType(root, Import.class);
  }

  private void assertVisibility(Protobuf consumer, String field, EObject target, boolean visible) {
    assertEquals(target.toString(), visible, visibility.isVisible(target, named(consumer, field, MessageField.class)));
    if (visible) assertFieldTarget(consumer, field, target);
    else assertUnresolvedField(consumer, field);
  }

  private void assertFieldTarget(Protobuf root, String name, EObject expected) {
    EObject actual = ((ComplexTypeLink) named(root, name, MessageField.class).getType()).getTarget();
    assertSame(name, expected, actual);
    assertFalse(name, actual.eIsProxy());
    assertSame(name, expected.eResource(), actual.eResource());
    assertEquals(name, expected.eResource().getURI(), actual.eResource().getURI());
  }

  private void assertUnresolvedField(Protobuf root, String name) {
    assertUnresolved(((ComplexTypeLink) named(root, name, MessageField.class).getType()).getTarget());
  }

  private void assertUnresolved(EObject target) {
    assertTrue(String.valueOf(target), target == null || target.eIsProxy());
  }

  private void assertReferenced(EObject root, EObject expected) {
    for (TreeIterator<EObject> contents = root.eAllContents(); contents.hasNext();) {
      EObject element = contents.next();
      for (EReference reference : element.eClass().getEAllReferences()) {
        if (!reference.isContainment() && !reference.isMany() && element.eGet(reference) == expected) {
          assertFalse(expected.eIsProxy());
          assertNotNull(expected.eResource());
          return;
        }
      }
    }
    fail("No real reference to " + expected + " in " + root.eResource().getURI());
  }

  private void assertDirectScope(EObject root, EReference reference, EObject expected, String name) {
    for (TreeIterator<EObject> contents = root.eAllContents(); contents.hasNext();) {
      EObject element = contents.next();
      if (element.eClass().getEAllReferences().contains(reference) && element.eGet(reference) == expected) {
        IEObjectDescription description = localScopes.getScope(element, reference).getSingleElement(
            QualifiedName.create(name.split("\\.")));
        assertNotNull(name, description);
        assertSame(name, expected, EcoreUtil.resolve(description.getEObjectOrProxy(), root));
        return;
      }
    }
    fail("Missing linked reference " + reference.getName() + " to " + name);
  }

  private void assertCount(List<Issue> issues, String code, int expected) {
    int count = 0;
    for (Issue issue : issues) if (code.equals(issue.getCode()) && issue.getSeverity() == Severity.ERROR) count++;
    assertEquals(issues.toString(), expected, count);
  }

  private void assertNoCode(List<Issue> issues, String code) {
    for (Issue issue : issues) assertFalse(issue.toString(), code.equals(issue.getCode()));
  }

  private void assertOn(List<Issue> issues, String code, EObject source) {
    for (Issue issue : issues) {
      if (code.equals(issue.getCode()) && issue.getSeverity() == Severity.ERROR && within(issue, source)) return;
    }
    fail("Expected " + code + " on " + source + ", got " + issues);
  }

  private void assertMessageOn(List<Issue> issues, String message, EObject source) {
    for (Issue issue : issues) {
      if (message.equals(issue.getMessage()) && issue.getSeverity() == Severity.ERROR && within(issue, source)) return;
    }
    fail("Expected '" + message + "' on " + source + ", got " + issues);
  }

  private boolean within(Issue issue, EObject source) {
    INode node = NodeModelUtils.getNode(source);
    return node != null && issue.getOffset() != null && issue.getOffset() >= node.getOffset()
        && issue.getOffset() < node.getEndOffset();
  }

  private <T extends EObject> T named(EObject root, String name, Class<T> type) {
    for (T element : EcoreUtil2.getAllContentsOfType(root, type)) {
      EStructuralFeature feature = element.eClass().getEStructuralFeature("name");
      if (feature != null && name.equals(element.eGet(feature))) return element;
    }
    throw new AssertionError("Missing real declaration " + name + " of " + type.getSimpleName());
  }
}
