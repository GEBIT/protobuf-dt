/*
 * Copyright (c) 2011 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.validation;

import static com.google.eclipse.protobuf.junit.core.IntegrationTestModule.integrationTestModule;
import static com.google.eclipse.protobuf.junit.core.XtextRule.overrideRuntimeModuleWith;
import static com.google.eclipse.protobuf.validation.ProtobufJavaValidator.INVALID_FIELD_TAG_NUMBER_ERROR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;

import com.google.eclipse.protobuf.junit.core.XtextRule;
import com.google.inject.Inject;

/**
 * Resource-level field and group tag-domain regressions.
 */
public class ProtobufJavaValidator_checkTagNumberIsGreaterThanZero_Test {
  @Rule public XtextRule xtext = overrideRuntimeModuleWith(integrationTestModule());

  @Inject private org.eclipse.xtext.validation.IResourceValidator resourceValidator;

  @Test public void should_accept_extreme_legal_tags_and_reject_each_illegal_domain_boundary() {
    xtext.parseText("syntax = 'proto3'; message Limits { int32 first = 1; int32 last = 536870911; }");
    assertTrue(resourceValidator.validate(xtext.resource(), org.eclipse.xtext.validation.CheckMode.ALL, null).isEmpty());
    for (long number : new long[] {0L, -1L, 19000L, 19999L, 536870912L}) {
      xtext.parseText("syntax = 'proto3'; message InvalidTag { int32 value = " + number + "; }");
      java.util.List<org.eclipse.xtext.validation.Issue> issues = resourceValidator.validate(
          xtext.resource(), org.eclipse.xtext.validation.CheckMode.ALL, null);
      assertTrue(xtext.resource().getErrors().isEmpty());
      assertEquals(1, issues.size());
      assertEquals(INVALID_FIELD_TAG_NUMBER_ERROR, issues.get(0).getCode());
    }
  }

  @Test public void should_bound_group_tags_but_not_group_body_tags_by_the_parent() {
    xtext.parseText("syntax = 'proto2'; message GroupLimits { "
        + "optional group Good = 536870911 { optional int32 value = 1; } }");
    assertTrue(resourceValidator.validate(xtext.resource(), org.eclipse.xtext.validation.CheckMode.ALL, null).isEmpty());
    xtext.parseText("syntax = 'proto2'; message GroupLimits { optional group Bad = 536870912 {} }");
    java.util.List<org.eclipse.xtext.validation.Issue> issues = resourceValidator.validate(
        xtext.resource(), org.eclipse.xtext.validation.CheckMode.ALL, null);
    assertTrue(xtext.resource().getErrors().isEmpty());
    assertEquals(1, issues.size());
    assertEquals(INVALID_FIELD_TAG_NUMBER_ERROR, issues.get(0).getCode());
  }
}
