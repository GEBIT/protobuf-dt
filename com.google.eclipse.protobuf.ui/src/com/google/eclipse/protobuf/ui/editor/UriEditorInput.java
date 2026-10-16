/*
 * Copyright (c) 2011 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.ui.editor;

import static com.google.common.base.Objects.equal;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Platform;
import org.eclipse.emf.common.util.URI;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IPersistableElement;

/**
 * @author alruiz@google.com (Alex Ruiz)
 */
public class UriEditorInput implements IEditorInput {
  private final URI fileUri;
  private final String name;
  private final IProject originatingProject;
  private final int hashCode;

  public UriEditorInput(URI fileUri, IProject originatingProject) {
    this.fileUri = fileUri.trimFragment();
    this.originatingProject = originatingProject;
    name = this.fileUri.lastSegment();
    hashCode = 31 * this.fileUri.hashCode() + (originatingProject == null ? 0 : originatingProject.hashCode());
  }

  @SuppressWarnings("rawtypes")
  @Override public Object getAdapter(Class adapter) {
    if (adapter == IProject.class) {
      return originatingProject;
    }
    return Platform.getAdapterManager().getAdapter(this, adapter);
  }

  @Override public boolean exists() {
    return false;
  }

  @Override public ImageDescriptor getImageDescriptor() {
    return null;
  }

  @Override public String getName() {
    return name;
  }

  @Override public IPersistableElement getPersistable() {
    return null;
  }

  @Override public String getToolTipText() {
    return name;
  }

  public URI getFileUri() {
    return fileUri;
  }

  public IProject getOriginatingProject() {
    return originatingProject;
  }

  @Override public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    UriEditorInput other = (UriEditorInput) obj;
    if (!equal(name, other.name)) {
      return false;
    }
    return equal(fileUri, other.fileUri) && equal(originatingProject, other.originatingProject);
  }

  @Override public int hashCode() {
    return hashCode;
  }
}
