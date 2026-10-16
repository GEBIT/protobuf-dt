/*
 * Copyright (c) 2011 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.model.util;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

import static com.google.eclipse.protobuf.junit.core.UnitTestModule.unitTestModule;
import static com.google.eclipse.protobuf.junit.core.XtextRule.overrideRuntimeModuleWith;

import org.junit.Rule;
import org.junit.Test;

import com.google.eclipse.protobuf.junit.core.XtextRule;
import com.google.eclipse.protobuf.protobuf.Literal;
import com.google.inject.Inject;

/**
 * Tests for <code>{@link Literals#calculateNewIndexOf(Literal)}</code>.
 *
 * @author alruiz@google.com (Alex Ruiz)
 */
public class Literals_calculateNewIndexOf_Test {
  @Rule public XtextRule xtext = overrideRuntimeModuleWith(unitTestModule());

  @Inject private Literals literals;

  // syntax = "proto2";
  //
  // enum PhoneType {
  //   MOBILE = 1;
  // }
  @Test public void should_return_zero_for_first_and_only_literal() {
    Literal mobile = xtext.find("MOBILE", Literal.class);
    long index = literals.calculateNewIndexOf(mobile);
    assertThat(index, equalTo(0L));
  }

  // syntax = "proto2";
  //
  // enum PhoneType {
  //   MOBILE = 1;
  //   HOME = 5;
  //   WORK = 9;
  // }
  @Test public void should_return_max_index_value_plus_one_for_new_literal() {
    Literal work = xtext.find("WORK", Literal.class);
    long index = literals.calculateNewIndexOf(work);
    assertThat(index, equalTo(6L));
  }

  // syntax = "proto3";
  // enum FirstProto3 { reserved 8; FIRST = 9; SECOND = 2; }
  @Test public void should_propose_zero_for_first_proto3_literal() {
    assertThat(literals.calculateNewIndexOf(xtext.find("FIRST", Literal.class)), equalTo(0L));
  }

  // syntax = "proto2";
  // enum EnumGap { FIRST = 0; SECOND = 1; reserved 3 to max; }
  @Test public void should_skip_enum_reserved_ranges_and_pending_assignment() {
    Literal second = xtext.find("SECOND", Literal.class);
    assertThat(literals.calculateNewIndexOf(second), equalTo(1L));
    assertThat(literals.calculateNewIndexAfter(second, 1L), equalTo(2L));
  }

  // syntax = "proto2";
  // enum NegativeGap { FIRST = 0; reserved 1 to max, -2147483648 to -2; }
  @Test public void should_preserve_minus_one_as_a_valid_enum_proposal() {
    Literal first = xtext.findFirst(Literal.class);
    assertThat(literals.calculateNewIndexAfter(first, 0L), equalTo(-1L));
  }

  // syntax = "proto2";
  // enum ExhaustedEnum { FIRST = 0; reserved -2147483648 to max; }
  @Test public void should_use_outside_int32_sentinel_for_exhausted_enum_domain() {
    assertThat(literals.calculateNewIndexOf(xtext.findFirst(Literal.class)), equalTo(Long.MIN_VALUE));
  }

  // syntax = "proto2";
  // enum LastEnumNumber { FIRST = 2147483647; NEXT = 1; }
  @Test public void should_find_a_gap_without_wrapping_enum_number() {
    assertThat(literals.calculateNewIndexOf(xtext.find("NEXT", Literal.class)), equalTo(0L));
  }
}
