/*
 * Copyright (c) 2016 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.scoping;

import com.google.common.base.Predicate;
import com.google.eclipse.protobuf.model.util.Resources;
import com.google.inject.Inject;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.resource.IResourceDescription;
import org.eclipse.xtext.resource.IResourceDescriptions;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.impl.ImportUriGlobalScopeProvider;

import java.util.LinkedHashSet;

/**
 * A global scope provider exposing the union of ordinary and option-only imports.
 */
public class ProtobufImportUriGlobalScopeProvider extends ImportUriGlobalScopeProvider {
  @Override
  protected IScope createLazyResourceScope(
      IScope parent,
      URI uri,
      IResourceDescriptions descriptions,
      EClass type,
      Predicate<IEObjectDescription> filter,
      boolean ignoreCase) {
    IResourceDescription description = descriptions.getResourceDescription(uri);
    return ProtobufSelectableBasedScope.createScope(parent, description, filter, type, ignoreCase);
  }

  @Inject private ImportVisibility visibility;
  @Inject private Resources resources;

  @Override
  protected LinkedHashSet<URI> getImportedUris(final Resource resource) {
    return new LinkedHashSet<>(visibility.importedUris(resources.rootOf(resource), true));
  }
}
