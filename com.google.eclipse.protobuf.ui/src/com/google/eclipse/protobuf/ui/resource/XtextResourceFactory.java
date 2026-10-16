/*
 * Copyright (c) 2011 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.ui.resource;

import static java.util.Collections.singletonMap;

import static org.eclipse.emf.ecore.resource.ContentHandler.UNSPECIFIED_CONTENT_TYPE;
import static org.eclipse.xtext.EcoreUtil2.resolveLazyCrossReferences;
import static org.eclipse.xtext.resource.XtextResource.OPTION_ENCODING;
import static org.eclipse.xtext.util.CancelIndicator.NullImpl;

import static com.google.eclipse.protobuf.util.Encodings.UTF_8;

import java.io.IOException;

import org.eclipse.core.resources.IProject;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.ui.resource.IResourceSetProvider;
import org.eclipse.xtext.util.StringInputStream;

import com.google.eclipse.protobuf.util.Uris;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Factory of <code>{@link XtextResource}</code>s.
 *
 * @author alruiz@google.com (Alex Ruiz)
 */
@Singleton public class XtextResourceFactory {
  @Inject private IResourceSetProvider resourceSetProvider;
  @Inject private Uris uris;

  /** Creates an unloaded resource in the source project's resource set, or the target/default context. */
  public XtextResource createResource(URI uri, IProject originatingProject) {
    IProject project = originatingProject;
    if (project == null && uri.isPlatformResource()) {
      project = uris.projectOfReferredFile(uri);
    }
    ResourceSet resourceSet = resourceSetProvider.get(project);
    return (XtextResource) resourceSet.createResource(uri, UNSPECIFIED_CONTENT_TYPE);
  }

  /** Loads the text into the already selected resource and resolves in that same project context. */
  public void loadResource(XtextResource resource, String contents) throws IOException {
    try (StringInputStream input = new StringInputStream(contents, UTF_8)) {
      resource.load(input, singletonMap(OPTION_ENCODING, UTF_8));
    }
    resolveLazyCrossReferences(resource, NullImpl);
  }
}
