/*
 * Copyright (c) 2026 GEBIT Solutions GmbH.
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.scoping;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;

import com.google.eclipse.protobuf.model.util.FeatureSets;
import com.google.eclipse.protobuf.model.util.Imports;
import com.google.eclipse.protobuf.model.util.Protobufs;
import com.google.eclipse.protobuf.model.util.Resources;
import com.google.eclipse.protobuf.model.util.Syntaxes;
import com.google.eclipse.protobuf.protobuf.Enum;
import com.google.eclipse.protobuf.protobuf.Import;
import com.google.eclipse.protobuf.protobuf.Message;
import com.google.eclipse.protobuf.protobuf.OptionImport;
import com.google.eclipse.protobuf.protobuf.Protobuf;
import com.google.eclipse.protobuf.protobuf.SymbolVisibilityEnum;
import com.google.eclipse.protobuf.resource.ResourceSets;
import com.google.eclipse.protobuf.util.Uris;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/** Import reachability and declaration visibility shared by actual reference scopes. */
@Singleton
public class ImportVisibility {
  @Inject private Imports imports;
  @Inject private Protobufs protobufs;
  @Inject private Resources resources;
  @Inject private ResourceSets resourceSets;
  @Inject private ProtoDescriptorProvider descriptorProvider;
  @Inject private Uris uris;
  @Inject private Syntaxes syntaxes;
  @Inject private FeatureSets features;

  /** Returns direct imports of the requested mode and only their public-import closure. */
  public Set<URI> importedUris(Protobuf root, boolean forOptions) {
    Set<URI> result = new LinkedHashSet<>();
    if (root == null || root.eResource() == null) {
      return result;
    }
    ResourceSet resourceSet = root.eResource().getResourceSet();
    if (resourceSet == null) {
      return result;
    }
    Set<URI> visited = new LinkedHashSet<>();
    visited.add(normalizedUri(root.eResource().getURI(), resourceSet));
    Deque<Import> pending = new ArrayDeque<>();
    for (Import declaration : protobufs.importsIn(root)) {
      if (forOptions || !(declaration instanceof OptionImport)) {
        pending.addLast(declaration);
      }
    }
    while (!pending.isEmpty()) {
      Import declaration = pending.removeFirst();
      URI location = imports.resolvedUriOf(declaration);
      Resource imported;
      if (location != null) {
        URI normalized = normalizedUri(location, resourceSet);
        if (!visited.add(normalized)) {
          continue;
        }
        imported = imports.importedResource(declaration);
        if (imported == null || resources.rootOf(imported) == null) {
          continue;
        }
        result.add(normalizedUri(imported.getURI(), resourceSet));
      } else {
        // Canonical descriptor names are resolved in the importing resource's project context.
        location = descriptorProvider.descriptorLocation(declaration.eResource(), imports.getPath(declaration));
        if (location == null || !uris.referredResourceExists(location)) {
          continue;
        }
        URI normalized = normalizedUri(location, resourceSet);
        if (!visited.add(normalized)) {
          continue;
        }
        result.add(normalized);
        imported = resourceSets.findResource(resourceSet, location);
      }
      if (imported != null) {
        Protobuf dependency = resources.rootOf(imported);
        if (dependency != null) {
          pending.addAll(protobufs.publicImportsIn(dependency));
        }
      }
    }
    result.remove(normalizedUri(root.eResource().getURI(), resourceSet));
    return result;
  }

  /** Applies only a type declaration's own visibility, never its ancestors' visibility. */
  public boolean isVisible(EObject target, EObject context) {
    if (target == null || context == null || context.eResource() == null) {
      return false;
    }
    ResourceSet resourceSet = context.eResource().getResourceSet();
    if (target.eIsProxy() && resourceSet != null) {
      target = EcoreUtil.resolve(target, resourceSet);
    }
    if (target.eIsProxy() || target.eResource() == null) {
      return false;
    }
    if (target.eResource() == context.eResource()
        || normalizedUri(target.eResource().getURI(), resourceSet)
            .equals(normalizedUri(context.eResource().getURI(), resourceSet))) {
      return true;
    }
    if (!(target instanceof Message) && !(target instanceof Enum)
        || !syntaxes.isEdition2024(target)) {
      return true;
    }
    SymbolVisibilityEnum visibility = target instanceof Message
        ? ((Message) target).getVisibility() : ((Enum) target).getVisibility();
    if (visibility == SymbolVisibilityEnum.LOCAL) {
      return false;
    }
    if (visibility == SymbolVisibilityEnum.EXPORT) {
      return true;
    }
    String defaultVisibility = features.coreValue(target, "default_symbol_visibility");
    return "EXPORT_ALL".equals(defaultVisibility)
        || "EXPORT_TOP_LEVEL".equals(defaultVisibility) && target.eContainer() instanceof Protobuf;
  }

  /** Uses the originating ResourceSet's URI mappings for both imports and indexed targets. */
  static URI normalizedUri(URI uri, ResourceSet resourceSet) {
    if (uri == null) {
      return null;
    }
    URI resourceUri = uri.trimFragment();
    return resourceSet == null ? resourceUri : resourceSet.getURIConverter().normalize(resourceUri);
  }
}
