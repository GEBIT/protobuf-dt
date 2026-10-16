package com.google.eclipse.protobuf.model.util;

import static com.google.eclipse.protobuf.junit.core.UnitTestModule.unitTestModule;
import static com.google.eclipse.protobuf.junit.core.XtextRule.overrideRuntimeModuleWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Rule;
import org.junit.Test;

import com.google.eclipse.protobuf.junit.core.XtextRule;
import com.google.eclipse.protobuf.protobuf.Message;
import com.google.eclipse.protobuf.protobuf.MessageField;
import com.google.inject.Inject;

public class MessageFields_legality_Test {
  @Rule public XtextRule xtext = overrideRuntimeModuleWith(unitTestModule());
  @Inject private MessageFields fields;

  // syntax = "proto2";
  // enum E { ZERO = 0; }
  // message M {
  //   optional uint32 scalar = 1;
  //   required E enumeration = 2;
  //   repeated int32 numbers = 3;
  //   repeated string strings = 4;
  //   optional M child = 5;
  //   map<string, int32> entries = 6;
  //   oneof choice { int32 member = 7; }
  // }
  @Test public void should_restrict_defaults_and_packing_to_legal_field_kinds() {
    assertTrue(fields.supportsExplicitDefault(xtext.find("scalar", MessageField.class)));
    assertTrue(fields.supportsExplicitDefault(xtext.find("enumeration", MessageField.class)));
    for (String name : new String[] {"numbers", "strings", "child", "entries", "member"}) {
      assertFalse(fields.supportsExplicitDefault(xtext.find(name, MessageField.class)));
    }
    assertTrue(fields.isPackable(xtext.find("numbers", MessageField.class)));
    for (String name : new String[] {"scalar", "enumeration", "strings", "child", "entries", "member"}) {
      assertFalse(fields.isPackable(xtext.find(name, MessageField.class)));
    }
  }

  // syntax = "proto3";
  // message M { optional int32 count = 1; repeated uint32 numbers = 2; }
  @Test public void should_preserve_optional_presence_without_explicit_default() {
    assertFalse(fields.supportsExplicitDefault(xtext.find("count", MessageField.class)));
    assertTrue(fields.isPackable(xtext.find("numbers", MessageField.class)));
  }

  // syntax = "proto2";
  // message M {
  //   optional int32 before = 1;
  //   message Nested { optional int32 hidden = 1; }
  //   optional group Body = 2 { optional int32 hiddenGroup = 1; }
  //   oneof choice { int32 first = 3; string second = 4; }
  //   optional int32 after = 5;
  // }
  @Test public void should_collect_only_message_owned_fields_in_source_order() {
    List<MessageField> owned = fields.fieldsOf(xtext.find("M", Message.class));
    assertEquals(4, owned.size());
    assertEquals("before", owned.get(0).getName());
    assertEquals("first", owned.get(1).getName());
    assertEquals("second", owned.get(2).getName());
    assertEquals("after", owned.get(3).getName());
  }
}
