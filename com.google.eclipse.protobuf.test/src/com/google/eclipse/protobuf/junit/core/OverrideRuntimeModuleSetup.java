/*
 * Copyright (c) 2011 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.junit.core;

import static java.util.Arrays.copyOf;

import org.eclipse.emf.ecore.EValidator;
import org.eclipse.emf.ecore.impl.EValidatorRegistryImpl;
import org.eclipse.xtext.resource.IResourceServiceProvider;
import org.eclipse.xtext.resource.impl.ResourceServiceProviderRegistryImpl;

import com.google.eclipse.protobuf.ProtobufRuntimeModule;
import com.google.eclipse.protobuf.ProtobufStandaloneSetup;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;

/**
 * @author alruiz@google.com (Alex Ruiz)
 */
public class OverrideRuntimeModuleSetup extends ProtobufStandaloneSetup {
  private final Module[] modules;

  public OverrideRuntimeModuleSetup(Module[] modules) {
    this.modules = copyOf(modules, modules.length);
  }

  @Override public Injector createInjector() {
    Module current = new ProtobufRuntimeModule();
    for (Module module : modules) {
      current = Modules.override(current).with(module);
    }
    // Registrars and cross-resource service lookup must never reuse another fixture's injector.
    current = Modules.override(current).with(new AbstractModule() {
      @Override protected void configure() {
        bind(EValidator.Registry.class).toInstance(new EValidatorRegistryImpl());
        bind(IResourceServiceProvider.Registry.class).toInstance(new ResourceServiceProviderRegistryImpl());
      }
    });
    Injector injector = Guice.createInjector(current);
    IResourceServiceProvider.Registry registry = injector.getInstance(IResourceServiceProvider.Registry.class);
    IResourceServiceProvider provider = injector.getInstance(IResourceServiceProvider.class);
    registry.getExtensionToFactoryMap().put("proto", provider);
    registry.getExtensionToFactoryMap().put("protodevel", provider);
    return injector;
  }
}
