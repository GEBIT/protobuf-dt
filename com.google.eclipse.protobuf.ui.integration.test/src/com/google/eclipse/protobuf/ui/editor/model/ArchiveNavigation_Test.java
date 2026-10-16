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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.ui.editor.XtextEditor;
import org.eclipse.xtext.ui.editor.preferences.IPreferenceStoreAccess;
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
import com.google.eclipse.protobuf.model.util.Imports;
import com.google.eclipse.protobuf.protobuf.Import;
import com.google.eclipse.protobuf.protobuf.NativeOption;
import com.google.eclipse.protobuf.ui.editor.FileOpener;
import com.google.eclipse.protobuf.ui.editor.UriEditorInput;
import com.google.eclipse.protobuf.ui.editor.hyperlinking.ProtobufHyperlinkDetector;
import com.google.eclipse.protobuf.ui.plugin.ProtobufEditorPlugIn;
import com.google.eclipse.protobuf.util.EResources;
import com.google.inject.Inject;

/** Opens real editors and archive members through the production import and symbol hyperlinks. */
public class ArchiveNavigation_Test {
  private static final String CANONICAL = "google/protobuf/descriptor.proto";
  private static final String DEPENDENCY = "syntax = \"proto2\"; package archiveproof; "
      + "import \"google/protobuf/descriptor.proto\"; import \"nested.proto\"; "
      + "option java_package = \"caf\u00e9\"; message Payload { optional Nested child = 1; }";
  private static final String NESTED = "syntax = \"proto2\"; package archiveproof; "
      + "import \"google/protobuf/descriptor.proto\"; option java_package = \"nested\"; "
      + "message Nested { optional string value = 1; }";
  private static final String STANDALONE = "syntax = \"proto3\"; message Standalone {}";

  @Rule public XtextRule xtext = createWith(ProtobufEditorPlugIn.injector());
  @Rule public TemporaryFolder temporary = new TemporaryFolder();
  @Inject private IPreferenceStoreAccess stores;
  @Inject private ProtobufHyperlinkDetector detector;
  @Inject private IResourceValidator validator;
  @Inject private Imports imports;
  @Inject private FileOpener opener;

  private IProject first;
  private IProject second;
  private URI firstDescriptor;
  private URI secondDescriptor;
  private IWorkbenchPage page;
  private final List<IEditorPart> opened = new ArrayList<>();

  @Before public void setUp() throws Exception {
    onUi(() -> {
      page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
      first = project("archive-origin-a-");
      second = project("archive-origin-b-");
      firstDescriptor = configure(first);
      secondDescriptor = configure(second);
      return null;
    });
  }

  @After public void tearDown() throws Exception {
    onUi(() -> {
      for (int i = opened.size() - 1; i >= 0; i--) {
        page.closeEditor(opened.get(i), false);
      }
      if (first != null && first.exists()) first.delete(true, true, null);
      if (second != null && second.exists()) second.delete(true, true, null);
      return null;
    });
  }

  @Test public void should_open_external_archive_imports_and_symbols_in_each_source_context() throws Exception {
    onUi(() -> {
      Path jar = temporary.newFile("external schemas.jar").toPath();
      Files.write(jar, archive());
      exerciseNavigation(URI.createFileURI(jar.toString()), jar.toString());
      return null;
    });
  }

  @Test public void should_open_workspace_archive_imports_and_symbols_in_each_source_context() throws Exception {
    onUi(() -> {
      IFile jar = first.getFile("workspace schemas.jar");
      jar.create(new ByteArrayInputStream(archive()), true, null);
      exerciseNavigation(platformUri(jar), "${workspace_loc:" + jar.getFullPath() + "}");
      return null;
    });
  }

  private void exerciseNavigation(URI jar, String importRoot) throws Exception {
    stores.getWritablePreferenceStore(first).setValue("paths.directoryPaths", importRoot);
    stores.getWritablePreferenceStore(second).setValue("paths.directoryPaths", importRoot);
    URI root = URI.createURI("archive:" + jar + "!/");
    XtextEditor sourceA = source(first);
    XtextEditor sourceB = source(second);

    // Import hyperlinks retain the creating resource's project even when another editor becomes active.
    IHyperlink importA = hyperlink(sourceA, "dependency.proto");
    page.activate(sourceB);
    XtextEditor dependencyA = follow(importA);
    assertArchive(dependencyA, root.appendSegment("dependency.proto"), first, firstDescriptor, DEPENDENCY);
    assertReadOnly(dependencyA);

    page.activate(sourceB);
    XtextEditor dependencyB = follow(hyperlink(sourceB, "dependency.proto"));
    assertArchive(dependencyB, root.appendSegment("dependency.proto"), second, secondDescriptor, DEPENDENCY);
    assertNotSame(dependencyA, dependencyB);
    assertFalse(dependencyA.getEditorInput().equals(dependencyB.getEditorInput()));

    // Cross-reference navigation captures the invoking editor before opening/activating its target.
    page.activate(sourceA);
    XtextEditor crossA = follow(hyperlink(sourceA, "archiveproof.Payload"));
    assertArchive(crossA, root.appendSegment("dependency.proto"), first, firstDescriptor, DEPENDENCY);
    assertSame(dependencyA, crossA);
    assertSelected(dependencyA, "Payload");
    page.activate(sourceB);
    XtextEditor crossB = follow(hyperlink(sourceB, "archiveproof.Payload"));
    assertArchive(crossB, root.appendSegment("dependency.proto"), second, secondDescriptor, DEPENDENCY);
    assertSame(dependencyB, crossB);
    assertSelected(dependencyB, "Payload");

    IHyperlink nestedA = hyperlink(dependencyA, "nested.proto");
    page.activate(sourceB);
    XtextEditor nested = follow(nestedA);
    assertArchive(nested, root.appendSegment("nested.proto"), first, firstDescriptor, NESTED);
    page.activate(dependencyA);
    assertSame(nested, follow(hyperlink(dependencyA, "Nested child")));
    assertSelected(nested, "Nested");
    page.activate(dependencyB);
    XtextEditor nestedB = follow(hyperlink(dependencyB, "Nested child"));
    assertArchive(nestedB, root.appendSegment("nested.proto"), second, secondDescriptor, NESTED);
    assertNotSame(nested, nestedB);
    assertSelected(nestedB, "Nested");

    // No origin means standalone/default context, not the project of the active editor or archive host.
    page.activate(sourceB);
    XtextEditor standalone = remember(opener.openProtoFileInArchive(root.appendSegment("standalone.proto"), null));
    assertEquals(STANDALONE, standalone.getDocument().get());
    standalone.getDocument().readOnly(resource -> {
      assertNull(EResources.getProjectOf(resource));
      assertClean(resource);
      return null;
    });
    assertReadOnly(standalone);
  }

  @Test public void should_keep_origin_for_filesystem_and_plugin_inputs_and_derive_workspace_targets() throws Exception {
    onUi(() -> {
      IFile invoking = write(second, "invoking.proto", STANDALONE);
      remember(page.openEditor(new FileEditorInput(invoking), "com.google.eclipse.protobuf.Protobuf"));
      Path file = temporary.newFile("outside.proto").toPath();
      String text = "syntax = \"proto2\"; import \"google/protobuf/descriptor.proto\"; "
          + "option java_package = \"external\";";
      Files.write(file, text.getBytes(StandardCharsets.UTF_8));
      XtextEditor external = remember(opener.openProtoFileInFileSystem(URI.createFileURI(file.toString()), first));
      assertSame(first, external.getEditorInput().getAdapter(IProject.class));
      assertContext(external, first, firstDescriptor);
      assertFalse(((ProtobufDocumentProvider) external.getDocumentProvider()).isReadOnly(external.getEditorInput()));

      XtextEditor plugin = remember(opener.openProtoFileInPlugin(
          URI.createURI("platform:/plugin/de.gebit.eclipse.protobuf/google/protobuf/empty.proto"), first));
      plugin.getDocument().readOnly(resource -> {
        assertSame(first, EResources.getProjectOf(resource));
        assertClean(resource);
        return null;
      });

      IFile workspace = write(first, "direct.proto", STANDALONE);
      XtextEditor direct = remember(page.openEditor(new UriEditorInput(platformUri(workspace), null),
          "com.google.eclipse.protobuf.Protobuf"));
      direct.getDocument().readOnly(resource -> {
        assertSame(first, EResources.getProjectOf(resource));
        assertClean(resource);
        return null;
      });
      return null;
    });
  }

  @Test public void should_reuse_workspace_resource_identity_for_filesystem_import_roots() throws Exception {
    onUi(() -> {
      IFile dependency = write(first, "dependency.proto", DEPENDENCY);
      write(first, "nested.proto", NESTED);
      stores.getWritablePreferenceStore(first).setValue("paths.directoryPaths", first.getLocation().toOSString());
      XtextEditor existing = remember(page.openEditor(new FileEditorInput(dependency),
          "com.google.eclipse.protobuf.Protobuf"));
      XtextEditor source = source(first);
      source.getDocument().readOnly(resource -> {
        Import imported = EcoreUtil2.getAllContentsOfType(resource.getContents().get(0), Import.class).get(0);
        assertEquals(platformUri(dependency), imports.resolvedUriOf(imported));
        assertClean(resource);
        return null;
      });
      XtextEditor target = follow(hyperlink(source, "archiveproof.Payload"));
      assertSame(existing, target);
      assertSelected(target, "Payload");
      target.getDocument().readOnly(resource -> {
        assertEquals(platformUri(dependency), resource.getURI());
        assertClean(resource);
        return null;
      });
      return null;
    });
  }

  private XtextEditor source(IProject project) throws Exception {
    IFile file = write(project, "main.proto", "syntax = \"proto2\"; import \"dependency.proto\"; "
        + "message Source { optional archiveproof.Payload payload = 1; }");
    return remember(page.openEditor(new FileEditorInput(file), "com.google.eclipse.protobuf.Protobuf"));
  }

  private IHyperlink hyperlink(XtextEditor editor, String token) {
    int offset = editor.getDocument().get().indexOf(token);
    assertTrue(token, offset >= 0);
    TextViewer viewer = new TextViewer(editor.getSite().getShell(), SWT.NONE);
    try {
      viewer.setDocument(editor.getDocument());
      IHyperlink[] links = detector.detectHyperlinks(viewer, new Region(offset + 1, 0), false);
      assertNotNull(token, links);
      assertEquals(token, 1, links.length);
      return links[0];
    } finally {
      viewer.getControl().dispose();
    }
  }

  private XtextEditor follow(IHyperlink link) {
    link.open();
    return remember(page.getActiveEditor());
  }

  private XtextEditor remember(IEditorPart editor) {
    assertTrue(String.valueOf(editor), editor instanceof XtextEditor);
    if (!opened.contains(editor)) opened.add(editor);
    return (XtextEditor) editor;
  }

  private void assertArchive(XtextEditor editor, URI uri, IProject project, URI descriptor, String text) {
    assertTrue(editor.getEditorInput() instanceof UriEditorInput);
    UriEditorInput input = (UriEditorInput) editor.getEditorInput();
    assertEquals(new UriEditorInput(uri.appendFragment("ignored"), project), input);
    assertEquals(new UriEditorInput(uri, project).hashCode(), input.hashCode());
    assertSame(project, input.getOriginatingProject());
    assertSame(project, input.getAdapter(IProject.class));
    assertEquals(text, editor.getDocument().get());
    URI actualUri = editor.getDocument().readOnly(resource -> resource.getURI());
    assertEquals(uri, actualUri);
    assertContext(editor, project, descriptor);
  }

  private void assertContext(XtextEditor editor, IProject project, URI descriptor) {
    editor.getDocument().readOnly(resource -> {
      assertSame(project, EResources.getProjectOf(resource));
      assertClean(resource);
      NativeOption option = EcoreUtil2.getAllContentsOfType(resource.getContents().get(0), NativeOption.class).get(0);
      assertFalse(option.getSource().getTarget().eIsProxy());
      assertEquals(descriptor, option.getSource().getTarget().eResource().getURI());
      Import canonical = EcoreUtil2.getAllContentsOfType(resource.getContents().get(0), Import.class).stream()
          .filter(value -> CANONICAL.equals(imports.getPath(value))).findFirst().get();
      assertSame(option.getSource().getTarget().eResource(), imports.importedResource(canonical));
      return null;
    });
  }

  private void assertReadOnly(XtextEditor editor) throws Exception {
    ProtobufDocumentProvider provider = (ProtobufDocumentProvider) editor.getDocumentProvider();
    Object input = editor.getEditorInput();
    assertTrue(provider.isReadOnly(input));
    assertFalse(provider.isModifiable(input));
    assertFalse(provider.canSaveDocument(input));
    XtextResource resource = editor.getDocument().readOnly(state -> state);
    byte[] original;
    try (java.io.InputStream stream = resource.getResourceSet().getURIConverter().createInputStream(resource.getURI())) {
      original = com.google.common.io.ByteStreams.toByteArray(stream);
    }
    String originalText = editor.getDocument().get();
    editor.getDocument().set(originalText + "\n// unsaved archive edit\n");
    assertFalse(provider.canSaveDocument(input));
    try {
      provider.doSaveDocument(new NullProgressMonitor(), input, editor.getDocument(), true);
      fail("Archive writeback must be rejected even when forced");
    } catch (CoreException expected) {
      assertTrue(expected.getStatus().matches(org.eclipse.core.runtime.IStatus.ERROR));
    }
    try (java.io.InputStream stream = resource.getResourceSet().getURIConverter().createInputStream(resource.getURI())) {
      assertArrayEquals(original, com.google.common.io.ByteStreams.toByteArray(stream));
    }
    editor.getDocument().set(originalText);
  }

  private void assertSelected(XtextEditor editor, String name) {
    ITextSelection selection = (ITextSelection) editor.getSelectionProvider().getSelection();
    assertEquals(name, selection.getText());
  }

  private void assertClean(XtextResource resource) {
    List<Issue> issues = validator.validate(resource, CheckMode.ALL, CancelIndicator.NullImpl);
    assertTrue(issues.toString(), issues.isEmpty());
  }

  private URI configure(IProject project) throws Exception {
    String descriptor;
    try (java.io.InputStream input = org.eclipse.core.runtime.Platform.getBundle("de.gebit.eclipse.protobuf")
        .getEntry("google/protobuf/descriptor.proto").openStream()) {
      descriptor = new String(com.google.common.io.ByteStreams.toByteArray(input), StandardCharsets.UTF_8);
    }
    IFile file = write(project, "selected-options.proto", descriptor);
    IPreferenceStore store = stores.getWritablePreferenceStore(project);
    store.setValue(ENABLE_PROJECT_SETTINGS_PREFERENCE_NAME, true);
    store.setValue(DESCRIPTOR_PROTO_PATH, URI.createFileURI(file.getLocation().toOSString()).toString());
    store.setValue("paths.filesInMultipleDirectories", true);
    return URI.createFileURI(file.getLocation().toOSString());
  }

  private IProject project(String prefix) throws Exception {
    IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(prefix + System.nanoTime());
    project.create(null);
    project.open(null);
    return project;
  }

  private IFile write(IProject project, String name, String text) throws Exception {
    IFile file = project.getFile(name);
    file.create(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), true, null);
    return file;
  }

  private byte[] archive() throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      entry(zip, "dependency.proto", DEPENDENCY);
      entry(zip, "nested.proto", NESTED);
      entry(zip, "standalone.proto", STANDALONE);
    }
    return bytes.toByteArray();
  }

  private void entry(ZipOutputStream zip, String name, String text) throws Exception {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(text.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }

  private static URI platformUri(IFile file) {
    return URI.createPlatformResourceURI(file.getFullPath().toPortableString(), true);
  }

  private static <T> T onUi(Callable<T> action) throws Exception {
    if (Display.getCurrent() != null) return action.call();
    FutureTask<T> task = new FutureTask<>(action);
    Display.getDefault().syncExec(task);
    return task.get();
  }
}
