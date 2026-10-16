package com.google.eclipse.protobuf.model.util;

import static com.google.eclipse.protobuf.junit.core.IntegrationTestModule.integrationTestModule;
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
  @Rule public XtextRule xtext = overrideRuntimeModuleWith(integrationTestModule());
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

  @Test public void should_allow_editions_defaults_only_with_explicit_singular_scalar_or_enum_presence() {
    for (String year : new String[] {"2023", "2024"}) {
      xtext.parseText("edition='" + year + "'; enum E { ZERO=0; } message M { "
          + "int32 scalar=1; E enumeration=2; repeated int32 numbers=3; repeated string strings=4; "
          + "M child=5; map<string,int32> entries=6; oneof choice { int32 member=7; } "
          + "int32 required_value=8 [features.field_presence=LEGACY_REQUIRED]; "
          + "int32 implicit_value=9 [features.field_presence=IMPLICIT]; }");
      for (String name : new String[] {"scalar", "enumeration", "required_value"}) {
        assertTrue(name, fields.supportsExplicitDefault(xtext.find(name, MessageField.class)));
      }
      for (String name : new String[] {"numbers", "strings", "child", "entries", "member", "implicit_value"}) {
        assertFalse(name, fields.supportsExplicitDefault(xtext.find(name, MessageField.class)));
      }
      assertTrue(fields.isPackable(xtext.find("numbers", MessageField.class)));
      assertFalse(fields.isPackable(xtext.find("strings", MessageField.class)));
      assertFalse(fields.isPackable(xtext.find("scalar", MessageField.class)));
    }
  }

  @Test public void should_share_inherited_presence_capability_with_dotted_and_aggregate_overrides() {
    xtext.parseText("edition='2023'; option features.field_presence=IMPLICIT; message M { "
        + "int32 inherited=1; int32 dotted=2 [features.field_presence=EXPLICIT]; "
        + "int32 aggregate=3 [features={field_presence:EXPLICIT}]; "
        + "message Nested { int32 nested=1; } "
        + "message Pinned { int32 pinned=1 [features={field_presence:EXPLICIT}]; } "
        + "extensions 100 to 199; } extend M { int32 extension_value=100; }");
    for (String name : new String[] {"inherited", "nested"}) {
      assertFalse(name, fields.supportsExplicitDefault(xtext.find(name, MessageField.class)));
    }
    for (String name : new String[] {"dotted", "aggregate", "pinned", "extension_value"}) {
      assertTrue(name, fields.supportsExplicitDefault(xtext.find(name, MessageField.class)));
    }
  }

  @Test public void should_not_guess_default_capability_for_unresolved_or_unknown_editions() {
    xtext.parseText("edition='2023'; message M { Missing unresolved=1; }");
    assertFalse(fields.supportsExplicitDefault(xtext.find("unresolved", MessageField.class)));
    xtext.parseText("edition='2025'; message M { int32 scalar=1; }");
    assertFalse(fields.supportsExplicitDefault(xtext.find("scalar", MessageField.class)));
  }
}
