/*
 * Copyright (c) 2015 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.validation;

import static com.google.eclipse.protobuf.junit.core.IntegrationTestModule.integrationTestModule;
import static com.google.eclipse.protobuf.junit.core.XtextRule.overrideRuntimeModuleWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Rule;
import org.junit.Test;

import com.google.eclipse.protobuf.junit.core.XtextRule;
import com.google.eclipse.protobuf.protobuf.IndexRange;
import com.google.inject.Inject;

public class ProtobufJavaValidator_checkIndexRangeBounds {
  @Rule public XtextRule xtext = overrideRuntimeModuleWith(integrationTestModule());

  @Inject private org.eclipse.xtext.validation.IResourceValidator resourceValidator;
  @Inject private com.google.eclipse.protobuf.model.util.IndexRanges ranges;

  @Test public void should_reject_nonpositive_message_ranges() {
    assertSemanticError("syntax = 'proto2'; message NegativeRange { reserved -2 to 2; }");
    assertSemanticError("syntax = 'proto2'; message ZeroRange { reserved 0; }");
    assertSemanticError("syntax = 'proto2'; message ZeroExtension { extensions 0 to 2; }");
  }

  @Test public void should_reject_backward_but_accept_single_number_ranges() {
    assertSemanticError("syntax = 'proto2'; message Backward { reserved 3 to 1; }");
    xtext.parseText("syntax = 'proto2'; message Singleton { reserved 4 to 4; }");
    List<org.eclipse.xtext.validation.Issue> issues = resourceValidator.validate(
        xtext.resource(), org.eclipse.xtext.validation.CheckMode.ALL, null);
    assertTrue(xtext.resource().getErrors().toString(), xtext.resource().getErrors().isEmpty());
    assertTrue(issues.toString(), issues.isEmpty());
  }

  // syntax = "proto3";
  // message MessageReservations { reserved 7 to max; int32 value = 1; }
  // enum EnumReservations { ZERO = 0; NEGATIVE = -1; reserved -2147483648 to -2, 7 to max; }
  @Test public void should_accept_owner_specific_max_and_negative_enum_reservations() throws Exception {
    assertTrue(resourceValidator.validate(xtext.resource(), org.eclipse.xtext.validation.CheckMode.ALL, null).isEmpty());
    assertTrue(xtext.resource().getErrors().isEmpty());
    List<IndexRange> parsed = xtext.findAll(IndexRange.class);
    assertEquals(Long.valueOf(536870911L), ranges.toLongRange(parsed.get(0)).upperEndpoint());
    assertEquals(Long.valueOf(Integer.MAX_VALUE), ranges.toLongRange(parsed.get(2)).upperEndpoint());
  }

  // syntax = "proto2";
  // message NumericReservations { reserved 010 to 0x0f; optional int32 value = 1; }
  @Test public void should_decode_octal_and_hex_range_endpoints() throws Exception {
    assertTrue(resourceValidator.validate(xtext.resource(), org.eclipse.xtext.validation.CheckMode.ALL, null).isEmpty());
    assertEquals(com.google.common.collect.Range.closed(8L, 15L), ranges.toLongRange(xtext.findFirst(IndexRange.class)));
  }

  @Test public void should_reject_range_overflow_and_backward_enum_ranges() {
    assertSemanticError("syntax = 'proto2'; message R { reserved 1 to 536870912; }");
    assertSemanticError("syntax = 'proto2'; enum E { ZERO = 0; reserved -2147483649; }");
    assertSemanticError("syntax = 'proto2'; enum E { ZERO = 0; reserved 1 to 2147483648; }");
    assertSemanticError("syntax = 'proto2'; enum E { ZERO = 0; reserved -1 to -2; }");
  }

  @Test public void should_reject_mixed_enum_reservations_and_overlapping_negative_ranges() {
    assertSemanticError("syntax = 'proto2'; enum E { ZERO = 0; reserved -2, 'REMOVED'; }");
    assertSemanticError("syntax = 'proto2'; enum E { ZERO = 0; reserved -5 to -2; reserved -3 to -1; }");
    assertSemanticError("syntax = 'proto2'; enum E { ZERO = 0; reserved 'REMOVED'; reserved 'REMOVED'; }");
  }

  private void assertSemanticError(String source) {
    xtext.parseText(source);
    List<org.eclipse.xtext.validation.Issue> issues = resourceValidator.validate(
        xtext.resource(), org.eclipse.xtext.validation.CheckMode.ALL, null);
    assertTrue(xtext.resource().getErrors().toString(), xtext.resource().getErrors().isEmpty());
    assertTrue(issues.toString(), issues.stream().anyMatch(
        issue -> issue.getSeverity() == org.eclipse.xtext.diagnostics.Severity.ERROR));
  }
}
