/*
 * Copyright (c) 2011 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.ui.editor;

import static com.google.eclipse.protobuf.ui.util.Workbenches.activeWorkbenchPage;
import static com.google.common.base.Objects.equal;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Path;
import org.eclipse.emf.common.util.URI;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.FileStoreEditorInput;
import org.eclipse.ui.part.FileEditorInput;

import com.google.eclipse.protobuf.util.Uris;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Utility methods related to open file from different type of locations.
 *
 * @author alruiz@google.com (Alex Ruiz)
 */
@Singleton public class FileOpener {
  @Inject private Uris uris;

  public IEditorPart openProtoFileInWorkspace(URI uri) throws PartInitException {
    IWorkbenchPage page = activeWorkbenchPage();
    if (page == null) {
      return null;
    }
    IFile file = uris.referredFile(uri);
    IEditorInput editorInput = new FileEditorInput(file);
    return openFile(editorInput, page);
  }

  public IEditorPart openProtoFileInFileSystem(URI uri, IProject originatingProject) throws PartInitException {
    IWorkbenchPage page = activeWorkbenchPage();
    if (page == null) {
      return null;
    }
    IFileStore fileStore = EFS.getLocalFileSystem().getStore(Path.fromOSString(uri.toFileString()));
    IEditorInput editorInput = new ProjectFileStoreEditorInput(fileStore, originatingProject);
    return openFile(editorInput, page);
  }

  public IEditorPart openProtoFileInPlugin(URI uri, IProject originatingProject) throws PartInitException {
    IWorkbenchPage page = activeWorkbenchPage();
    if (page == null) {
      return null;
    }
    IEditorInput editorInput = new UriEditorInput(uri, originatingProject);
    return openFile(editorInput, page);
  }

  public IEditorPart openProtoFileInArchive(URI uri, IProject originatingProject) throws PartInitException {
    IWorkbenchPage page = activeWorkbenchPage();
    if (page == null) {
      return null;
    }
    return openFile(new UriEditorInput(uri, originatingProject), page);
  }

  private IEditorPart openFile(IEditorInput editorInput, IWorkbenchPage page) throws PartInitException {
    return page.openEditor(editorInput, "com.google.eclipse.protobuf.Protobuf");
  }

  private static final class ProjectFileStoreEditorInput extends FileStoreEditorInput {
    private final IProject originatingProject;
    private final int hashCode;

    ProjectFileStoreEditorInput(IFileStore fileStore, IProject originatingProject) {
      super(fileStore);
      this.originatingProject = originatingProject;
      hashCode = 31 * getURI().hashCode() + (originatingProject == null ? 0 : originatingProject.hashCode());
    }

    @Override public <T> T getAdapter(Class<T> adapter) {
      return adapter == IProject.class ? adapter.cast(originatingProject) : super.getAdapter(adapter);
    }

    @Override public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof ProjectFileStoreEditorInput)) {
        return false;
      }
      ProjectFileStoreEditorInput input = (ProjectFileStoreEditorInput) other;
      return equal(getURI(), input.getURI()) && equal(originatingProject, input.originatingProject);
    }

    @Override public int hashCode() {
      return hashCode;
    }
  }

}
