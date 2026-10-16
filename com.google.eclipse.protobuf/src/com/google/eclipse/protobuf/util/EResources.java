/*
 * Copyright (c) 2014 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.util;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Path;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.ui.workspace.EclipseProjectConfig;
import org.eclipse.xtext.workspace.ProjectConfigAdapter;

/**
 * Utility methods for dealing with eResources.
 */
public final class EResources {
  private EResources() {}

  /**
   * Returns the containing project of the given resource, or {@code null} if none can be found.
   */
  public static IProject getProjectOf(Resource resource) {
    if (resource == null) {
      return null;
    }
    if (resource.getResourceSet() != null) {
      ProjectConfigAdapter adapter = ProjectConfigAdapter.findInEmfObject(resource.getResourceSet());
      if (adapter != null && adapter.getProjectConfig() instanceof EclipseProjectConfig) {
        return ((EclipseProjectConfig) adapter.getProjectConfig()).getProject();
      }
    }
    if (resource.getURI() == null || !resource.getURI().isPlatformResource()) {
      return null;
    }
    String resourceUri = resource.getURI().toPlatformString(true);
    if (resourceUri == null || Path.fromPortableString(resourceUri).segmentCount() < 2) {
      return null;
    }
    IFile file = Workspaces.workspaceRoot().getFile(Path.fromPortableString(resourceUri));
    return file.getProject();
  }
}
