package com.google.eclipse.protobuf.model.util;

import static com.google.eclipse.protobuf.junit.core.IntegrationTestModule.integrationTestModule;
import static com.google.eclipse.protobuf.junit.core.XtextRule.overrideRuntimeModuleWith;
import static org.junit.Assert.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.diagnostics.Diagnostic;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.ui.editor.preferences.IPreferenceStoreAccess;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.util.StringInputStream;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.eclipse.xtext.validation.Issue;
import org.junit.Rule;
import org.junit.Test;

import com.google.eclipse.protobuf.junit.core.XtextRule;
import com.google.eclipse.protobuf.preferences.general.PreferenceNames;
import com.google.eclipse.protobuf.protobuf.IndexedElement;
import com.google.eclipse.protobuf.protobuf.Literal;
import com.google.eclipse.protobuf.protobuf.Message;
import com.google.eclipse.protobuf.protobuf.MessageField;
import com.google.eclipse.protobuf.scoping.ProtoDescriptorProvider;
import com.google.eclipse.protobuf.validation.EditionValidator;
import com.google.inject.Inject;

public class FeatureSets_Test {
  @Rule public XtextRule xtext = overrideRuntimeModuleWith(integrationTestModule());
  @Inject private FeatureSets features;
  @Inject private ProtoDescriptorProvider descriptors;
  @Inject private IResourceValidator validator;
  @Inject private IPreferenceStoreAccess preferences;

  @Test public void should_derive_both_years_defaults_and_assignment_policy_from_real_metadata() {
    for (String year : new String[] {"2023", "2024"}) {
      parse(year, "enum E { ZERO = 0; } message M { int32 value = 1; repeated int32 numbers = 2; }");
      assertTrue(features.hasRequiredMetadata(xtext.root()));
      assertEquals(Boolean.TRUE, features.isOpenEnum(named(xtext.root(), "E", com.google.eclipse.protobuf.protobuf.Enum.class)));
      MessageField scalar = named(xtext.root(), "value", MessageField.class);
      MessageField repeated = named(xtext.root(), "numbers", MessageField.class);
      assertEquals("EXPLICIT", features.coreValue(scalar, "field_presence"));
      assertEquals("PACKED", features.coreValue(repeated, "repeated_field_encoding"));
      assertTrue(features.canAssign(repeated, corePath("repeated_field_encoding"), coreLiteral("PACKED")));
      assertFalse(features.canAssign(scalar, corePath("repeated_field_encoding"), coreLiteral("PACKED")));
      assertFalse(features.canAssign(scalar, corePath("field_presence"), coreLiteral("FIELD_PRESENCE_UNKNOWN")));
      assertEquals("VERIFY", features.coreValue(scalar, "utf8_validation"));
      assertEquals("LENGTH_PREFIXED", features.coreValue(scalar, "message_encoding"));
      assertEquals("ALLOW", features.coreValue(named(xtext.root(), "M", Message.class), "json_format"));
      assertEquals("2024".equals(year) ? "STYLE2024" : "STYLE_LEGACY",
          features.coreValue(scalar, "enforce_naming_style"));
      assertEquals("2024".equals(year) ? "EXPORT_TOP_LEVEL" : "EXPORT_ALL",
          features.coreValue(xtext.root(), "default_symbol_visibility"));
      assertEquals("2024".equals(year), features.canAssign(xtext.root(), corePath("enforce_naming_style"), null));
      assertFalse(features.canAssign(scalar, corePath("enforce_proto_limits"), null));
      assertLinked();
    }
  }

  @Test public void should_merge_closest_lexical_leaves_without_following_field_types_or_siblings() {
    for (String aggregate : new String[] {"{ json_format: LEGACY_BEST_EFFORT }", "< json_format: LEGACY_BEST_EFFORT >"}) {
      parse("2024", "option features.field_presence = IMPLICIT; "
          + "message Outer { option features = " + aggregate + "; "
          + "int32 pinned = 1 [features.field_presence = EXPLICIT]; int32 inherited = 2; "
          + "message Inner { int32 nested = 1; } oneof choice { int32 member = 3; } } "
          + "message Sibling { Outer typed = 1; } enum E { ZERO = 0; }");
      MessageField pinned = named(xtext.root(), "pinned", MessageField.class);
      MessageField inherited = named(xtext.root(), "inherited", MessageField.class);
      assertEquals("EXPLICIT", features.explicitValue(pinned, corePath("field_presence")));
      assertNull(features.explicitValue(inherited, corePath("field_presence")));
      assertEquals("IMPLICIT", features.coreValue(inherited, "field_presence"));
      assertEquals("IMPLICIT", features.coreValue(named(xtext.root(), "nested", MessageField.class), "field_presence"));
      assertEquals("LEGACY_BEST_EFFORT", features.coreValue(named(xtext.root(), "Inner", Message.class), "json_format"));
      assertEquals("LEGACY_BEST_EFFORT", features.coreValue(named(xtext.root(), "member", MessageField.class), "json_format"));
      assertEquals("ALLOW", features.coreValue(named(xtext.root(), "typed", MessageField.class), "json_format"));
      assertEquals(Boolean.TRUE, features.isOpenEnum(named(xtext.root(), "E", com.google.eclipse.protobuf.protobuf.Enum.class)));
      assertEquals(1, features.assignmentsIn(pinned).size());
      FeatureSets.Assignment assignment = features.assignmentsIn(pinned).get(0);
      assertSame(pinned, assignment.owner);
      assertSame(corePath("field_presence").get(0), assignment.path.get(0));
      assertSame(xtext.resource(), assignment.source.eResource());
      assertNotNull(assignment.value);
      try {
        assignment.path.clear();
        fail("Assignment paths must be immutable");
      } catch (UnsupportedOperationException expected) {
        // Immutable snapshot, not an alias of the option traversal's mutable path.
      }
      assertLinked();
    }
  }

  @Test public void should_return_null_for_duplicate_and_invalid_leaves_not_choose_a_winner() {
    parse("2023", "option features.field_presence = IMPLICIT; "
        + "message M { int32 duplicate = 1 [features.field_presence = EXPLICIT, "
        + "features = { field_presence: IMPLICIT }]; "
        + "int32 unknown = 2 [features.field_presence = FIELD_PRESENCE_UNKNOWN]; "
        + "int32 wrong = 3 [features.field_presence = 1]; int32 clean = 4; }");
    for (String name : new String[] {"duplicate", "unknown", "wrong"}) {
      MessageField field = named(xtext.root(), name, MessageField.class);
      assertNull(features.explicitValue(field, corePath("field_presence")));
      assertNull(features.effectiveValue(field, corePath("field_presence")));
    }
    assertEquals(2, features.assignmentsIn(named(xtext.root(), "duplicate", MessageField.class)).size());
    assertEquals("IMPLICIT", features.coreValue(named(xtext.root(), "clean", MessageField.class), "field_presence"));
    assertLinked();
  }

  @Test public void should_keep_extension_identity_and_explicit_false_in_dotted_and_aggregate_forms() {
    parse("2024", customFeatures()
        + "option features.(first).enabled = false; "
        + "option features = { [second]: < enabled: true > }; "
        + "message M { int32 value = 1 [features.(second).enabled = false]; }");
    List<IndexedElement> first = extensionPath("first", "enabled");
    List<IndexedElement> second = extensionPath("second", "enabled");
    assertNotSame(first.get(0), second.get(0));
    assertSame(first.get(1), second.get(1));
    assertEquals("false", features.explicitValue(xtext.root(), first));
    assertEquals("true", features.explicitValue(xtext.root(), second));
    MessageField field = named(xtext.root(), "value", MessageField.class);
    assertEquals("false", features.effectiveValue(field, first));
    assertEquals("false", features.explicitValue(field, second));
    assertEquals(2, features.assignmentsIn(xtext.root()).size());
    assertTrue(features.canAssign(xtext.root(), Collections.singletonList(first.get(0)), null));
    assertNull(features.invalidDefinition(first.get(1)));
    assertLinked();
  }

  @Test public void should_preserve_textproto_boolean_forms_without_accepting_numeric_dotted_options() {
    parse("2024", customFeatures() + "option features = { [first]: { enabled: 0 } [second]: < enabled: 1 > };");
    assertEquals("false", features.explicitValue(xtext.root(), extensionPath("first", "enabled")));
    assertEquals("true", features.explicitValue(xtext.root(), extensionPath("second", "enabled")));
    assertLinked();
    parse("2024", customFeatures() + "option features.(first).enabled = 0;");
    assertNull(features.explicitValue(xtext.root(), extensionPath("first", "enabled")));
    assertNull(features.effectiveValue(xtext.root(), extensionPath("first", "enabled")));
    parse("2024", customFeatures().replace("value: 'true'", "value: '0'"));
    assertEquals("false", features.effectiveValue(xtext.root(), extensionPath("first", "enabled")));
    for (String value : new String[] {"1", "01", "0x1"}) {
      parse("2024", customFeatures().replace("value: 'true'", "value: '" + value + "'"));
      assertNull(features.invalidDefinition(extensionPath("first", "enabled").get(1)));
      assertEquals("true", features.effectiveValue(xtext.root(), extensionPath("first", "enabled")));
    }
  }

  @Test public void should_apply_support_boundaries_and_keep_fixed_defaults_effective() {
    for (String year : new String[] {"2023", "2024"}) {
      parse(year, customFeatures() + "message M { int32 value = 1; }");
      List<IndexedElement> late = extensionPath("first", "late");
      List<IndexedElement> removed = extensionPath("first", "removed");
      List<IndexedElement> deprecated = extensionPath("first", "deprecated");
      boolean modern = "2024".equals(year);
      assertEquals(modern, features.canAssign(xtext.root(), late, null));
      assertEquals("false", features.effectiveValue(xtext.root(), late));
      assertEquals(!modern, features.canAssign(xtext.root(), removed, null));
      assertEquals(modern ? "false" : "true", features.effectiveValue(xtext.root(), removed));
      assertEquals(!modern, features.supportError(late.get(1), null, xtext.root()) != null);
      assertEquals(modern, features.supportError(removed.get(1), null, xtext.root()) != null);
      assertTrue(features.canAssign(xtext.root(), deprecated, null));
      assertEquals(modern, features.deprecationWarning(deprecated.get(1), null, xtext.root()) != null);
      if (modern) {
        assertTrue(features.supportError(removed.get(1), null, xtext.root()).contains("Use enabled"));
        assertTrue(features.deprecationWarning(deprecated.get(1), null, xtext.root()).contains("Prefer enabled"));
      }
      assertLinked();
    }
  }

  @Test public void should_invalidate_custom_metadata_and_explicit_assignments_on_reparse() throws Exception {
    String source = "edition = '2024'; " + customFeatures() + "message M { int32 value = 1; }";
    xtext.parseText(source);
    assertEquals("true", features.effectiveValue(xtext.root(), extensionPath("first", "enabled")));
    xtext.resource().reparse(source.replace("value: 'true'", "value: 'false'"));
    EcoreUtil.resolveAll(xtext.resource());
    assertEquals("false", features.effectiveValue(xtext.resource().getContents().get(0),
        extensionPath("first", "enabled")));
    String assigned = source + " option features.(first).enabled = false;";
    xtext.resource().reparse(assigned);
    EcoreUtil.resolveAll(xtext.resource());
    EObject root = xtext.resource().getContents().get(0);
    assertEquals("false", features.explicitValue(root, extensionPath("first", "enabled")));
    xtext.resource().reparse(source);
    EcoreUtil.resolveAll(xtext.resource());
    root = xtext.resource().getContents().get(0);
    assertNull(features.explicitValue(root, extensionPath("first", "enabled")));
    assertEquals("true", features.effectiveValue(root, extensionPath("first", "enabled")));
  }

  @Test public void should_refine_value_windows_without_suppressing_custom_zero_values() {
    for (String year : new String[] {"2023", "2024"}) {
      parse(year, customFeatures() + "message Consumer { int32 value = 1; }");
      List<IndexedElement> path = extensionPath("first", "mode");
      Literal zero = named(xtext.root(), "MODE_ZERO", Literal.class);
      Literal retired = named(xtext.root(), "MODE_RETIRED", Literal.class);
      assertTrue(features.canAssign(xtext.root(), path, zero));
      assertEquals("MODE_ZERO", features.effectiveValue(xtext.root(), path));
      assertEquals("2023".equals(year), features.canAssign(xtext.root(), path, retired));
      assertEquals("2024".equals(year), features.supportError(path.get(1), retired, xtext.root()) != null);
      assertFalse(features.canAssign(named(xtext.root(), "value", MessageField.class), path, zero));
      assertLinked();
    }
  }

  @Test public void should_resolve_numeric_enum_metadata_defaults_and_reject_unknown_numbers() {
    for (String[] value : new String[][] {{"0", "MODE_ZERO"}, {"0x1", "MODE_RETIRED"}, {"01", "MODE_RETIRED"}}) {
      parse("2023", customFeatures().replace("value: 'MODE_ZERO'", "value: '" + value[0] + "'"));
      List<IndexedElement> path = extensionPath("first", "mode");
      assertNull(features.invalidDefinition(path.get(1)));
      assertEquals(value[1], features.effectiveValue(xtext.root(), path));
      assertTrue(features.canAssign(xtext.root(), path, named(xtext.root(), value[1], Literal.class)));
      assertLinked();
    }
    for (String value : new String[] {"2", "2147483648", "0xmissing"}) {
      parse("2023", customFeatures().replace("value: 'MODE_ZERO'", "value: '" + value + "'"));
      List<IndexedElement> path = extensionPath("first", "mode");
      assertNotNull(features.invalidDefinition(path.get(1)));
      assertNull(features.effectiveValue(xtext.root(), path));
    }
  }

  @Test public void should_ignore_empty_aggregates_and_ordinary_custom_fields_named_features() {
    parse("2024", customFeatures()
        + "message Ordinary { Payload features = 1; } "
        + "extend google.protobuf.FileOptions { Ordinary ordinary = 51000; } "
        + "option (ordinary).features.enabled = false; option features = {}; "
        + "message Consumer { int32 value = 1; }");
    assertTrue(features.assignmentsIn(xtext.root()).isEmpty());
    assertEquals("true", features.effectiveValue(xtext.root(), extensionPath("first", "enabled")));
    assertLinked();
  }

  @Test public void should_not_guess_policy_for_missing_owners_or_unsupported_source_years() {
    parse("2025", "message M { int32 value = 1; }");
    List<IndexedElement> path = corePath("field_presence");
    assertFalse(features.canAssign(xtext.root(), path, null));
    assertNull(features.effectiveValue(xtext.root(), path));
    assertNull(features.supportError(path.get(0), null, xtext.root()));
    assertFalse(features.canAssign(null, path, null));
    assertNull(features.effectiveValue(null, path));
  }

  @Test public void should_reject_malformed_feature_definitions_without_throwing() {
    parse("2024", customFeatures().replace("bool enabled", "repeated bool enabled"));
    assertNotNull(features.invalidDefinition(extensionPath("first", "enabled").get(1)));
    parse("2024", customFeatures().replace("value: 'true'", "value: 'not_a_boolean'"));
    assertNotNull(features.invalidDefinition(extensionPath("first", "enabled").get(1)));
    parse("2024", customFeatures().replace("feature_support = { edition_introduced: EDITION_2023 },",
        "feature_support = { edition_introduced: EDITION_2023 }, "
        + "feature_support = { edition_removed: EDITION_2023 },"));
    assertNotNull(features.invalidDefinition(extensionPath("first", "enabled").get(1)));
    assertNull(features.effectiveValue(xtext.root(), extensionPath("first", "enabled")));
  }

  @Test public void should_follow_selected_descriptor_identity_and_never_fall_back_when_missing() throws Exception {
    parse("2024", "enum E { ZERO = 0; }");
    XtextResource bundled = descriptor();
    String text = bundled.getParseResult().getRootNode().getText();
    URI selectedUri = URI.createURI("file:/feature-tests/selected-descriptor.proto");
    Resource selected = xtext.resource().getResourceSet().createResource(selectedUri);
    selected.load(new StringInputStream(text.replace("value: \"OPEN\"", "value: \"CLOSED\"")), null);
    IPreferenceStore store = preferences.getWritablePreferenceStore();
    boolean wasDefault = store.isDefault(PreferenceNames.DESCRIPTOR_PROTO_PATH);
    String previous = store.getString(PreferenceNames.DESCRIPTOR_PROTO_PATH);
    try {
      store.setValue(PreferenceNames.DESCRIPTOR_PROTO_PATH, selectedUri.toString());
      descriptors.invalidateCacheFor(null);
      assertTrue(features.hasRequiredMetadata(xtext.root()));
      assertEquals(Boolean.FALSE, features.isOpenEnum(named(xtext.root(), "E", com.google.eclipse.protobuf.protobuf.Enum.class)));
      assertSame(selected, corePath("enum_type").get(0).eResource());
      ((XtextResource) selected).reparse(text);
      EcoreUtil.resolveAll(selected);
      assertEquals(Boolean.TRUE, features.isOpenEnum(named(xtext.root(), "E", com.google.eclipse.protobuf.protobuf.Enum.class)));
      assertSame(selected, corePath("enum_type").get(0).eResource());
      store.setValue(PreferenceNames.DESCRIPTOR_PROTO_PATH, "file:/feature-tests/nonexistent-descriptor.proto");
      descriptors.invalidateCacheFor(null);
      assertFalse(features.hasRequiredMetadata(xtext.root()));
      assertNull(features.coreValue(xtext.root(), "enum_type"));
      assertNull(features.isOpenEnum(named(xtext.root(), "E", com.google.eclipse.protobuf.protobuf.Enum.class)));
      store.setValue(PreferenceNames.DESCRIPTOR_PROTO_PATH, selectedUri.toString());
      descriptors.invalidateCacheFor(null);
      ((XtextResource) selected).reparse("syntax = 'proto2'; package google.protobuf; message FileOptions {} message FeatureSet {}");
      assertFalse(features.hasRequiredMetadata(xtext.root()));
      assertNull(features.coreValue(xtext.root(), "field_presence"));
    } finally {
      if (wasDefault) store.setToDefault(PreferenceNames.DESCRIPTOR_PROTO_PATH);
      else store.setValue(PreferenceNames.DESCRIPTOR_PROTO_PATH, previous);
      descriptors.invalidateCacheFor(null);
    }
  }

  @Test public void should_follow_legacy_schema_capability_and_selected_defaults_across_selection_and_reparse()
      throws Exception {
    File legacy = new File("resources/legacy-descriptor/google/protobuf/descriptor.proto");
    File modern = new File("resources/protobuf-java-4.36.2/google/protobuf/descriptor.proto");
    URI legacyUri = URI.createURI(legacy.toURI().toString());
    URI modernUri = URI.createURI(modern.toURI().toString());
    String legacyText = new String(Files.readAllBytes(legacy.toPath()), StandardCharsets.UTF_8);
    String modernText = new String(Files.readAllBytes(modern.toPath()), StandardCharsets.UTF_8);
    String source = "edition = \"2023\";\n"
        + "import \"google/protobuf/descriptor.proto\";\n"
        + "extend google.protobuf.FeatureSet { Payload custom = 9995; }\n"
        + "message Payload {\n"
        + "  bool enabled = 1 [\n"
        + "    targets = TARGET_TYPE_FILE,\n"
        + "    edition_defaults = { edition: EDITION_PROTO2, value: \"true\" }\n"
        + "  ];\n"
        + "}\n"
        + "enum E { ZERO = 0; }\n"
        + "message M {\n"
        + "  int32 value = 1;\n"
        + "  repeated int32 numbers = 2;\n"
        + "  string text = 3;\n"
        + "}\n";
    IPreferenceStore store = preferences.getWritablePreferenceStore();
    boolean wasDefault = store.isDefault(PreferenceNames.DESCRIPTOR_PROTO_PATH);
    String previous = store.getString(PreferenceNames.DESCRIPTOR_PROTO_PATH);
    try {
      store.setValue(PreferenceNames.DESCRIPTOR_PROTO_PATH, legacyUri.toString());
      descriptors.invalidateCacheFor(null);
      xtext.parseText(source);
      EcoreUtil.resolveAll(xtext.resource());
      assertSelectedSchemaPolicy(legacyUri, true);
      Resource selected = descriptor();

      store.setValue(PreferenceNames.DESCRIPTOR_PROTO_PATH, modernUri.toString());
      descriptors.invalidateCacheFor(null);
      xtext.resource().reparse(source);
      EcoreUtil.resolveAll(xtext.resource());
      assertSelectedSchemaPolicy(modernUri, false);

      store.setValue(PreferenceNames.DESCRIPTOR_PROTO_PATH, legacyUri.toString());
      descriptors.invalidateCacheFor(null);
      xtext.resource().reparse(source);
      EcoreUtil.resolveAll(xtext.resource());
      assertSelectedSchemaPolicy(legacyUri, true);
      assertSame(selected, descriptor());

      ((XtextResource) selected).reparse(modernText);
      EcoreUtil.resolveAll(selected);
      xtext.resource().reparse(source);
      EcoreUtil.resolveAll(xtext.resource());
      assertSelectedSchemaPolicy(legacyUri, false);
      assertSame(selected, descriptor());

      ((XtextResource) selected).reparse(legacyText);
      EcoreUtil.resolveAll(selected);
      xtext.resource().reparse(source);
      EcoreUtil.resolveAll(xtext.resource());
      assertSelectedSchemaPolicy(legacyUri, true);
      assertSame(selected, descriptor());
    } finally {
      if (wasDefault) store.setToDefault(PreferenceNames.DESCRIPTOR_PROTO_PATH);
      else store.setValue(PreferenceNames.DESCRIPTOR_PROTO_PATH, previous);
      descriptors.invalidateCacheFor(null);
    }
  }

  private void assertSelectedSchemaPolicy(URI selectedUri, boolean legacy) {
    EObject root = xtext.resource().getContents().get(0);
    assertEquals(selectedUri, descriptors.primaryDescriptor(xtext.resource()).location);
    assertTrue(features.hasRequiredMetadata(root));
    for (String name : new String[] {"field_presence", "enum_type", "repeated_field_encoding",
        "utf8_validation", "message_encoding", "json_format"}) {
      assertEquals(selectedUri, corePath(name).get(0).eResource().getURI());
      assertSame(descriptor(), corePath(name).get(0).eResource());
    }
    assertEquals("EXPLICIT", features.coreValue(named(root, "value", MessageField.class), "field_presence"));
    com.google.eclipse.protobuf.protobuf.Enum enumeration = named(root, "E", com.google.eclipse.protobuf.protobuf.Enum.class);
    assertEquals("OPEN", features.coreValue(enumeration, "enum_type"));
    assertEquals(Boolean.TRUE, features.isOpenEnum(enumeration));
    assertEquals("PACKED", features.coreValue(named(root, "numbers", MessageField.class), "repeated_field_encoding"));
    assertEquals("VERIFY", features.coreValue(named(root, "text", MessageField.class), "utf8_validation"));
    assertEquals("LENGTH_PREFIXED", features.coreValue(root, "message_encoding"));
    assertEquals("ALLOW", features.coreValue(named(root, "M", Message.class), "json_format"));
    List<IndexedElement> custom = extensionPath("custom", "enabled");
    assertEquals(legacy, features.invalidDefinition(custom.get(1)) == null);
    assertEquals(legacy ? "true" : null, features.effectiveValue(root, custom));
    assertEquals(legacy, features.canAssign(root, custom, null));
    boolean invalidLeaf = false;
    for (Issue issue : validator.validate(xtext.resource(), CheckMode.ALL, CancelIndicator.NullImpl)) {
      assertFalse(issue.toString(), Diagnostic.LINKING_DIAGNOSTIC.equals(issue.getCode()));
      if (EditionValidator.INVALID_FEATURE_DEFINITION.equals(issue.getCode())
          && issue.getSeverity() == Severity.ERROR && EcoreUtil.getURI(custom.get(1)).equals(issue.getUriToProblem()))
        invalidLeaf = true;
    }
    assertEquals(!legacy, invalidLeaf);
    assertTrue(xtext.resource().getErrors().toString(), xtext.resource().getErrors().isEmpty());
  }

  @Test public void should_keep_metadata_and_values_local_to_each_resource_set() {
    parse("2024", customFeatures());
    EObject firstRoot = xtext.root();
    List<IndexedElement> firstPath = extensionPath("first", "enabled");
    assertEquals("true", features.effectiveValue(firstRoot, firstPath));
    parse("2024", customFeatures().replace("value: 'true'", "value: 'false'"));
    List<IndexedElement> secondPath = extensionPath("first", "enabled");
    assertNotSame(firstPath.get(0).eResource(), secondPath.get(0).eResource());
    assertEquals("false", features.effectiveValue(xtext.root(), secondPath));
    assertEquals("true", features.effectiveValue(firstRoot, firstPath));
  }


  private void parse(String year, String body) {
    xtext.parseText("edition = '" + year + "'; " + body);
  }

  private XtextResource descriptor() {
    return (XtextResource) xtext.resource().getResourceSet().getResource(
        descriptors.primaryDescriptor(xtext.resource()).location, true);
  }

  private List<IndexedElement> corePath(String name) {
    Message set = named(descriptor().getContents().get(0), "FeatureSet", Message.class);
    return Collections.<IndexedElement>singletonList(named(set, name, MessageField.class));
  }

  private Literal coreLiteral(String name) {
    return named(descriptor().getContents().get(0), name, Literal.class);
  }

  private List<IndexedElement> extensionPath(String extension, String field) {
    EObject root = xtext.resource().getContents().get(0);
    return Arrays.<IndexedElement>asList(named(root, extension, MessageField.class),
        named(root, field, MessageField.class));
  }

  private <T extends EObject> T named(EObject root, String name, Class<T> type) {
    for (T element : EcoreUtil2.getAllContentsOfType(root, type)) {
      Object actual = element.eGet(element.eClass().getEStructuralFeature("name"));
      if (name.equals(actual)) return element;
    }
    throw new AssertionError("Missing real declaration " + name + " of " + type.getSimpleName());
  }

  private void assertLinked() {
    for (Issue issue : validator.validate(xtext.resource(), CheckMode.ALL, CancelIndicator.NullImpl)) {
      assertFalse(issue.toString(), Diagnostic.LINKING_DIAGNOSTIC.equals(issue.getCode()));
    }
  }

  private String customFeatures() {
    return "import 'google/protobuf/descriptor.proto'; "
        + "extend google.protobuf.FeatureSet { Payload first = 9995; Payload second = 9996; } "
        + "message Payload { "
        + "bool enabled = 1 [targets = TARGET_TYPE_FILE, targets = TARGET_TYPE_FIELD, "
        + "feature_support = { edition_introduced: EDITION_2023 }, "
        + "edition_defaults = { edition: EDITION_LEGACY, value: 'true' }]; "
        + "bool late = 2 [targets = TARGET_TYPE_FILE, "
        + "feature_support = { edition_introduced: EDITION_2024 }, "
        + "edition_defaults = { edition: EDITION_LEGACY, value: 'false' }]; "
        + "bool removed = 3 [targets = TARGET_TYPE_FILE, "
        + "feature_support = { edition_introduced: EDITION_2023, edition_removed: EDITION_2024, removal_error: 'Use enabled' }, "
        + "edition_defaults = { edition: EDITION_LEGACY, value: 'true' }, "
        + "edition_defaults = { edition: EDITION_2024, value: 'false' }]; "
        + "bool deprecated = 4 [targets = TARGET_TYPE_FILE, "
        + "feature_support = { edition_introduced: EDITION_2023, edition_deprecated: EDITION_2024, deprecation_warning: 'Prefer enabled' }, "
        + "edition_defaults = { edition: EDITION_LEGACY, value: 'false' }]; "
        + "enum Mode { MODE_ZERO = 0; MODE_RETIRED = 1 [feature_support = { "
        + "edition_introduced: EDITION_2023, edition_removed: EDITION_2024, removal_error: 'Use MODE_ZERO' }]; } "
        + "Mode mode = 5 [targets = TARGET_TYPE_FILE, "
        + "feature_support = { edition_introduced: EDITION_2023 }, "
        + "edition_defaults = { edition: EDITION_LEGACY, value: 'MODE_ZERO' }]; } ";
  }
}
