/*
 * Copyright (c) 2026 GEBIT Solutions GmbH.
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.ui.editor.model;

import static com.google.eclipse.protobuf.junit.core.XtextRule.createWith;
import static com.google.eclipse.protobuf.preferences.general.PreferenceNames.DESCRIPTOR_PROTO_PATH;
import static com.google.eclipse.protobuf.preferences.general.PreferenceNames.ENABLE_PROJECT_SETTINGS_PREFERENCE_NAME;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.emf.common.util.URI;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.ui.editor.preferences.IPreferenceStoreAccess;
import org.eclipse.xtext.ui.resource.IResourceSetProvider;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.eclipse.xtext.validation.Issue;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.eclipse.protobuf.junit.core.XtextRule;
import com.google.eclipse.protobuf.model.util.Options;
import com.google.eclipse.protobuf.naming.IProtobufQualifiedNameProvider;
import com.google.eclipse.protobuf.protobuf.BuiltinLiteral;
import com.google.eclipse.protobuf.protobuf.Import;
import com.google.eclipse.protobuf.protobuf.Literal;
import com.google.eclipse.protobuf.protobuf.LiteralLink;
import com.google.eclipse.protobuf.protobuf.MessageField;
import com.google.eclipse.protobuf.protobuf.NativeFieldOption;
import com.google.eclipse.protobuf.protobuf.NativeOption;
import com.google.eclipse.protobuf.protobuf.OptionSource;
import com.google.eclipse.protobuf.scoping.ProtoDescriptorProvider;
import com.google.eclipse.protobuf.ui.plugin.ProtobufEditorPlugIn;
import com.google.eclipse.protobuf.util.EResources;
import com.google.inject.Inject;

/** Validates the exact release inputs through production project/file/archive resolution. */
public class ModernSchemaCorpus_Test {
  private static final String CANONICAL = "google/protobuf/descriptor.proto";
  private static final URI BUNDLED = URI.createURI("platform:/plugin/de.gebit.eclipse.protobuf/" + CANONICAL);
  private static final String[] MEMBERS = {
      "any.proto", "api.proto", "descriptor.proto", "duration.proto", "empty.proto", "field_mask.proto",
      "java_features.proto", "source_context.proto", "struct.proto", "timestamp.proto", "type.proto", "wrappers.proto"
  };
  @Rule public XtextRule xtext = createWith(ProtobufEditorPlugIn.injector());
  @Rule public TemporaryFolder temporary = new TemporaryFolder();
  @Inject private IPreferenceStoreAccess stores;
  @Inject private IResourceSetProvider sets;
  @Inject private IResourceValidator validator;
  @Inject private ProtoDescriptorProvider descriptors;
  @Inject private IProtobufQualifiedNameProvider names;
  @Inject private Options options;
  @Inject private com.google.eclipse.protobuf.model.util.Imports imports;
  private IProject project;
  private IPreferenceStore store;

  @Before public void setUp() throws Exception {
    project = ResourcesPlugin.getWorkspace().getRoot().getProject("modern-corpus-" + System.nanoTime());
    project.create(null);
    project.open(null);
    store = stores.getWritablePreferenceStore(project);
    store.setValue(ENABLE_PROJECT_SETTINGS_PREFERENCE_NAME, true);
    store.setValue("paths.filesInMultipleDirectories", true);
    store.setValue(DESCRIPTOR_PROTO_PATH, CANONICAL);
  }

  @After public void tearDown() throws Exception {
    if (project != null && project.exists()) project.delete(true, true, null);
  }

  @Test public void should_validate_exact_external_archive_without_bundle_substitution() throws Exception {
    Path jar = temporary.newFile("exact external schemas.jar").toPath();
    archive(jar);
    store.setValue("paths.directoryPaths", jar.toString());
    URI root = URI.createURI("archive:" + URI.createFileURI(jar.toString()) + "!/");
    validateCorpus(root, root.appendSegments(CANONICAL.split("/")));
  }

  @Test public void should_validate_exact_workspace_archive_in_its_originating_project() throws Exception {
    Path jar = project.getLocation().append("exact workspace schemas.jar").toFile().toPath();
    archive(jar);
    project.refreshLocal(org.eclipse.core.resources.IResource.DEPTH_INFINITE, null);
    store.setValue("paths.directoryPaths", "${workspace_loc:" + project.getFullPath() + "/exact workspace schemas.jar}");
    URI root = URI.createURI("archive:" + URI.createPlatformResourceURI(
        project.getFullPath() + "/exact workspace schemas.jar", true) + "!/");
    validateCorpus(root, root.appendSegments(CANONICAL.split("/")));
  }

  @Test public void should_validate_exact_sources_with_the_explicit_bundled_descriptor() throws Exception {
    URI root = extract();
    store.setValue("paths.directoryPaths", root.toFileString());
    store.setValue(DESCRIPTOR_PROTO_PATH, BUNDLED.toString());
    validateCorpus(root, BUNDLED);
  }

  @Test public void should_validate_exact_sources_with_a_noncanonical_configured_descriptor() throws Exception {
    URI root = extract();
    Path configured = temporary.newFile("selected-options.proto").toPath();
    Files.write(configured, fixture("descriptor.proto"));
    store.setValue("paths.directoryPaths", root.toFileString());
    store.setValue(DESCRIPTOR_PROTO_PATH, configured.toString());
    validateCorpus(root, URI.createFileURI(configured.toString()));
  }

  @Test public void should_format_exact_metadata_twice_and_preserve_values_and_targets_on_reopen() throws Exception {
    URI root = extract();
    store.setValue("paths.directoryPaths", root.toFileString());
    store.setValue(DESCRIPTOR_PROTO_PATH, root.appendSegments(CANONICAL.split("/")).toFileString());
    org.eclipse.emf.ecore.resource.ResourceSet set = sets.get(project);
    org.eclipse.xtext.formatting.INodeModelFormatter formatter =
        xtext.injector().getInstance(org.eclipse.xtext.formatting.INodeModelFormatter.class);
    for (String member : new String[] {"descriptor.proto", "java_features.proto"}) {
      URI uri = root.appendSegments(new String[] {"google", "protobuf", member});
      XtextResource resource = (XtextResource) set.getResource(uri, true);
      assertClean(resource);
      List<String> original = semanticSnapshot(resource);
      String formatted = format(resource, formatter);
      assertFalse("No leading blank line", formatted.startsWith("\n"));
      resource.reparse(formatted);
      assertClean(resource);
      assertEquals(member + " semantics after format", original, semanticSnapshot(resource));
      assertEquals(member + " format is idempotent", formatted, format(resource, formatter));
      Files.write(java.nio.file.Paths.get(uri.toFileString()), formatted.getBytes(StandardCharsets.UTF_8));
      resource.unload();
      resource.load(Collections.emptyMap());
      assertClean(resource);
      assertEquals(member + " values/references after reopen", original, semanticSnapshot(resource));
    }
  }

  private String format(XtextResource resource, org.eclipse.xtext.formatting.INodeModelFormatter formatter) {
    org.eclipse.xtext.nodemodel.ICompositeNode node = resource.getParseResult().getRootNode();
    return formatter.format(node, 0, node.getText().length()).getFormattedText();
  }

  private List<String> semanticSnapshot(XtextResource resource) {
    List<String> result = new ArrayList<>();
    org.eclipse.emf.common.util.TreeIterator<org.eclipse.emf.ecore.EObject> contents = resource.getAllContents();
    while (contents.hasNext()) {
      org.eclipse.emf.ecore.EObject object = contents.next();
      if (object instanceof BuiltinLiteral
          || object instanceof MessageField && object.eContainer() instanceof com.google.eclipse.protobuf.protobuf.MapType) {
        contents.prune();
        continue;
      }
      result.add(object.eClass().getName());
      for (org.eclipse.emf.ecore.EStructuralFeature feature : object.eClass().getEAllStructuralFeatures()) {
        if (feature.isTransient() || feature.isDerived()
            || feature instanceof org.eclipse.emf.ecore.EReference
                && ((org.eclipse.emf.ecore.EReference) feature).isContainment()) continue;
        Object value = object.eGet(feature);
        if (feature instanceof org.eclipse.emf.ecore.EReference && value instanceof org.eclipse.emf.ecore.EObject) {
          value = org.eclipse.emf.ecore.util.EcoreUtil.getURI((org.eclipse.emf.ecore.EObject) value);
        }
        result.add(feature.getName() + "=" + String.valueOf(value));
      }
    }
    return result;
  }

  @Test public void should_preserve_any_prefix_chunks_presence_and_streaming_when_formatting_twice() throws Exception {
    URI root = extract();
    store.setValue("paths.directoryPaths", root.toFileString());
    store.setValue(DESCRIPTOR_PROTO_PATH, root.appendSegments(CANONICAL.split("/")).toFileString());
    String definitions = "syntax = \"proto2\"; package formatting; "
        + "import \"google/protobuf/descriptor.proto\"; import \"google/protobuf/any.proto\"; "
        + "message Payload { optional int32 id = 1; } message Settings { "
        + "map<string, google.protobuf.Any> entries = 1; repeated uint64 nums = 2; "
        + "repeated Payload children = 3; optional string warning = 4; } "
        + "extend google.protobuf.FileOptions { optional Settings settings = 50001; }";
    Files.write(java.nio.file.Paths.get(root.appendSegment("format-options.proto").toFileString()),
        definitions.getBytes(StandardCharsets.UTF_8));
    String source = "syntax=\"proto3\";import \"format-options.proto\";"
        + "option (formatting.settings)={entries:{key:\"payload\" value:{"
        + "[example.test/a.b/formatting.Payload]{id:7}}} nums:[0,18446744073709551615] "
        + "children:[] warning:\"keep / meaningful spaces \" \"and chunks\",}; ;"
        + "message Envelope{optional int32 count=1;map < string , formatting.Payload > payloads=2;"
        + "oneof content{// first oneof comment\n formatting.Payload selected=3;}reserved 4 to max;;}"
        + "service Api{rpc Bidi(stream Envelope)returns(stream formatting.Payload){;}}";
    URI uri = root.appendSegment("format-main.proto");
    Files.write(java.nio.file.Paths.get(uri.toFileString()), source.getBytes(StandardCharsets.UTF_8));
    XtextResource resource = (XtextResource) sets.get(project).getResource(uri, true);
    assertClean(resource);
    List<String> original = semanticSnapshot(resource);
    org.eclipse.xtext.formatting.INodeModelFormatter formatter =
        xtext.injector().getInstance(org.eclipse.xtext.formatting.INodeModelFormatter.class);
    String formatted = format(resource, formatter);
    assertTrue(formatted, formatted.contains("map<string, formatting.Payload> payloads"));
    assertTrue(formatted, formatted.contains("example.test/a.b/formatting.Payload"));
    assertTrue(formatted, formatted.contains("// first oneof comment"));
    resource.reparse(formatted);
    assertClean(resource);
    assertEquals(original, semanticSnapshot(resource));
    assertEquals(formatted, format(resource, formatter));
    Files.write(java.nio.file.Paths.get(uri.toFileString()), formatted.getBytes(StandardCharsets.UTF_8));
    resource.unload();
    resource.load(Collections.emptyMap());
    assertClean(resource);
    assertEquals(original, semanticSnapshot(resource));
  }

  private void validateCorpus(URI root, URI selected) throws Exception {
    org.eclipse.emf.ecore.resource.ResourceSet set = sets.get(project);
    List<XtextResource> resources = new ArrayList<>();
    for (String member : MEMBERS) {
      URI expected = root.appendSegments(new String[] {"google", "protobuf", member});
      XtextResource resource = (XtextResource) set.getResource(expected, true);
      assertEquals(expected, resource.getURI());
      assertSame(project, EResources.getProjectOf(resource));
      assertClean(resource);
      assertEquals(selected, descriptors.primaryDescriptor(resource).location);
      for (Import imported : EcoreUtil2.getAllContentsOfType(resource.getContents().get(0), Import.class)) {
        String path = imports.getPath(imported);
        URI expectedImport = CANONICAL.equals(path) ? selected : root.appendSegments(path.split("/"));
        assertEquals(path, expectedImport, imports.resolvedUriOf(imported));
      }
      for (OptionSource source : EcoreUtil2.getAllContentsOfType(resource.getContents().get(0), OptionSource.class)) {
        if (source.eContainer() instanceof NativeOption || source.eContainer() instanceof NativeFieldOption) {
          assertNotNull(source.getTarget());
          assertFalse(source.getTarget().eIsProxy());
          assertEquals(selected, source.getTarget().eResource().getURI());
        }
      }
      resources.add(resource);
    }
    XtextResource descriptor = resources.get(2);
    Literal maximum = EcoreUtil2.getAllContentsOfType(descriptor.getContents().get(0), Literal.class).stream()
        .filter(value -> !(value instanceof BuiltinLiteral) && "EDITION_MAX".equals(value.getName()))
        .findFirst().get();
    assertEquals(2147483647L, maximum.getIndex());
    assertLiteralLink(resources, "google.protobuf.FieldOptions.RETENTION_RUNTIME", selected);
    assertLiteralLink(resources, "google.protobuf.FieldOptions.TARGET_TYPE_FILE", selected);
    assertLiteralLink(resources, "google.protobuf.EDITION_2024", selected);

    String text = "syntax = \"proto2\"; import \"google/protobuf/descriptor.proto\"; "
        + "import \"google/protobuf/java_features.proto\"; message Probe { "
        + "optional pb.JavaFeatures.NestInFileClassFeature.NestInFileClass choice = 1 "
        + "[default = LEGACY, feature_support = { edition_introduced: EDITION_2024 }]; "
        + "extensions 1000 to 1100 [verification = UNVERIFIED]; } "
        + "enum E { ZERO = 0 [feature_support = { edition_introduced: EDITION_2024 }]; }";
    IFile file = project.getFile("probe.proto");
    file.create(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), true, null);
    XtextResource probe = (XtextResource) set.getResource(
        URI.createPlatformResourceURI(file.getFullPath().toString(), true), true);
    assertClean(probe);
    assertLiteralLink(Collections.singletonList(probe), "google.protobuf.ExtensionRangeOptions.UNVERIFIED", selected);
    assertLiteralLink(Collections.singletonList(probe), "pb.JavaFeatures.NestInFileClassFeature.LEGACY",
        root.appendSegments(new String[] {"google", "protobuf", "java_features.proto"}));
    for (NativeFieldOption option : EcoreUtil2.getAllContentsOfType(probe.getContents().get(0), NativeFieldOption.class)) {
      if ("feature_support".equals(options.nameForOption(options.rootSourceOf(option)))) {
        MessageField target = (MessageField) options.rootSourceOf(option);
        String owner = target.eContainer() instanceof com.google.eclipse.protobuf.protobuf.Message
            ? ((com.google.eclipse.protobuf.protobuf.Message) target.eContainer()).getName() : null;
        assertEquals(option.eContainer() instanceof Literal ? "EnumValueOptions" : "FieldOptions", owner);
        assertEquals(selected, target.eResource().getURI());
      }
    }
  }

  private void assertClean(XtextResource resource) {
    List<Issue> issues = validator.validate(resource, CheckMode.ALL, CancelIndicator.NullImpl);
    assertFalse(resource.getURI() + " syntax errors", resource.getParseResult().hasSyntaxErrors());
    assertTrue(resource.getURI() + " resource diagnostics: " + resource.getErrors(), resource.getErrors().isEmpty());
    assertTrue(resource.getURI() + " validation diagnostics: " + issues, issues.isEmpty());
  }

  private void assertLiteralLink(List<XtextResource> resources, String expectedName, URI expectedResource) {
    for (XtextResource resource : resources) {
      for (LiteralLink link : EcoreUtil2.getAllContentsOfType(resource.getContents().get(0), LiteralLink.class)) {
        Literal target = link.getTarget();
        if (target != null && !target.eIsProxy() && expectedName.equals(String.valueOf(names.getFullyQualifiedName(target)))) {
          assertEquals(expectedName, expectedResource, target.eResource().getURI());
          return;
        }
      }
    }
    fail("Missing expected-value link to " + expectedName);
  }

  private URI extract() throws Exception {
    Path root = temporary.newFolder("exact-source").toPath();
    for (String member : MEMBERS) {
      Path file = root.resolve("google/protobuf/" + member);
      Files.createDirectories(file.getParent());
      Files.write(file, fixture(member));
    }
    return URI.createFileURI(root.toString());
  }

  private void archive(Path path) throws Exception {
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
      for (String member : MEMBERS) {
        zip.putNextEntry(new ZipEntry("google/protobuf/" + member));
        zip.write(fixture(member));
        zip.closeEntry();
      }
    }
  }

  private byte[] fixture(String member) throws Exception {
    String path = "/resources/protobuf-java-4.36.2/google/protobuf/" + member;
    try (InputStream input = XtextRule.class.getResourceAsStream(path)) {
      assertNotNull("Missing exact corpus fixture " + path, input);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int count;
      while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
      return bytes.toByteArray();
    }
  }
}
