package com.google.eclipse.protobuf.ui.scoping;

import static com.google.eclipse.protobuf.junit.core.UnitTestModule.unitTestModule;
import static com.google.eclipse.protobuf.junit.core.XtextRule.overrideRuntimeModuleWith;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.jface.preference.PreferenceStore;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.eclipse.xtext.ui.editor.preferences.IPreferenceStoreAccess;
import org.eclipse.xtext.ui.workspace.EclipseProjectConfig;
import org.eclipse.xtext.util.IResourceScopeCache;
import org.eclipse.xtext.workspace.ProjectConfigAdapter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.eclipse.protobuf.junit.core.AbstractTestModule;
import com.google.eclipse.protobuf.junit.core.XtextRule;
import com.google.eclipse.protobuf.model.util.Imports;
import com.google.eclipse.protobuf.preferences.general.PreferenceNames;
import com.google.eclipse.protobuf.protobuf.Import;
import com.google.eclipse.protobuf.protobuf.NativeOption;
import com.google.eclipse.protobuf.scoping.IImportResolver;
import com.google.eclipse.protobuf.scoping.IUriResolver;
import com.google.eclipse.protobuf.scoping.ProtoDescriptorProvider;
import com.google.eclipse.protobuf.util.EResources;

/** Exercises actual file/archive members and declaration identities, not resolver echoes. */
public class DescriptorImportAuthority_Test {
  private static final String CANONICAL = "google/protobuf/descriptor.proto";
  private static final String DESCRIPTOR = "syntax = \"proto2\"; package google.protobuf; "
      + "message FileOptions { optional bool deprecated = 23; }";
  private final TestModule module = new TestModule();
  @Rule public XtextRule xtext = overrideRuntimeModuleWith(unitTestModule(), module);
  @Rule public TemporaryFolder temporary = new TemporaryFolder();

  @Before public void defaults() {
    module.store.setValue(PreferenceNames.DESCRIPTOR_PROTO_PATH, CANONICAL);
    module.store.setValue("paths.directoryPaths", "");
    module.store.setValue("paths.filesInMultipleDirectories", true);
  }

  @Test public void should_use_noncanonical_override_for_import_and_native_declaration() throws Exception {
    File descriptor = temporary.newFile("selected-options.proto");
    Files.write(descriptor.toPath(), DESCRIPTOR.getBytes(StandardCharsets.UTF_8));
    module.store.setValue(PreferenceNames.DESCRIPTOR_PROTO_PATH, descriptor.getAbsolutePath());
    Resource source = source(URI.createFileURI(temporary.newFile("source.proto").getAbsolutePath()));
    URI selected = URI.createFileURI(descriptor.getAbsolutePath());
    assertSelection(source, selected);
    assertEquals(selected, provider().descriptorLocation(source, descriptor.getAbsolutePath()));
  }

  @Test public void should_not_fall_back_from_an_unreadable_explicit_descriptor() throws Exception {
    File descriptor = new File(temporary.getRoot(), "missing-options.proto");
    module.store.setValue(PreferenceNames.DESCRIPTOR_PROTO_PATH, descriptor.getAbsolutePath());
    Resource source = source(URI.createFileURI(temporary.newFile("source.proto").getAbsolutePath()));
    assertEquals(URI.createFileURI(descriptor.getAbsolutePath()), provider().primaryDescriptor(source).location);
    Import imported = EcoreUtil2.getAllContentsOfType(source.getContents().get(0), Import.class).get(0);
    assertNull(xtext.injector().getInstance(Imports.class).resolvedUriOf(imported));
    NativeOption option = EcoreUtil2.getAllContentsOfType(source.getContents().get(0), NativeOption.class).get(0);
    assertTrue(option.getSource().getTarget() == null || option.getSource().getTarget().eIsProxy());
  }

  @Test public void should_prefer_exact_external_archive_members_and_leave_missing_members_unresolved() throws Exception {
    File archive = temporary.newFile("dependency with spaces.jar");
    archive(archive);
    module.store.setValue("paths.directoryPaths", archive.getAbsolutePath());
    Resource source = source(URI.createFileURI(temporary.newFile("source.proto").getAbsolutePath()));
    URI root = URI.createURI("archive:" + URI.createFileURI(archive.getAbsolutePath()) + "!/");
    assertSelection(source, root.appendSegments(CANONICAL.split("/")));
    assertImport(source, "google/protobuf/api.proto", root.appendSegments(new String[] {"google", "protobuf", "api.proto"}));
    assertImport(source, "google/protobuf/not-in-any-bundle.proto", null);
    Resource imported = source.getResourceSet().getResource(root.appendSegments(CANONICAL.split("/")), true);
    assertEquals(root.appendSegments(CANONICAL.split("/")), provider().primaryDescriptor(imported).location);
    module.store.setValue("paths.filesInMultipleDirectories", false);
    module.store.setValue("paths.directoryPaths", "");
    assertEquals(root.appendSegments(new String[] {"google", "protobuf", "api.proto"}).toString(),
        xtext.injector().getInstance(IUriResolver.class).resolveUri(
            "google/protobuf/api.proto", imported.getURI(), null));
  }

  @Test public void should_resolve_workspace_archive_without_substituting_bundle_members() throws Exception {
    IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("descriptor-archive-" + System.nanoTime());
    project.create(null);
    project.open(null);
    try {
      File archive = project.getLocation().append("schemas with spaces.jar").toFile();
      archive(archive);
      project.refreshLocal(org.eclipse.core.resources.IResource.DEPTH_INFINITE, null);
      module.store.setValue("paths.directoryPaths", "${workspace_loc:" + project.getFullPath()
          + "/schemas with spaces.jar}");
      Resource source = source(URI.createPlatformResourceURI(project.getFullPath() + "/source.proto", true));
      ProjectConfigAdapter.install(source.getResourceSet(), new EclipseProjectConfig(project, null));
      URI root = URI.createURI("archive:" + URI.createPlatformResourceURI(project.getFullPath()
          + "/schemas with spaces.jar", true) + "!/");
      assertSelection(source, root.appendSegments(CANONICAL.split("/")));
    } finally {
      project.delete(true, true, null);
    }
  }

  @Test public void should_use_existing_bundle_only_when_no_dependency_descriptor_exists() throws Exception {
    Resource source = source(URI.createFileURI(temporary.newFile("source.proto").getAbsolutePath()));
    assertSelection(source, URI.createURI("platform:/plugin/de.gebit.eclipse.protobuf/" + CANONICAL));
    assertImport(source, "google/protobuf/any.proto",
        URI.createURI("platform:/plugin/de.gebit.eclipse.protobuf/google/protobuf/any.proto"));
    assertImport(source, "google/protobuf/not-in-any-bundle.proto", null);
  }

  @Test public void should_keep_origin_for_loaded_archive_and_plugin_resources() throws Exception {
    IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("descriptor-origin-" + System.nanoTime());
    project.create(null);
    project.open(null);
    try {
      XtextResourceSet set = xtext.injector().getInstance(XtextResourceSet.class);
      ProjectConfigAdapter.install(set, new EclipseProjectConfig(project, null));
      File archiveFile = temporary.newFile("origin.jar");
      archive(archiveFile);
      URI archiveUri = URI.createURI("archive:" + URI.createFileURI(archiveFile.getAbsolutePath())
          + "!/" + CANONICAL);
      Resource archiveResource = set.getResource(archiveUri, true);
      Resource plugin = set.getResource(URI.createURI(
          "platform:/plugin/de.gebit.eclipse.protobuf/google/protobuf/any.proto"), true);
      assertSame(project, EResources.getProjectOf(archiveResource));
      assertSame(project, EResources.getProjectOf(plugin));
      assertNull(EResources.getProjectOf(null));
      assertNull(EResources.getProjectOf(new org.eclipse.emf.ecore.resource.impl.ResourceImpl()));
    } finally {
      project.delete(true, true, null);
    }
  }

  @Test public void should_change_resource_dependent_selection_after_root_invalidation() throws Exception {
    File first = temporary.newFolder("first");
    File second = temporary.newFolder("second");
    for (File root : new File[] {first, second}) {
      File descriptor = new File(root, CANONICAL);
      descriptor.getParentFile().mkdirs();
      Files.write(descriptor.toPath(), DESCRIPTOR.getBytes(StandardCharsets.UTF_8));
    }
    module.store.setValue("paths.directoryPaths", first.getAbsolutePath());
    Resource source = source(URI.createFileURI(temporary.newFile("source.proto").getAbsolutePath()));
    assertSelection(source, URI.createFileURI(new File(first, CANONICAL).getAbsolutePath()));
    module.store.setValue("paths.directoryPaths", second.getAbsolutePath());
    provider().invalidateCacheFor(null);
    xtext.injector().getInstance(IResourceScopeCache.class).clear(source);
    org.eclipse.xtext.resource.XtextResource parsed = (org.eclipse.xtext.resource.XtextResource) source;
    parsed.reparse(parsed.getParseResult().getRootNode().getText());
    assertSelection(source, URI.createFileURI(new File(second, CANONICAL).getAbsolutePath()));
  }

  private ProtoDescriptorProvider provider() {
    return xtext.injector().getInstance(ProtoDescriptorProvider.class);
  }

  private Resource source(URI uri) throws Exception {
    XtextResourceSet set = xtext.injector().getInstance(XtextResourceSet.class);
    Resource resource = set.createResource(uri);
    resource.load(new ByteArrayInputStream(("syntax = \"proto2\"; import \"" + CANONICAL
        + "\"; option deprecated = true;").getBytes(StandardCharsets.UTF_8)), Collections.emptyMap());
    assertTrue(resource.getErrors().isEmpty());
    return resource;
  }

  private void assertSelection(Resource source, URI expected) {
    assertEquals(expected, provider().primaryDescriptor(source).location);
    Import imported = EcoreUtil2.getAllContentsOfType(source.getContents().get(0), Import.class).get(0);
    Imports imports = xtext.injector().getInstance(Imports.class);
    assertEquals(expected, imports.resolvedUriOf(imported));
    Resource descriptor = imports.importedResource(imported);
    assertNotNull(descriptor);
    assertSame(source.getResourceSet(), descriptor.getResourceSet());
    NativeOption option = EcoreUtil2.getAllContentsOfType(source.getContents().get(0), NativeOption.class).get(0);
    assertNotNull(option.getSource().getTarget());
    assertFalse(option.getSource().getTarget().eIsProxy());
    assertSame(descriptor, option.getSource().getTarget().eResource());
  }

  private void assertImport(Resource source, String path, URI expected) {
    Import imported = com.google.eclipse.protobuf.protobuf.ProtobufFactory.eINSTANCE.createImport();
    com.google.eclipse.protobuf.protobuf.StringLiteral literal =
        com.google.eclipse.protobuf.protobuf.ProtobufFactory.eINSTANCE.createStringLiteral();
    literal.getChunks().add("\"" + path + "\"");
    imported.setPath(literal);
    ((com.google.eclipse.protobuf.protobuf.Protobuf) source.getContents().get(0)).getElements().add(imported);
    assertEquals(expected, xtext.injector().getInstance(Imports.class).resolvedUriOf(imported));
  }

  private void archive(File file) throws Exception {
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file.toPath()))) {
      zip.putNextEntry(new ZipEntry(CANONICAL));
      zip.write(DESCRIPTOR.getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
      zip.putNextEntry(new ZipEntry("google/protobuf/api.proto"));
      zip.write("syntax = \"proto3\"; message ExactArchiveApi {}".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
  }

  private static class TestModule extends AbstractTestModule {
    final PreferenceStore store = new PreferenceStore();
    @Override protected void configure() {
      IPreferenceStoreAccess access = mock(IPreferenceStoreAccess.class);
      when(access.getWritablePreferenceStore()).thenReturn(store);
      when(access.getWritablePreferenceStore(any(IProject.class))).thenReturn(store);
      binder().bind(IPreferenceStoreAccess.class).toInstance(access);
      binder().bind(IUriResolver.class).to(UriResolver.class);
      binder().bind(IImportResolver.class).to(ImportResolver.class);
    }
  }
}
