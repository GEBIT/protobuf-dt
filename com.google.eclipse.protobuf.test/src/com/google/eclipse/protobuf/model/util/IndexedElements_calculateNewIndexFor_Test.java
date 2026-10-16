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
import com.google.eclipse.protobuf.protobuf.IndexedElement;
import com.google.eclipse.protobuf.protobuf.MessageField;
import com.google.inject.Inject;

/**
 * Tests for <code>{@link IndexedElements#calculateNewIndexFor(IndexedElement)}</code>.
 *
 * @author alruiz@google.com (Alex Ruiz)
 */
public class IndexedElements_calculateNewIndexFor_Test {
  @Rule public XtextRule xtext = overrideRuntimeModuleWith(unitTestModule());

  @Inject private IndexedElements indexedElements;

  // syntax = "proto2";
  //
  // message Person {
  //   required string name = 2;
  // }
  @Test public void should_return_one_for_first_and_only_field() {
    MessageField field = xtext.find("name", MessageField.class);
    long index = indexedElements.calculateNewIndexFor(field);
    assertThat(index, equalTo(3L));
  }

  // syntax = "proto2";
  //
  // message Person {
  //   required string name = 6;
  //   required int32 id = 8;
  // }
  @Test public void should_return_max_index_value_plus_one_for_new_field() {
    MessageField field = xtext.find("id", MessageField.class);
    long index = indexedElements.calculateNewIndexFor(field);
    assertThat(index, equalTo(9L));
  }

  // syntax = "proto3";
  // message AvailableGap { int32 first = 1; int32 second = 2; reserved 4 to max; }
  @Test public void should_find_remaining_gap_before_reserved_to_max() {
    MessageField field = xtext.find("second", MessageField.class);
    assertThat(indexedElements.calculateNewIndexFor(field), equalTo(3L));
    assertThat(indexedElements.calculateNewIndexAfter(field, 3L), equalTo(-1L));
  }

  // syntax = "proto3";
  // message NoNumbers { reserved 1 to max; }
  @Test public void should_return_minus_one_when_field_domain_is_exhausted() {
    MessageField pending = com.google.eclipse.protobuf.protobuf.ProtobufFactory.eINSTANCE.createMessageField();
    xtext.findFirst(com.google.eclipse.protobuf.protobuf.Message.class).getElements().add(pending);
    assertThat(indexedElements.calculateNewIndexFor(pending), equalTo(-1L));
  }

  // syntax = "proto3";
  // message ImplementationGap { int32 last = 18999; }
  @Test public void should_skip_implementation_reserved_interval() {
    assertThat(indexedElements.calculateNewIndexFor(xtext.findFirst(MessageField.class)), equalTo(20000L));
  }

  // syntax = "proto2";
  // message ParentDomain {
  //   reserved 2 to max;
  //   optional group Child = 1 { optional int32 value = 1; }
  // }
  @Test public void should_allocate_inside_the_group_not_the_parent_message() {
    assertThat(indexedElements.calculateNewIndexFor(xtext.find("value", MessageField.class)), equalTo(2L));
  }

  // syntax = "proto3";
  // message OneofAllocation { int32 first = 1; oneof choice { string second = 2; } reserved 4 to max; }
  @Test public void should_share_oneof_numbers_in_bounded_allocation() {
    assertThat(indexedElements.calculateNewIndexFor(xtext.find("first", MessageField.class)), equalTo(3L));
  }

  // syntax = "proto2";
  // message ExtensionAllocation { optional int32 first = 1; extensions 2 to max; }
  @Test public void should_not_allocate_inside_extension_ranges() {
    assertThat(indexedElements.calculateNewIndexFor(xtext.findFirst(MessageField.class)), equalTo(-1L));
  }

  // syntax = "proto3";
  // message LastNumber { int32 last = 536870911; }
  @Test public void should_find_a_low_gap_instead_of_overflowing_the_field_domain() {
    assertThat(indexedElements.calculateNewIndexFor(xtext.findFirst(MessageField.class)), equalTo(1L));
  }
}
