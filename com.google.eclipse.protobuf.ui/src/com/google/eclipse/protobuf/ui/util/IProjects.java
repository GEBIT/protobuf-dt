/*
 * Copyright (c) 2011 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.ui.util;

import static com.google.eclipse.protobuf.ui.util.Workbenches.activeWorkbenchPage;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPartSite;

import com.google.inject.Singleton;

/**
 * Utility methods related to <code>{@link IProject}</code>s.
 *
 * @author alruiz@google.com (Alex Ruiz)
 */
@Singleton public class IProjects {
  private static final IViewReference[] NO_VIEW_REFERENCES = new IViewReference[0];

  /**
   * Returns the project owning the resource currently selected in whichever view (e.g. Project
   * Explorer, Package Explorer) is showing a resource-based selection in the active workbench
   * page.
   *
   * <p>This used to look specifically for the (now removed) legacy
   * {@code org.eclipse.ui.views.navigator.ResourceNavigator} view. Modern Eclipse no longer ships
   * that view, so instead we look at the current selection of every view in the active page and
   * take the first one that resolves to an <code>{@link IResource}</code>, which works with any
   * navigator-style view.
   */
  public IProject activeProject() {
    for (IViewReference reference : viewReferencesInActivePage()) {
      IViewPart part = reference.getView(false);
      IResource resource = resourceSelectedIn(part);
      if (resource != null) {
        return resource.getProject();
      }
    }
    return null;
  }

  private IResource resourceSelectedIn(IViewPart part) {
    if (part == null) {
      return null;
    }
    IWorkbenchPartSite site = part.getSite();
    if (site == null) {
      return null;
    }
    ISelectionProvider selectionProvider = site.getSelectionProvider();
    if (selectionProvider == null) {
      return null;
    }
    ISelection selection = selectionProvider.getSelection();
    if (!(selection instanceof IStructuredSelection)) {
      return null;
    }
    Object element = ((IStructuredSelection) selection).getFirstElement();
    return asResource(element);
  }

  private IResource asResource(Object element) {
    if (element instanceof IResource) {
      return (IResource) element;
    }
    if (element instanceof IAdaptable) {
      return ((IAdaptable) element).getAdapter(IResource.class);
    }
    return null;
  }

  private IViewReference[] viewReferencesInActivePage() {
    IWorkbenchPage page = activeWorkbenchPage();
    if (page == null) {
      return NO_VIEW_REFERENCES;
    }
    IViewReference[] references = page.getViewReferences();
    return (references == null) ? NO_VIEW_REFERENCES : references;
  }
}
