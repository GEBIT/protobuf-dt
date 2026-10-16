/*
 * Copyright (c) 2014 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.ui.scoping;

import com.google.eclipse.protobuf.model.util.Imports;
import com.google.eclipse.protobuf.protobuf.Import;
import com.google.eclipse.protobuf.scoping.IImportResolver;
import com.google.eclipse.protobuf.scoping.IUriResolver;
import com.google.eclipse.protobuf.scoping.ProtoDescriptorProvider;
import com.google.eclipse.protobuf.util.EResources;
import com.google.eclipse.protobuf.util.Uris;
import com.google.inject.Inject;

import org.eclipse.core.resources.IProject;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.util.IResourceScopeCache;
import org.eclipse.xtext.util.Tuples;


/**
 * Resolves "import" URIs.
 *
 * @author alruiz@google.com (Alex Ruiz)
 */
public class ImportResolver implements IImportResolver {
  @Inject private ProtoDescriptorProvider descriptorProvider;
  @Inject private Imports imports;
  @Inject private IUriResolver resolver;
  @Inject private Uris uris;

  @Inject private IResourceScopeCache cache;

  /*
   * The import URI is relative to the file where the import is. Protoc works fine, but the editor doesn't.
   * In order for the editor to see the import, we need to add to the import URI "platform:resource" and the parent
   * folder of the file containing the import.
   *
   * For example: given the following file hierarchy:
   *
   * - protobuf-test (project)
   *   - folder
   *     - proto2.proto
   *   - proto1.proto
   *
   * If we import "folder/proto2.proto" into proto1.proto, proto1.proto will compile fine, but the editor will complain.
   * We need to have the import URI as "platform:/resource/protobuf-test/folder/proto2.proto" for the editor to see it.
   */
  @Override public String resolve(final Import anImport) {
    if (anImport == null || anImport.eResource() == null || imports.getPath(anImport) == null) {
      return null;
    }
    return cache.get(Tuples.create(ImportResolver.class, anImport), anImport.eResource(),
        () -> internalResolveUri(anImport));
  }

  private String internalResolveUri(Import anImport) {
    return resolveUri(imports.getPath(anImport), anImport.eResource());
  }
  
  private static final URI WELL_KNOWN_TYPES_LOCATION =
	      URI.createURI("platform:/plugin/de.gebit.eclipse.protobuf/");

  private String resolveUri(String importUri, Resource resource) {
    URI location = descriptorProvider.descriptorLocation(resource, importUri);
    if (location != null) {
      // Explicit descriptor selection is authoritative, including an unreadable selection.
      return uris.referredResourceExists(location) ? location.toString() : null;
    }
    IProject project = EResources.getProjectOf(resource);
    String resolved = resolver.resolveUri(importUri, resource.getURI(), project);
    if (resolved != null) {
      return resolved;
    }
    if (importUri.startsWith("google/protobuf/")) {
      URI bundled = URI.createURI(importUri).resolve(WELL_KNOWN_TYPES_LOCATION);
      return uris.referredResourceExists(bundled) ? bundled.toString() : null;
    }
    return null;
  }

  @Override
  public void invalidateCacheFor(Import anImport) {
    if (anImport != null && anImport.eResource() != null) {
      cache.clear(anImport.eResource());
    }
  }
  
  
}
