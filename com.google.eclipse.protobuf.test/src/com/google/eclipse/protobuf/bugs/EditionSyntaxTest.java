/*
 * Copyright (c) 2026 GEBIT.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.bugs;

import static com.google.eclipse.protobuf.junit.core.UnitTestModule.unitTestModule;
import static com.google.eclipse.protobuf.junit.core.XtextRule.overrideRuntimeModuleWith;
import static org.junit.Assert.assertTrue;

import com.google.eclipse.protobuf.junit.core.XtextRule;
import com.google.eclipse.protobuf.validation.ProtobufJavaValidator;
import com.google.inject.Inject;

import org.eclipse.emf.common.util.BasicDiagnostic;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests parsing and validation of protobuf editions.
 */
public class EditionSyntaxTest {
  @Rule public XtextRule xtext = overrideRuntimeModuleWith(unitTestModule());

  @Inject private ProtobufJavaValidator validator;

  @Test public void shouldAllowEdition2023() {
    xtext.parseText("edition = \"2023\";\nmessage Foo { string bar = 1; }");
    assertTrue(xtext.root().getSyntax().isEdition());
    boolean isValid = validator.validate(xtext.root(), new BasicDiagnostic(), null);
    assertTrue(isValid);
  }

  // edition = "2023";
  //
  // message Foo {
  //   string bar = 1;
  // }
  @Test public void shouldAllowEdition2024() {
    xtext.parseText("edition = \"2024\";\nexport message Foo { string bar = 1; }\n"
        + "enum Kind { reserved OLD; KIND_UNSPECIFIED = 0; }");
    assertTrue(xtext.root().getSyntax().isEdition());
    boolean isValid = validator.validate(xtext.root(), new BasicDiagnostic(), null);
    assertTrue(isValid);
  }

  @Test public void shouldRejectEditionFieldPresenceLabels() {
    xtext.parseText("edition = \"2023\";\nmessage Foo { optional string bar = 1; }");
    boolean isValid = validator.validate(xtext.root(), new BasicDiagnostic(), null);
    assertTrue(!isValid);
  }

  @Test public void shouldRejectGroupsInEditions() {
    xtext.parseText("edition = \"2023\";\nmessage Foo { group Bar = 1 { string value = 1; } }");
    boolean isValid = validator.validate(xtext.root(), new BasicDiagnostic(), null);
    assertTrue(!isValid);
  }

  @Test public void shouldParseEdition2024OptionImports() {
    xtext.parseText("edition = \"2024\";\nimport option \"options.proto\";");
    assertTrue(xtext.root().getSyntax().isEdition());
  }
}
