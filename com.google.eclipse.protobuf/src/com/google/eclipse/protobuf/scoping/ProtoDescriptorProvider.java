/*
 * Copyright (c) 2011 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.scoping;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.ui.editor.preferences.IPreferenceStoreAccess;
import org.eclipse.xtext.util.IResourceScopeCache;
import org.eclipse.xtext.util.Tuples;

import com.google.common.collect.ImmutableList;
import com.google.eclipse.protobuf.preferences.general.GeneralPreferences;
import com.google.eclipse.protobuf.preferences.general.PreferenceNames;
import com.google.eclipse.protobuf.util.EResources;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/** Selects the descriptor shared by canonical imports and native option scopes. */
@Singleton
public class ProtoDescriptorProvider {
  public static class ProtoDescriptorInfo {
    public final String importUri;
    public final URI location;

    ProtoDescriptorInfo(String importUri, URI location) {
      this.importUri = importUri;
      this.location = location;
    }
  }

  private static final String CANONICAL_IMPORT = PreferenceNames.DEFAULT_DESCRIPTOR_PATH;
  private static final URI DEFAULT_DESCRIPTOR_LOCATION =
      URI.createURI("platform:/plugin/de.gebit.eclipse.protobuf/google/protobuf/descriptor.proto");
  private static final String EXTENSION_ID = "com.google.eclipse.protobuf.descriptorSource";

  private final ProtoDescriptorInfo extensionDescriptor;
  private final IUriResolver resolver;
  private final IPreferenceStoreAccess storeAccess;
  private final IResourceScopeCache cache;
  private final AtomicLong generation = new AtomicLong();

  @Inject
  ProtoDescriptorProvider(IPreferenceStoreAccess storeAccess, IExtensionRegistry registry,
      IUriResolver resolver, IResourceScopeCache cache) {
    this.storeAccess = storeAccess;
    this.resolver = resolver;
    this.cache = cache;
    this.extensionDescriptor = extensionDescriptor(registry);
  }

  public ProtoDescriptorInfo primaryDescriptor(final Resource resource) {
    final String configured = configuredPath(resource);
    if (resource == null) {
      return selectDescriptor(null, configured);
    }
    return cache.get(Tuples.create(ProtoDescriptorProvider.class, configured, generation.get()),
        resource, () -> selectDescriptor(resource, configured));
  }

  public URI descriptorLocation(Resource resource, String importUri) {
    ProtoDescriptorInfo info = descriptor(resource, importUri);
    return info == null ? null : info.location;
  }

  public ProtoDescriptorInfo descriptor(Resource resource, String importUri) {
    if (importUri == null) {
      return null;
    }
    ProtoDescriptorInfo primary = primaryDescriptor(resource);
    if (CANONICAL_IMPORT.equals(importUri) || primary.importUri.equals(importUri)
        || primary.location.toString().equals(importUri)) {
      return primary;
    }
    // An extension may supply a separate, legitimate logical import name.
    if (extensionDescriptor != null && !CANONICAL_IMPORT.equals(extensionDescriptor.importUri)
        && (extensionDescriptor.importUri.equals(importUri)
            || extensionDescriptor.location.toString().equals(importUri))) {
      return extensionDescriptor;
    }
    return null;
  }

  public ImmutableList<URI> allDescriptorLocations(Resource resource) {
    Set<URI> locations = new LinkedHashSet<>();
    locations.add(primaryDescriptor(resource).location);
    if (extensionDescriptor != null && !CANONICAL_IMPORT.equals(extensionDescriptor.importUri)) {
      locations.add(extensionDescriptor.location);
    }
    return ImmutableList.copyOf(locations);
  }

  /** Called by the document lifecycle; no projects, resources or preference listeners are retained. */
  public void invalidateCacheFor(IProject project) {
    // Resource-scope entries are also cleared by that lifecycle. A generation invalidates selections
    // in other sets (including builder sets) without a global resource registry or strong project keys.
    generation.incrementAndGet();
  }

  private String configuredPath(Resource resource) {
    String configured = new GeneralPreferences(storeAccess, EResources.getProjectOf(resource))
        .getDescriptorProtoPath();
    return configured == null || configured.isEmpty() ? CANONICAL_IMPORT : configured;
  }

  private ProtoDescriptorInfo selectDescriptor(Resource resource, String configured) {
    URI declaringUri = resource == null ? null : resource.getURI();
    IProject project = EResources.getProjectOf(resource);
    if (!CANONICAL_IMPORT.equals(configured)) {
      URI uri = URI.createURI(configured, true);
      String resolved = uri.isRelative()
          ? resolver.resolveUri(configured, declaringUri, project) : uri.toString();
      // Keep an unreadable explicit selection authoritative: loading/linking reports the missing
      // descriptor rather than silently taking members from a different descriptor version.
      if (resolved == null) {
        uri = configured.startsWith("/") ? URI.createFileURI(configured)
            : declaringUri == null ? uri : uri.resolve(declaringUri);
      } else {
        uri = URI.createURI(resolved);
      }
      return new ProtoDescriptorInfo(configured, uri);
    }
    if (extensionDescriptor != null) {
      return extensionDescriptor;
    }
    String resolved = resolver.resolveUri(CANONICAL_IMPORT, declaringUri, project);
    return new ProtoDescriptorInfo(CANONICAL_IMPORT,
        resolved == null ? DEFAULT_DESCRIPTOR_LOCATION : URI.createURI(resolved));
  }

  private static ProtoDescriptorInfo extensionDescriptor(IExtensionRegistry registry) {
    if (registry == null) {
      return null;
    }
    IConfigurationElement[] elements = registry.getConfigurationElementsFor(EXTENSION_ID);
    if (elements != null) {
      for (IConfigurationElement element : elements) {
        String name = element.getAttribute("importUri");
        String path = element.getAttribute("path");
        if (name != null && !name.isEmpty() && path != null && !path.isEmpty()) {
          return new ProtoDescriptorInfo(name, URI.createURI("platform:/plugin/"
              + element.getContributor().getName() + "/" + path));
        }
      }
    }
    return null;
  }
}
