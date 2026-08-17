/*
 * Copyright (c) 2025 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf;

import org.eclipse.xtext.Grammar;
import org.eclipse.xtext.GrammarUtil;
import org.eclipse.xtext.xtext.generator.XtextGeneratorNaming;
import org.eclipse.xtext.xtext.generator.model.TypeReference;
import org.eclipse.xtext.xtext.generator.validation.ValidatorNaming;

import com.google.inject.Inject;

/**
 * Used only by the Xtext generator (see {@code GenerateProtobuf.mwe2} / {@link ProtobufGeneratorModule})
 * to keep the validator class names this project has always used ({@code ProtobufJavaValidator} /
 * {@code AbstractProtobufJavaValidator}, following the naming convention of the old, pre-fragment2
 * {@code JavaValidatorFragment}) instead of the {@code ValidatorFragment2} default of
 * {@code ProtobufValidator} / {@code AbstractProtobufValidator}.
 *
 * @author alruiz@google.com (Alex Ruiz)
 */
public class ProtobufValidatorNaming extends ValidatorNaming {
  @Inject private XtextGeneratorNaming xtextGeneratorNaming;

  @Override public TypeReference getValidatorClass(Grammar grammar) {
    String basePackage = xtextGeneratorNaming.getRuntimeBasePackage(grammar);
    return new TypeReference(basePackage + ".validation." + GrammarUtil.getSimpleName(grammar) + "JavaValidator");
  }

  @Override protected TypeReference getAbstractValidatorClass(Grammar grammar) {
    String basePackage = xtextGeneratorNaming.getRuntimeBasePackage(grammar);
    return new TypeReference(
        basePackage + ".validation.Abstract" + GrammarUtil.getSimpleName(grammar) + "JavaValidator");
  }
}
