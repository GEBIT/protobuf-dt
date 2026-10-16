/*
 * Copyright (c) 2014 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.junit.core;

import static com.google.eclipse.protobuf.junit.core.GeneratedProtoFiles.protoFile;
import static org.eclipse.xtext.util.Strings.isEmpty;
import java.io.File;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EReference;
import com.google.eclipse.protobuf.model.util.Imports;
import com.google.eclipse.protobuf.protobuf.Import;
import com.google.eclipse.protobuf.scoping.IImportResolver;
import com.google.eclipse.protobuf.scoping.ProtoDescriptorProvider;
import com.google.eclipse.protobuf.util.Uris;
import com.google.inject.Inject;

/**
 * Guice module for unit testing.
 *
 * @author alruiz@google.com (Alex Ruiz)
 */
public class IntegrationTestModule extends AbstractTestModule {
  public static IntegrationTestModule integrationTestModule() {
    return new IntegrationTestModule();
  }

  private IntegrationTestModule() {}

  @Override
  protected void configure() {
    binder().bind(IImportResolver.class).to(ImportResolver.class);
    mockAndBind(EReference.class);
  }

  private static class ImportResolver implements IImportResolver {
    @Inject private Imports imports;
    @Inject private ProtoDescriptorProvider descriptors;
    @Inject private Uris uris;

    @Override
    public String resolve(Import anImport) {
      String importUri = imports.getPath(anImport);
      if (importUri == null) {
        return null;
      }
      URI descriptor = descriptors.descriptorLocation(anImport.eResource(), importUri);
      if (descriptor != null) {
        return uris.referredResourceExists(descriptor) ? descriptor.toString() : null;
      }
      URI uri = URI.createURI(importUri);
      if (!isEmpty(uri.scheme())) {
        return uris.referredResourceExists(uri) ? importUri : null;
      }
      File file = protoFile(importUri);
      if (!file.exists()) {
        return null; // file does not exist.
      }
      return file.toURI().toString();
    }

    @Override
    public void invalidateCacheFor(Import anImport) {}
  }
}
