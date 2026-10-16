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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static com.google.eclipse.protobuf.junit.core.XtextRule.overrideRuntimeModuleWith;

import java.util.List;

import org.junit.Rule;
import org.junit.Test;

import com.google.eclipse.protobuf.junit.core.XtextRule;
import com.google.eclipse.protobuf.protobuf.IndexRange;
import com.google.eclipse.protobuf.protobuf.MessageField;
import com.google.inject.Inject;

public class ProtobufJavaValidator_checkForIndexConflicts_Test {
  @Rule public XtextRule xtext = overrideRuntimeModuleWith(integrationTestModule());

  @Inject private org.eclipse.xtext.validation.IResourceValidator resourceValidator;
  @Inject private com.google.eclipse.protobuf.scoping.ProtoDescriptorProvider descriptorProvider;
  @Inject private com.google.eclipse.protobuf.model.util.IndexedElements indexedElements;
  @Inject private com.google.eclipse.protobuf.model.util.IndexRanges indexRanges;

  @Test public void should_reject_conflicting_reservations_and_extension_ranges() {
    assertInvalid("syntax = 'proto2'; message RepeatedReservations { reserved 10, 20 to 30; reserved 10; }");
    assertInvalid("syntax = 'proto2'; message OverlappingReservations { reserved 20 to 30; reserved 25 to 35; }");
    assertInvalid("syntax = 'proto2'; message RangeConflict { reserved 20 to 30; extensions 15 to max; }");
    assertInvalid("syntax = 'proto2'; message ExtensionConflict { extensions 10 to 20; extensions 20 to 30; }");
  }

  @Test public void should_reject_actual_field_and_group_uses_of_reserved_or_extension_tags() {
    assertInvalid("syntax = 'proto2'; message ReservedField { reserved 10; optional bool value = 10; }");
    assertInvalid("syntax = 'proto2'; message ReservedGroup { reserved 10; optional group Detail = 10 {} }");
    assertInvalid("syntax = 'proto2'; message ExtensionField { extensions 10 to 20; optional bool value = 15; }");
    assertInvalid("syntax = 'proto2'; message ExtensionGroup { extensions 10 to 20; optional group Detail = 15 {} }");
  }

  @Test public void should_reject_duplicate_ordinary_and_group_tags_in_the_parent_scope() {
    assertInvalid("syntax = 'proto2'; message RepeatedFields { "
        + "optional int32 first = 1; optional int32 second = 1; }");
    assertInvalid("syntax = 'proto2'; message RepeatedGroupTag { "
        + "optional int32 first = 1; optional group Detail = 1 { optional int32 nested = 2; } }");
    assertInvalid("syntax = 'proto2'; message RepeatedGroups { "
        + "optional group First = 1 {} optional group Second = 1 {} }");
  }

  @Test public void should_keep_nested_message_and_extension_fields_out_of_parent_tag_scope() {
    assertValid("syntax = 'proto2'; message Parent { extensions 10 to 20; reserved 30 to 40; "
        + "optional bool value = 50; message Child { optional int32 a = 15; optional int32 b = 35; "
        + "optional int32 c = 50; } }");
    assertValid("syntax = 'proto2'; message Extendee { extensions 1 to max; } "
        + "message Parent { reserved 30 to 40; optional bool value = 50; "
        + "extend Extendee { optional int32 a = 35; optional int32 b = 50; } }");
  }

  @Test public void should_validate_reserved_group_body_fields_only_in_their_own_scope() {
    assertInvalid("syntax = 'proto2'; message Parent { optional group Detail = 10 { "
        + "reserved 1 to 3; optional int32 value = 1; } optional int32 outside = 2; "
        + "optional group Other = 20 { optional int32 value = 3; } }");
  }

  @Test public void should_accept_map_roles_without_phantom_tag_conflicts() {
    assertValid("syntax = 'proto3'; message MapRoles { map<string, int32> entries = 1; "
        + "oneof kind { string text = 2; bool flag = 3; } }");
  }

  @Test public void should_keep_nested_enum_reservations_out_of_message_fields() {
    assertValid("syntax = 'proto2'; message OuterDomain { reserved 1 to max; "
        + "enum E { LAST = 0x7fffffff; FIRST = 1; NEGATIVE = -1; } "
        + "message Child { optional int32 x = 1; } }");
  }

  @Test public void should_keep_group_body_tags_separate_from_parent_and_sibling_groups() {
    assertValid("syntax = 'proto2'; message GroupDomains { reserved 2; "
        + "optional group First = 1 { optional int32 value = 2; } "
        + "optional group Second = 3 { optional int32 value = 2; } }");
  }

  @Test public void should_share_oneof_tag_namespace_with_message_fields() {
    assertInvalid("syntax = 'proto3'; message OneofTags { int32 value = 1; "
        + "oneof choice { string other = 1; } }");
  }

  @Test public void should_check_reserved_alias_even_when_native_aliasing_is_enabled() {
    assertInvalid("syntax = 'proto3'; enum AliasReservation { option allow_alias = true; "
        + "reserved 1; ZERO = 0; FIRST = 1; SECOND = 1; }");
  }

  @Test public void should_accept_real_native_enum_aliases() {
    assertValid("syntax = 'proto3'; enum RealAliases { option allow_alias = true; "
        + "ZERO = 0; FIRST = 1; SECOND = 1; }");
  }

  @Test public void should_reject_no_effect_allow_alias_declarations() {
    assertInvalid("syntax = 'proto2'; enum NoAliasNeeded { option allow_alias = true; FIRST = 1; }");
    assertInvalid("syntax = 'proto2'; enum FalseAlias { option allow_alias = false; FIRST = 1; }");
  }

  @Test public void should_reject_aliasing_without_the_native_option() {
    assertInvalid("syntax = 'proto3'; enum NoAliases { ZERO = 0; FIRST = 1; SECOND = 1; }");
  }

  @Test public void should_check_protoc_enum_prefix_and_case_normalization() {
    assertInvalid("syntax = 'proto3'; enum MyEnum { MY_ENUM_FOO = 0; foo = 1; }");
    assertValid("syntax = 'proto3'; enum Foo { FOO_BAR_BAZ = 0; FOO_BARBAZ = 1; }");
    assertValid("syntax = 'proto2'; enum MyEnum { option allow_alias = true; MY_ENUM_FOO = 1; foo = 1; }");
  }

  @Test public void should_honor_native_legacy_enum_name_conflict_severity() {
    xtext.parseText("syntax = 'proto2'; enum LegacyJson { "
        + "option deprecated_legacy_json_field_conflicts = true; LEGACY_JSON_FOO = 0; foo = 1; }");
    List<org.eclipse.xtext.validation.Issue> issues = validate();
    assertEquals(1, issues.size());
    assertEquals(org.eclipse.xtext.diagnostics.Severity.WARNING, issues.get(0).getSeverity());
  }

  @Test public void should_check_message_oneof_type_and_enum_value_names_together() {
    assertInvalid("syntax = 'proto3'; message SharedNames { int32 value = 1; "
        + "oneof value { string other = 2; } }");
    assertInvalid("syntax = 'proto3'; message SharedTypes { int32 Nested = 1; message Nested {} }");
    assertInvalid("syntax = 'proto3'; message SharedEnumValues { "
        + "enum First { SHARED = 0; } enum Second { SHARED = 0; } }");
  }

  @Test public void should_check_generated_map_entry_names_using_protoc_case_rules() {
    assertInvalid("syntax = 'proto3'; message MapEntryTypes { "
        + "map<string, int32> foo_bar = 1; message FooBarEntry {} }");
    assertInvalid("syntax = 'proto3'; message MapEntryFields { "
        + "map<string, int32> foo_bar = 1; int32 FooBarEntry = 2; }");
    assertInvalid("syntax = 'proto3'; message TwoMapEntries { "
        + "map<string, int32> foo_bar = 1; map<string, int32> FooBar = 2; }");
  }

  @Test public void should_check_top_level_and_rpc_names_without_crossing_scopes() {
    assertInvalid("syntax = 'proto3'; message Duplicate {} enum Duplicate { ZERO = 0; }");
    assertInvalid("syntax = 'proto3'; message Request {} service Api { "
        + "rpc Call(Request) returns(Request); rpc Call(Request) returns(Request); }");
    assertValid("syntax = 'proto3'; message Separate { message Nested {} } "
        + "message Other { message Nested {} }");
  }

  @Test public void should_require_actual_oneof_fields() {
    assertInvalid("syntax = 'proto3'; message EmptyChoice { oneof choice {} }");
    assertInvalid("syntax = 'proto3'; message RepeatedChoice { repeated oneof choice { string x = 1; } }");
    assertInvalid("syntax = 'proto2'; message RangeChoice { oneof choice { extensions 2 to 3; string x = 1; } }");
    assertValid("syntax = 'proto2'; message GroupChoice { oneof choice { group Detail = 1 { optional int32 x = 1; } } }");
  }

  @Test public void should_check_proto3_enum_zero_and_int32_boundaries() {
    assertValid("syntax = 'proto3'; enum SignedValues { ZERO = 0; NEGATIVE = -1; "
        + "MINIMUM = -2147483648; MAXIMUM = 0x7fffffff; }");
    assertValid("syntax = 'proto2'; enum OldValues { FIRST = 7; NEGATIVE = -1; }");
    assertInvalid("syntax = 'proto3'; enum NonzeroFirst { reserved 7; FIRST = 1; ZERO = 0; }");
    assertInvalid("syntax = 'proto2'; enum Overflow { FIRST = 0x80000000; }");
    assertInvalid("syntax = 'proto2'; enum Underflow { FIRST = -2147483649; }");
    assertInvalid("syntax = 'proto3'; enum EmptyEnum { reserved 1; }");
  }

  @Test public void should_require_zero_first_for_proto2_map_value_enums_only() {
    assertValid("syntax = 'proto2'; enum MapZero { ZERO = 0; } "
        + "message GoodMap { map<string, MapZero> values = 1; }");
    assertInvalid("syntax = 'proto2'; enum MapNonzero { FIRST = 1; } "
        + "message BadMap { map<string, MapNonzero> values = 1; }");
  }

  private void assertValid(String source) {
    xtext.parseText(source);
    List<org.eclipse.xtext.validation.Issue> issues = validate();
    assertTrue(issues.toString(), issues.isEmpty());
  }

  private void assertInvalid(String source) {
    xtext.parseText(source);
    List<org.eclipse.xtext.validation.Issue> issues = validate();
    assertTrue(issues.toString(), issues.stream()
        .anyMatch(issue -> issue.getSeverity() == org.eclipse.xtext.diagnostics.Severity.ERROR));
  }

  private List<org.eclipse.xtext.validation.Issue> validate() {
    List<org.eclipse.xtext.validation.Issue> issues = resourceValidator.validate(
        xtext.resource(), org.eclipse.xtext.validation.CheckMode.ALL, null);
    assertTrue(xtext.resource().getErrors().toString(), xtext.resource().getErrors().isEmpty());
    return issues;
  }

  // Create file semantic_closed_domain.proto
  // syntax = "proto2";
  // package semantic.closed;
  // enum Closed { ZERO = 0; ONE = 1; }
  // message Legacy { optional string value = 1; }

  // syntax = "proto3";
  // import "semantic_closed_domain.proto";
  // message ModernUse {
  //   semantic.closed.Legacy legacy = 1;
  //   semantic.closed.Closed status = 2;
  //   map<string, semantic.closed.Closed> statuses = 3;
  // }
  @Test public void should_link_but_reject_closed_enum_field_and_map_value_uses_in_proto3() {
    List<org.eclipse.xtext.validation.Issue> issues = validate();
    assertEquals(2, issues.size());
    for (com.google.eclipse.protobuf.protobuf.ComplexTypeLink link :
        xtext.findAll(com.google.eclipse.protobuf.protobuf.ComplexTypeLink.class)) {
      assertTrue(!link.getTarget().eIsProxy());
    }
  }

  // Create file semantic_open_domain.proto
  // syntax = "proto3";
  // package semantic.open;
  // enum Open { ZERO = 0; ONE = 1; }

  // syntax = "proto2";
  // import "semantic_open_domain.proto";
  // message OldUse { optional semantic.open.Open status = 1; }
  @Test public void should_allow_proto2_fields_to_use_imported_proto3_enums() {
    assertTrue(validate().isEmpty());
  }

  @Test public void should_allow_each_exact_proto3_option_extendee_with_its_declared_range() {
    StringBuilder source = new StringBuilder("syntax = 'proto3'; " + descriptorImport() + "package semantic.options; ");
    String[] names = {"FileOptions", "MessageOptions", "FieldOptions", "EnumOptions", "EnumValueOptions",
        "ServiceOptions", "MethodOptions", "OneofOptions", "ExtensionRangeOptions"};
    for (int i = 0; i < names.length; i++) {
      source.append("extend google.protobuf.").append(names[i])
          .append(" { string note_").append(i).append(" = 50001; } ");
    }
    assertValid(source.toString());
  }

  // Create file semantic_impostor_extendee.proto
  // syntax = "proto2";
  // package semantic.impostor;
  // message FileOptions { extensions 1000 to max; }

  // syntax = "proto3";
  // import "semantic_impostor_extendee.proto";
  // extend semantic.impostor.FileOptions { string note = 50001; }
  @Test public void should_not_authorize_an_option_extendee_by_simple_name() {
    assertEquals(1, validate().size());
  }

  @Test public void should_preserve_messageset_signed_int32_extension_domain() {
    assertValid("syntax = 'proto2'; message MessageSetDomain { "
        + "option message_set_wire_format = true; extensions 1 to max; } message Payload {} "
        + "extend MessageSetDomain { optional Payload item = 2147483646; }");
    assertInvalid("syntax = 'proto2'; message MessageSetOverflow { "
        + "option message_set_wire_format = true; extensions 1 to max; } message Payload {} "
        + "extend MessageSetOverflow { optional Payload item = 2147483647; }");
    assertInvalid("syntax = 'proto2'; message OrdinaryDomain { extensions 1 to max; } "
        + "extend OrdinaryDomain { optional int32 item = 536870912; }");
    assertInvalid("syntax = 'proto2'; message LimitedDomain { extensions 100 to 200; } "
        + "extend LimitedDomain { optional int32 item = 201; }");
  }

  @Test public void should_check_visible_duplicate_extension_numbers_per_resolved_extendee() {
    assertInvalid("syntax = 'proto2'; message ExtendedDomain { extensions 100 to 200; } "
        + "extend ExtendedDomain { optional int32 first = 100; } "
        + "extend ExtendedDomain { optional int32 second = 100; }");
  }

  @Test public void should_reject_proto3_groups_ranges_streams_and_messagesets() {
    assertInvalid("syntax = 'proto3'; message GroupForbidden { group Detail = 1 {} }");
    assertInvalid("syntax = 'proto3'; message RangeForbidden { extensions 100 to 200; }");
    assertInvalid("syntax = 'proto3'; message Request {} service Api { stream Legacy(Request, Request); }");
    assertInvalid("syntax = 'proto3'; message MessageSetForbidden { option message_set_wire_format = true; }");
    assertValid("syntax = 'proto3'; message Request {} service Api { "
        + "rpc Unary(Request) returns(Request); rpc Client(stream Request) returns(Request); "
        + "rpc Server(Request) returns(stream Request); rpc Bidi(stream Request) returns(stream Request); }");
  }

  @Test public void should_not_enable_aliases_from_a_same_named_custom_option() {
    xtext.parseText("syntax = 'proto3'; " + descriptorImport() + "package semantic.customalias; "
        + "extend google.protobuf.EnumOptions { bool allow_alias = 50002; } "
        + "enum CustomAliasing { option (semantic.customalias.allow_alias) = true; ZERO = 0; A = 1; B = 1; }");
    assertEquals(1, validate().size());
  }

  private String descriptorImport() {
    return "import '" + descriptorProvider.primaryDescriptor(xtext.resource()).location.toString() + "'; ";
  }

  // Create file semantic_closed_option.proto
  // syntax = "proto2";
  // package semantic.closedoption;
  // enum Closed { ZERO = 0; ONE = 1; }

  // syntax = "proto3";
  // import "semantic_closed_option.proto";
  @Test public void should_reject_closed_enum_on_new_proto3_custom_option_declarations() {
    xtext.parseText("syntax = 'proto3'; import 'semantic_closed_option.proto'; " + descriptorImport()
        + "extend google.protobuf.FileOptions { semantic.closedoption.Closed status = 50003; }");
    assertEquals(1, validate().size());
    assertTrue(!xtext.findFirst(com.google.eclipse.protobuf.protobuf.ComplexTypeLink.class).getTarget().eIsProxy());
  }

  @Test public void should_allow_proto2_featureset_extensions_but_not_proto3_ordinary_extensions() {
    assertValid("syntax = 'proto2'; " + descriptorImport() + "package pb; "
        + "message JavaFeatures { optional bool flag = 1 [targets=TARGET_TYPE_FILE, "
        + "feature_support={edition_introduced:EDITION_2023}, "
        + "edition_defaults={edition:EDITION_LEGACY value:'false'}]; } "
        + "extend google.protobuf.FeatureSet { optional JavaFeatures java = 1001; }");
    assertInvalid("syntax = 'proto3'; " + descriptorImport() + "package pb; "
        + "message JavaFeatures { bool flag = 1; } "
        + "extend google.protobuf.FeatureSet { JavaFeatures java = 1001; }");
  }

  // Create file semantic_missing_range.proto
  // syntax = "proto2";
  // package proto2;
  // message FileOptions {}

  // syntax = "proto3";
  // import "semantic_missing_range.proto";
  // extend proto2.FileOptions { string absent_range = 50001; }
  @Test public void should_require_an_actual_extension_range_on_named_option_extendees() {
    assertEquals(1, validate().size());
  }

  // Create file semantic_existing_extension.proto
  // syntax = "proto2";
  // package semantic.extensions;
  // message Extendee { extensions 100 to 200; }
  // extend Extendee { optional int32 first = 100; }

  // syntax = "proto2";
  // import "semantic_existing_extension.proto";
  // extend semantic.extensions.Extendee { optional int32 second = 100; }
  @Test public void should_check_duplicate_extension_numbers_in_direct_imports() {
    assertEquals(1, validate().size());
  }

  // Create file semantic_package_symbol.proto
  // syntax = "proto3";
  // package semantic;
  // message packages {}

  // syntax = "proto3";
  // import "semantic_package_symbol.proto";
  // package semantic.packages;
  // message Child {}
  @Test public void should_check_package_namespace_against_visible_source_symbols() {
    assertEquals(1, validate().size());
  }

  @Test public void should_not_reject_maps_nested_inside_a_group_extension_body() {
    assertValid("syntax = 'proto2'; message Container { extensions 100 to 200; } "
        + "extend Container { optional group Detail = 100 { map<string, int32> entries = 1; } }");
  }

  @Test public void should_bound_messageset_range_and_numbering_before_exclusive_int32_end() throws Exception {
    assertValid("syntax = 'proto2'; message LastMessageSetNumber { option message_set_wire_format = true; "
        + "extensions 2147483646 to max; } message Payload {} "
        + "extend LastMessageSetNumber { optional Payload item = 2147483646; }");
    assertEquals(Long.valueOf(2147483646L), indexRanges.toLongRange(xtext.findFirst(IndexRange.class)).upperEndpoint());
    assertEquals(-1L, indexedElements.calculateNewIndexAfter(xtext.findFirst(MessageField.class), 2147483646L));
  }

  @Test public void should_not_count_oneof_options_as_fields() {
    xtext.parseText("syntax = 'proto3'; " + descriptorImport()
        + "extend google.protobuf.OneofOptions { string note = 50004; } "
        + "message OptionOnly { oneof choice { option (note) = 'still empty'; } }");
    assertEquals(1, validate().size());
  }

  // Create file semantic_applied_closed_option.proto
  // syntax = "proto2";
  // package semantic.applied;
  // import "google/protobuf/descriptor.proto";
  // enum Closed { ZERO = 0; ONE = 1; }
  // extend google.protobuf.FileOptions { optional Closed mode = 50005; }

  // syntax = "proto3";
  // import "semantic_applied_closed_option.proto";
  // option (semantic.applied.mode) = ONE;
  @Test public void should_allow_applying_proto2_defined_closed_enum_options_in_proto3() {
    List<org.eclipse.xtext.validation.Issue> issues = validate();
    assertTrue(issues.toString(), issues.isEmpty());
    assertTrue(xtext.findFirst(com.google.eclipse.protobuf.protobuf.LiteralLink.class).getTarget().eContainer()
        instanceof com.google.eclipse.protobuf.protobuf.Enum);
  }

  @Test public void should_treat_groups_as_parent_wire_fields_and_nested_source_types() {
    assertInvalid("syntax = 'proto2'; message GroupWireName { reserved 'detail'; optional group Detail = 1 {} }");
    assertValid("syntax = 'proto2'; message GroupTypeReservation { reserved 'Detail'; optional group Detail = 1 {} }");
    assertInvalid("syntax = 'proto2'; message GroupWireDuplicate { optional group Detail = 1 {} "
        + "optional int32 detail = 2; }");
    assertInvalid("syntax = 'proto2'; message GroupTypeDuplicate { optional group Detail = 1 {} message Detail {} }");
    assertValid("syntax = 'proto2'; message LegacyLowercaseGroup { optional group detail = 1 {} }");
  }
}
