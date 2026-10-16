/*
 * Copyright (c) 2011 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.ui.editor;

import static org.eclipse.xtext.ui.editor.utils.EditorUtils.getXtextEditor;
import static com.google.eclipse.protobuf.ui.util.Workbenches.activeWorkbenchPage;

import org.apache.log4j.Logger;
import org.eclipse.core.resources.IProject;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.xtext.ui.editor.LanguageSpecificURIEditorOpener;
import org.eclipse.xtext.ui.editor.XtextEditor;

import com.google.eclipse.protobuf.util.EResources;
import com.google.inject.Inject;

/**
 * @author alruiz@google.com (Alex Ruiz)
 */
public class ProtobufUriEditorOpener extends LanguageSpecificURIEditorOpener {
  private static Logger logger = Logger.getLogger(ProtobufUriEditorOpener.class);

  @Inject private FileOpener fileOpener;

  /** {@inheritDoc} */
  @Override public IEditorPart open(URI uri, EReference crossReference, int indexInList, boolean select) {
    return open(uri, crossReference, indexInList, select, originatingProject());
  }

  /** Opens a captured hyperlink in its invoking document's descriptor/import context. */
  public IEditorPart open(URI uri, EReference crossReference, int indexInList, boolean select,
      IProject originatingProject) {
    try {
      IEditorPart editor = editorFor(uri.trimFragment(), originatingProject);
      if (editor != null) {
        selectAndReveal(editor, uri, crossReference, indexInList, select);
        return getXtextEditor(editor);
      }
    } catch (PartInitException e) {
      logger.error("Unable to open " + uri.toString(), e);
    }
    return super.open(uri, crossReference, indexInList, select);
  }

  private IEditorPart editorFor(URI uri, IProject originatingProject) throws PartInitException {
    if (uri.isArchive()) {
      return fileOpener.openProtoFileInArchive(uri, originatingProject);
    }
    if (uri.isFile()) {
      return fileOpener.openProtoFileInFileSystem(uri, originatingProject);
    }
    if (uri.isPlatformPlugin()) {
      return fileOpener.openProtoFileInPlugin(uri, originatingProject);
    }
    return null;
  }

  private IProject originatingProject() {
    IWorkbenchPage page = activeWorkbenchPage();
    if (page == null || page.getActiveEditor() == null) {
      return null;
    }
    XtextEditor editor = getXtextEditor(page.getActiveEditor());
    return editor == null || editor.getDocument() == null ? null
        : editor.getDocument().readOnly(resource -> EResources.getProjectOf(resource));
  }
}
