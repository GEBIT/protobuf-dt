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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.ui.XtextProjectHelper;
import org.eclipse.xtext.ui.editor.model.XtextDocument;
import org.eclipse.xtext.ui.editor.preferences.IPreferenceStoreAccess;
import org.eclipse.xtext.ui.editor.validation.XtextAnnotation;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.eclipse.xtext.validation.Issue;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.google.eclipse.protobuf.junit.core.XtextRule;
import com.google.eclipse.protobuf.model.util.Imports;
import com.google.eclipse.protobuf.protobuf.ComplexTypeLink;
import com.google.eclipse.protobuf.protobuf.Import;
import com.google.eclipse.protobuf.protobuf.NativeOption;
import com.google.eclipse.protobuf.ui.plugin.ProtobufEditorPlugIn;
import com.google.eclipse.protobuf.util.EResources;
import com.google.inject.Inject;

/** Exercises real connected documents, preference stores, workspace deltas and dependency archives. */
public class ProtobufDocumentProvider_livePreferences_Test {
  @Rule public XtextRule xtext = createWith(ProtobufEditorPlugIn.injector());

  @Inject private IPreferenceStoreAccess stores;
  @Inject private ProtobufDocumentProvider provider;
  @Inject private Imports imports;
  @Inject private IResourceValidator validator;

  private IProject project;
  private IPreferenceStore workspaceStore;
  private IPreferenceStore projectStore;
  private String originalWorkspaceDescriptor;
  private IFile workspaceDescriptor;
  private IFile projectDescriptor;
  private FileEditorInput input;
  private XtextDocument document;
  private int connections;

  @Before public void setUp() throws Exception {
    onUi(() -> {
      project = ResourcesPlugin.getWorkspace().getRoot().getProject("live-options-" + System.nanoTime());
      project.create(null);
      project.open(null);
      workspaceStore = stores.getWritablePreferenceStore();
      projectStore = stores.getWritablePreferenceStore(project);
      originalWorkspaceDescriptor = workspaceStore.getString(DESCRIPTOR_PROTO_PATH);
      workspaceDescriptor = write("workspace-options.proto", descriptor("java_package", 1));
      projectDescriptor = write("project-options.proto", descriptor("java_package", 2));
      workspaceStore.setValue(DESCRIPTOR_PROTO_PATH, fileUri(workspaceDescriptor).toString());
      projectStore.setValue(DESCRIPTOR_PROTO_PATH, fileUri(projectDescriptor).toString());
      projectStore.setValue(ENABLE_PROJECT_SETTINGS_PREFERENCE_NAME, false);
      return null;
    });
  }

  @After public void tearDown() throws Exception {
    onUi(() -> {
      while (connections > 0) {
        disconnect();
      }
      if (workspaceStore != null) {
        workspaceStore.setValue(DESCRIPTOR_PROTO_PATH, originalWorkspaceDescriptor);
      }
      if (project != null && project.exists()) {
        project.delete(true, true, null);
      }
      return null;
    });
  }

  @Test public void should_relink_native_options_and_canonical_imports_without_changing_unsaved_text() throws Exception {
    onUi(() -> {
      open("syntax = \"proto2\"; import \"google/protobuf/descriptor.proto\"; option java_package = \"saved\";");
      return null;
    });
    await(() -> selectedDescriptorIs(fileUri(workspaceDescriptor)));
    String unsaved = onUi(() -> {
      String text = document.get() + "\nmessage Unsaved { optional int32 live = 1; }\n";
      document.set(text);
      assertTrue(provider.canSaveDocument(input));
      projectStore.setValue(ENABLE_PROJECT_SETTINGS_PREFERENCE_NAME, true);
      return text;
    });
    long stamp = onUi(() -> document.getModificationStamp());
    await(() -> selectedDescriptorIs(fileUri(projectDescriptor)));
    onUi(() -> {
      assertEquals(unsaved, document.get());
      assertEquals(stamp, document.getModificationStamp());
      assertTrue(provider.canSaveDocument(input));
      document.readOnly(resource -> {
        assertEquals(unsaved, resource.getParseResult().getRootNode().getText());
        assertEquals(project, EResources.getProjectOf(resource));
        return null;
      });
      assertNoErrors();
      return null;
    });
  }

  @Test public void should_follow_both_stores_across_toggles_and_the_last_disconnect() throws Exception {
    onUi(() -> {
      projectStore.setValue(ENABLE_PROJECT_SETTINGS_PREFERENCE_NAME, true);
      open("syntax = \"proto2\"; import \"google/protobuf/descriptor.proto\"; option java_package = \"value\";");
      provider.connect(input);
      connections++;
      assertSame(document, provider.getDocument(input));
      return null;
    });
    await(() -> selectedDescriptorIs(fileUri(projectDescriptor)));
    onUi(() -> {
      disconnect();
      projectStore.setValue(ENABLE_PROJECT_SETTINGS_PREFERENCE_NAME, false);
      return null;
    });
    await(() -> selectedDescriptorIs(fileUri(workspaceDescriptor)));
    onUi(() -> {
      workspaceStore.setValue(DESCRIPTOR_PROTO_PATH, fileUri(projectDescriptor).toString());
      return null;
    });
    await(() -> selectedDescriptorIs(fileUri(projectDescriptor)));
    onUi(() -> {
      XtextDocument previous = document;
      disconnect();
      assertEquals(null, previous.getResourceURI());
      workspaceStore.setValue(DESCRIPTOR_PROTO_PATH, fileUri(workspaceDescriptor).toString());
      provider.connect(input);
      connections++;
      document = (XtextDocument) provider.getDocument(input);
      assertFalse(previous == document);
      return null;
    });
    await(() -> selectedDescriptorIs(fileUri(workspaceDescriptor)));
    onUi(() -> {
      assertFalse(provider.canSaveDocument(input));
      assertNoErrors();
      return null;
    });
  }

  @Test public void should_report_removed_native_options_then_recover_from_the_unsaved_buffer() throws Exception {
    IFile older = onUi(() -> write("older-options.proto", descriptor("other", 1)));
    onUi(() -> {
      open("syntax = \"proto2\"; import \"google/protobuf/descriptor.proto\"; option java_package = \"saved\";");
      return null;
    });
    await(() -> selectedDescriptorIs(fileUri(workspaceDescriptor)));
    String unsaved = onUi(() -> {
      String text = document.get().replace("saved", "unsaved");
      document.set(text);
      workspaceStore.setValue(DESCRIPTOR_PROTO_PATH, fileUri(older).toString());
      return text;
    });
    await(() -> document.readOnly(resource -> {
      NativeOption option = EcoreUtil2.getAllContentsOfType(resource.getContents().get(0), NativeOption.class).get(0);
      EObject target = option.getSource().getTarget();
      return (target == null || target.eIsProxy()) && hasErrorAnnotations();
    }));
    onUi(() -> {
      assertEquals(unsaved, document.get());
      assertTrue(provider.canSaveDocument(input));
      workspaceStore.setValue(DESCRIPTOR_PROTO_PATH, fileUri(workspaceDescriptor).toString());
      return null;
    });
    await(() -> selectedDescriptorIs(fileUri(workspaceDescriptor)) && !hasErrorAnnotations());
    onUi(() -> {
      assertEquals(unsaved, document.get());
      assertTrue(provider.canSaveDocument(input));
      assertNoErrors();
      return null;
    });
  }

  @Test public void should_refresh_persisted_project_markers_when_the_descriptor_setting_changes() throws Exception {
    IFile older = onUi(() -> write("older-options.proto", descriptor("other", 1)));
    onUi(() -> {
      IProjectDescription description = project.getDescription();
      description.setNatureIds(new String[] { XtextProjectHelper.NATURE_ID });
      project.setDescription(description, null);
      open("syntax = \"proto2\"; import \"google/protobuf/descriptor.proto\"; option java_package = \"value\";");
      return null;
    });
    await(() -> selectedDescriptorIs(fileUri(workspaceDescriptor)));
    onUi(() -> {
      workspaceStore.setValue(DESCRIPTOR_PROTO_PATH, fileUri(older).toString());
      return null;
    });
    await(this::hasProblemMarkers);
    onUi(() -> {
      workspaceStore.setValue(DESCRIPTOR_PROTO_PATH, fileUri(workspaceDescriptor).toString());
      return null;
    });
    await(() -> selectedDescriptorIs(fileUri(workspaceDescriptor)) && !hasProblemMarkers());
  }

  @Test public void should_refresh_import_roots_and_changed_import_contents() throws Exception {
    IFile first = onUi(() -> dependency("first", "before"));
    IFile second = onUi(() -> dependency("second", "after"));
    onUi(() -> {
      projectStore.setValue("paths.filesInMultipleDirectories", true);
      projectStore.setValue("paths.directoryPaths", rootOf(first));
      open("syntax = \"proto2\"; import \"dependency.proto\"; message Main { optional support.Dep value = 1; }");
      return null;
    });
    await(() -> importedTypeIs(platformUri(first), "before"));
    onUi(() -> {
      projectStore.setValue("paths.directoryPaths", rootOf(second));
      return null;
    });
    await(() -> importedTypeIs(platformUri(second), "after"));
    onUi(() -> {
      write(second, "syntax = \"proto2\"; package support; message Dep { optional string revised = 1; }");
      return null;
    });
    await(() -> importedTypeIs(platformUri(second), "revised"));
  }

  @Test public void should_relink_when_the_java_archive_dependency_changes() throws Exception {
    IFile first = onUi(() -> archive("first.jar", "before"));
    IFile second = onUi(() -> archive("second.jar", "after"));
    IJavaProject javaProject = onUi(() -> {
      IProjectDescription description = project.getDescription();
      description.setNatureIds(new String[] { JavaCore.NATURE_ID });
      project.setDescription(description, null);
      IJavaProject java = JavaCore.create(project);
      java.setRawClasspath(new IClasspathEntry[] { JavaCore.newLibraryEntry(first.getFullPath(), null, null) }, null);
      open("syntax = \"proto2\"; import \"dependency.proto\"; message Main { optional support.Dep value = 1; }");
      return java;
    });
    await(() -> importedTypeIs(archiveUri(first), "before"));
    onUi(() -> {
      document.set(document.get() + "\nmessage Unsaved {}\n");
      javaProject.setRawClasspath(new IClasspathEntry[] { JavaCore.newLibraryEntry(second.getFullPath(), null, null) }, null);
      return null;
    });
    await(() -> importedTypeIs(archiveUri(second), "after"));
    onUi(() -> {
      assertTrue(document.get().contains("message Unsaved"));
      assertTrue(provider.canSaveDocument(input));
      assertNoErrors();
      return null;
    });
  }

  private void open(String source) throws Exception {
    input = new FileEditorInput(write("main.proto", source));
    provider.connect(input);
    connections++;
    document = (XtextDocument) provider.getDocument(input);
  }

  private void disconnect() {
    provider.disconnect(input);
    connections--;
  }

  private boolean selectedDescriptorIs(URI uri) {
    return document.readOnly(resource -> {
      NativeOption option = EcoreUtil2.getAllContentsOfType(resource.getContents().get(0), NativeOption.class).get(0);
      EObject target = option.getSource().getTarget();
      if (target == null || target.eIsProxy() || !uri.equals(target.eResource().getURI())) {
        return false;
      }
      Import anImport = EcoreUtil2.getAllContentsOfType(resource.getContents().get(0), Import.class).get(0);
      return target.eResource() == imports.importedResource(anImport);
    });
  }

  private boolean importedTypeIs(URI uri, String field) {
    return document.readOnly(resource -> {
      ComplexTypeLink link = EcoreUtil2.getAllContentsOfType(resource.getContents().get(0), ComplexTypeLink.class).get(0);
      EObject target = link.getTarget();
      return target != null && !target.eIsProxy() && uri.equals(target.eResource().getURI())
          && EcoreUtil2.getAllContentsOfType(target, com.google.eclipse.protobuf.protobuf.MessageField.class).stream()
              .anyMatch(member -> field.equals(member.getName()));
    });
  }

  private boolean hasErrorAnnotations() {
    Iterator<?> annotations = provider.getAnnotationModel(input).getAnnotationIterator();
    while (annotations.hasNext()) {
      Object annotation = annotations.next();
      if (annotation instanceof XtextAnnotation
          && ((XtextAnnotation) annotation).getIssue().getSeverity() == Severity.ERROR) {
        return true;
      }
    }
    return false;
  }

  private void assertNoErrors() {
    List<Issue> issues = document.readOnly(resource -> validator.validate(resource, CheckMode.ALL, CancelIndicator.NullImpl));
    assertFalse(issues.toString(), issues.stream().anyMatch(issue -> issue.getSeverity() == Severity.ERROR));
  }

  private IFile dependency(String folderName, String field) throws Exception {
    IFolder folder = project.getFolder(folderName);
    folder.create(true, true, null);
    return write(folderName + "/dependency.proto",
        "syntax = \"proto2\"; package support; message Dep { optional string " + field + " = 1; }");
  }

  private IFile archive(String name, String field) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      zip.putNextEntry(new ZipEntry("dependency.proto"));
      zip.write(("syntax = \"proto2\"; package support; message Dep { optional string " + field + " = 1; }")
          .getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    IFile file = project.getFile(name);
    file.create(new ByteArrayInputStream(bytes.toByteArray()), true, null);
    return file;
  }

  private IFile write(String name, String text) throws Exception {
    IFile file = project.getFile(name);
    write(file, text);
    return file;
  }

  private void write(IFile file, String text) throws Exception {
    try (ByteArrayInputStream stream = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))) {
      if (file.exists()) {
        file.setContents(stream, true, false, null);
      } else {
        file.create(stream, true, null);
      }
    }
  }

  private boolean hasProblemMarkers() {
    try {
      for (IMarker marker : input.getFile().findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ZERO)) {
        if (marker.getAttribute(IMarker.SEVERITY, -1) == IMarker.SEVERITY_ERROR) {
          return true;
        }
      }
      return false;
    } catch (CoreException e) {
      throw new AssertionError(e);
    }
  }

  private static String descriptor(String field, int tag) {
    return "syntax = \"proto2\"; package google.protobuf; message FileOptions { optional string " + field + " = " + tag + "; }";
  }

  private static String rootOf(IFile file) {
    return "${workspace_loc:" + file.getParent().getFullPath().toPortableString() + "}";
  }

  private static URI fileUri(IFile file) {
    return URI.createFileURI(file.getLocation().toOSString());
  }

  private static URI platformUri(IFile file) {
    return URI.createPlatformResourceURI(file.getFullPath().toPortableString(), true);
  }

  private static URI archiveUri(IFile file) {
    return URI.createURI("archive:" + platformUri(file) + "!/dependency.proto");
  }

  private static <T> T onUi(Callable<T> action) throws Exception {
    if (Display.getCurrent() != null) {
      return action.call();
    }
    FutureTask<T> task = new FutureTask<>(action);
    Display.getDefault().syncExec(task);
    return task.get();
  }

  private static void await(BooleanSupplier condition) throws Exception {
    long deadline = System.currentTimeMillis() + 10000;
    do {
      if (onUi(() -> condition.getAsBoolean())) {
        return;
      }
      if (Display.getCurrent() != null) {
        while (Display.getCurrent().readAndDispatch()) {
          // Let the document provider's queued UI refresh run without blocking the workbench.
        }
      } else {
        Thread.sleep(10);
      }
    } while (System.currentTimeMillis() < deadline);
    assertTrue("The connected document did not refresh", onUi(() -> condition.getAsBoolean()));
  }
}
