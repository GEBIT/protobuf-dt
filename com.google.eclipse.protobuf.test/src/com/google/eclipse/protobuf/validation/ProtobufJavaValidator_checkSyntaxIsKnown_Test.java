/*
 * Copyright (c) 2011 Google Inc.
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.validation;

import static com.google.eclipse.protobuf.junit.core.UnitTestModule.unitTestModule;
import static com.google.eclipse.protobuf.junit.core.XtextRule.overrideRuntimeModuleWith;
import static org.junit.Assert.*;

import java.util.List;

import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.eclipse.xtext.validation.Issue;
import org.junit.Rule;
import org.junit.Test;

import com.google.eclipse.protobuf.junit.core.XtextRule;
import com.google.inject.Inject;

public class ProtobufJavaValidator_checkSyntaxIsKnown_Test {
  @Rule public XtextRule xtext = overrideRuntimeModuleWith(unitTestModule());
  @Inject private IResourceValidator validator;

  @Test public void should_accept_both_supported_dialects_with_their_field_rules() {
    xtext.parseText("syntax = 'proto2'; message M { optional int32 value = 1; }");
    List<Issue> issues = issues();
    assertTrue(issues.toString(), issues.isEmpty());
    xtext.parseText("syntax = 'proto3'; message M { int32 value = 1; }");
    issues = issues();
    assertTrue(issues.toString(), issues.isEmpty());
  }

  @Test public void should_diagnose_unsupported_syntax_without_proto2_field_fallback() {
    xtext.parseText("syntax = 'proto5'; message M { int32 value = 1; }");
    List<Issue> issues = issues();
    assertTrue(issues.toString(), issues.stream().anyMatch(issue ->
        ProtobufJavaValidator.SYNTAX_IS_NOT_KNOWN_ERROR.equals(issue.getCode())));
    assertFalse(issues.toString(), issues.stream().anyMatch(issue ->
        ProtobufJavaValidator.MISSING_MODIFIER_ERROR.equals(issue.getCode())));
  }

  private List<Issue> issues() {
    return validator.validate(xtext.resource(), CheckMode.ALL, CancelIndicator.NullImpl);
  }
}
