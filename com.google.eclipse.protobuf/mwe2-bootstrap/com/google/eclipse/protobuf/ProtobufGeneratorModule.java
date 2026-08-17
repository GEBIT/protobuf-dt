/*
 * Copyright (c) 2011 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf;

import org.eclipse.xtext.xtext.ecoreInference.IXtext2EcorePostProcessor;
import org.eclipse.xtext.xtext.generator.validation.ValidatorNaming;

import com.google.inject.Binder;
import com.google.inject.Module;

/**
 * Guice module used only by the Xtext generator (see {@code GenerateProtobuf.mwe2}) while
 * generating sources for the Protobuf language. It is not part of the runtime bundle's
 * dependency injection setup (see {@link ProtobufRuntimeModule} for that).
 *
 * <p>Replaces the former {@code ProtobufGenerator} class, which achieved the same thing by
 * subclassing the (now removed) legacy {@code org.eclipse.xtext.generator.Generator}.
 *
 * @author alruiz@google.com (Alex Ruiz)
 */
@SuppressWarnings("restriction")
public class ProtobufGeneratorModule implements Module {
  @Override public void configure(Binder binder) {
    binder.bind(IXtext2EcorePostProcessor.class).to(ProtobufEcorePostProcessor.class);
    binder.bind(ValidatorNaming.class).to(ProtobufValidatorNaming.class);
  }
}
