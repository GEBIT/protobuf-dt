package com.google.eclipse.protobuf.validation;

import static com.google.eclipse.protobuf.junit.core.IntegrationTestModule.integrationTestModule;
import static com.google.eclipse.protobuf.junit.core.XtextRule.overrideRuntimeModuleWith;
import static org.junit.Assert.*;

import java.math.BigInteger;
import java.util.List;

import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.eclipse.xtext.validation.Issue;
import org.junit.Rule;
import org.junit.Test;

import com.google.eclipse.protobuf.junit.core.XtextRule;
import com.google.eclipse.protobuf.protobuf.BuiltinLiteral;
import com.google.eclipse.protobuf.protobuf.HexNumberLink;
import com.google.eclipse.protobuf.protobuf.LiteralLink;
import com.google.eclipse.protobuf.protobuf.LongLink;
import com.google.inject.Inject;

/** Resource-wide contracts: parsing, linking and composed semantic checks are exercised together. */
public class DataTypeValidator_Test {
  @Rule public XtextRule xtext = overrideRuntimeModuleWith(integrationTestModule());
  @Inject private IResourceValidator validator;

  private static final String OPTIONS =
      "syntax = \"proto2\"; import \"google/protobuf/descriptor.proto\"; "
      + "import \"" + new java.io.File("resources/protobuf-java-4.36.2/google/protobuf/any.proto").toURI() + "\"; "
      + "enum Keyword { true=0; false=1; inf=2; nan=3; } "
      + "message Child { optional int32 id=1; } "
      + "message Settings { optional bool enabled=1; optional double weight=2; repeated uint64 nums=3; "
      + "repeated Child children=4; map<string, Child> entries=5; optional Keyword mode=6; "
      + "optional string text=7; optional bytes raw=8; oneof choice { int32 left=9; string right=10; } "
      + "optional google.protobuf.Any any=11; optional Child child=12; } "
      + "extend google.protobuf.FileOptions { optional Settings config=50001; optional uint64 large=50002; "
      + "optional bool flag=50003; optional Keyword keyword=50004; optional double real=50005; "
      + "optional int32 signed=50006; optional string string_option=50007; optional bytes bytes_option=50008; } ";

  private List<Issue> validate(String source) {
    xtext.parseText(source);
    return validator.validate(xtext.resource(), CheckMode.ALL, null);
  }

  private void accepted(String suffix) {
    List<Issue> issues = validate(OPTIONS + suffix);
    assertTrue(issues.toString(), issues.isEmpty());
    assertTrue(xtext.resource().getErrors().toString(), xtext.resource().getErrors().isEmpty());
  }

  private void rejected(String suffix, String code) {
    List<Issue> issues = validate(OPTIONS + suffix);
    for (Issue issue : issues) {
      if (issue.getSeverity() == Severity.ERROR && (code == null || code.equals(issue.getCode()))
          && issue.getOffset() != null && issue.getOffset() >= OPTIONS.length()) return;
    }
    fail("Expected an error" + (code == null ? "" : " with code " + code) + ", got " + issues);
  }

  @Test public void should_preserve_unsigned64_boundaries_in_both_integer_models() {
    accepted("option (large)=18446744073709551615; option (config)={ nums:[0xffffffffffffffff] }; ");
    assertEquals(new BigInteger("18446744073709551615"), xtext.findAll(LongLink.class).get(0).getTarget());
    assertEquals(new BigInteger("18446744073709551615"), xtext.findAll(HexNumberLink.class).get(0).getTarget());
  }

  @Test public void should_reject_negative_unsigned_values() {
    rejected("option (large)=-1;", DataTypeValidator.OPTION_TYPE_ERROR);
  }

  @Test public void should_reject_unsigned64_overflow_without_wrapping() {
    rejected("option (large)=18446744073709551616;", DataTypeValidator.OPTION_TYPE_ERROR);
  }

  @Test public void should_reject_signed32_overflow() {
    rejected("option (signed)=2147483648;", DataTypeValidator.OPTION_TYPE_ERROR);
  }

  @Test public void should_accept_signed32_boundary_and_separated_sign() {
    accepted("option (signed)=- /* sign */ 2147483648;");
  }

  @Test public void should_accept_typed_aggregate_lists_map_roles_and_textformat_aliases() {
    accepted("option (config)={enabled:t weight:-Infinity nums:[0,18446744073709551615] "
        + "children:[] entries:[{key:'a' value:{id:1}},{key:'b' value:{id:2}}]};");
  }

  @Test public void should_accept_empty_scalar_and_message_lists_without_message_colon() {
    accepted("option (config)={nums:[] children[]};");
  }

  @Test public void should_keep_real_keyword_enum_values_distinct_from_builtins() {
    accepted("option (keyword)=true; option (flag)=true; option (config)={mode:inf enabled:False weight:nan};");
    List<LiteralLink> links = xtext.findAll(LiteralLink.class);
    assertFalse(links.get(0).getTarget() instanceof BuiltinLiteral);
    assertTrue(links.get(1).getTarget() instanceof BuiltinLiteral);
    assertFalse(links.get(2).getTarget() instanceof BuiltinLiteral);
  }

  @Test public void should_reject_ordinary_boolean_aliases() {
    rejected("option (flag)=t;", null);
  }

  @Test public void should_reject_signed_boolean_identifiers() {
    rejected("option (flag)=-true;", DataTypeValidator.EXPECTED_BOOL_ERROR);
  }

  @Test public void should_reject_numeric_boolean_outside_aggregate() {
    rejected("option (flag)=1;", DataTypeValidator.EXPECTED_BOOL_ERROR);
  }

  @Test public void should_accept_numeric_boolean_in_aggregate() {
    accepted("option (config)={enabled:1};");
  }

  @Test public void should_reject_aggregate_boolean_other_than_zero_or_one() {
    rejected("option (config)={enabled:2};", DataTypeValidator.EXPECTED_BOOL_ERROR);
  }

  @Test public void should_accept_ordinary_signed_special_floats() {
    accepted("option (real)=-inf; option (config)={weight:-NaN};");
  }

  @Test public void should_reject_ordinary_infinity_alias() {
    rejected("option (real)=infinity;", null);
  }

  @Test public void should_accept_aggregate_float_suffix() {
    accepted("option (config)={weight:1.5f};");
  }

  @Test public void should_reject_ordinary_float_suffix() {
    rejected("option (real)=1.5f;", DataTypeValidator.OPTION_TYPE_ERROR);
  }

  @Test public void should_accept_non_utf8_bytes() {
    accepted("option (bytes_option)='\\377'; option (config)={raw:'\\xff'};");
  }

  @Test public void should_reject_non_utf8_strings() {
    rejected("option (string_option)='\\377';", "invalidStringLiteral");
  }

  @Test public void should_reject_message_scalar_mismatch() {
    rejected("option (config)=1;", DataTypeValidator.OPTION_TYPE_ERROR);
  }

  @Test public void should_reject_scalar_message_mismatch() {
    rejected("option (config)={enabled:{}};", DataTypeValidator.OPTION_TYPE_ERROR);
  }

  @Test public void should_require_scalar_colon() {
    rejected("option (config)={enabled true};", DataTypeValidator.OPTION_TYPE_ERROR);
  }

  @Test public void should_reject_list_on_singular_message_even_when_empty() {
    rejected("option (config)={child:[]};", DataTypeValidator.OPTION_TYPE_ERROR);
  }

  @Test public void should_reject_duplicate_singular_aggregate_member() {
    rejected("option (config)={enabled:true enabled:false};", DataTypeValidator.OPTION_ASSIGNMENT_ERROR);
  }

  @Test public void should_reject_conflicting_oneof_members() {
    rejected("option (config)={left:1 right:'conflict'};", DataTypeValidator.OPTION_ASSIGNMENT_ERROR);
  }

  @Test public void should_reject_conflicting_oneof_members_across_dotted_statements() {
    rejected("option (config).left=1; option (config).right='conflict';", DataTypeValidator.OPTION_ASSIGNMENT_ERROR);
  }

  @Test public void should_merge_distinct_dotted_paths_but_reject_duplicate_terminal_members() {
    accepted("option (config).child.id=1; option (config).enabled=true;");
    rejected("option (config)={child:{id:1}}; option (config).child.id=2;", DataTypeValidator.OPTION_ASSIGNMENT_ERROR);
  }

  @Test public void should_reject_repeated_message_dotted_traversal() {
    rejected("option (config).children.id=1;", DataTypeValidator.OPTION_CONTEXT_ERROR);
  }

  @Test public void should_accept_expanded_any_with_arbitrary_url_prefix() {
    accepted("option (config)={any:{[example.test/custom/Child]{id:7}}};");
  }

  @Test public void should_reject_expanded_any_on_other_message_types() {
    rejected("option (config)={child:{[example.test/Child]{id:7}}};", DataTypeValidator.OPTION_CONTEXT_ERROR);
  }

  @Test public void should_reject_expanded_any_and_explicit_payload() {
    rejected("option (config)={any:{[example.test/Child]{id:7} value:'x'}};", DataTypeValidator.OPTION_ASSIGNMENT_ERROR);
  }

  @Test public void should_accept_repeated_native_metadata_as_distinct_elements() {
    accepted("message Feature { optional bool x=1 [retention=RETENTION_RUNTIME, "
        + "targets=TARGET_TYPE_FILE, targets=TARGET_TYPE_FIELD, "
        + "edition_defaults={edition:EDITION_LEGACY value:'true'}, "
        + "edition_defaults={edition:EDITION_2023 value:'false'}, "
        + "feature_support={edition_introduced:EDITION_2023 edition_removed:EDITION_2023}]; }");
  }

  @Test public void should_accept_feature_support_removal_without_introduction_on_ordinary_options() {
    accepted("message Feature { optional bool x=1 [feature_support={edition_removed:EDITION_2024}]; }");
  }

  @Test public void should_reject_duplicate_native_singular_metadata() {
    rejected("message Feature { optional bool x=1 [retention=RETENTION_RUNTIME,retention=RETENTION_SOURCE]; }",
        DataTypeValidator.OPTION_ASSIGNMENT_ERROR);
  }

  @Test public void should_reject_duplicate_feature_support_even_with_disjoint_members() {
    rejected("message Feature { optional bool x=1 [feature_support={edition_introduced:EDITION_2023},"
        + "feature_support={edition_removed:EDITION_2024}]; }", DataTypeValidator.OPTION_ASSIGNMENT_ERROR);
  }

  @Test public void should_require_edition_default_values_to_be_strings() {
    rejected("message Feature { optional bool x=1 [edition_defaults={edition:EDITION_LEGACY value:true}]; }",
        DataTypeValidator.EXPECTED_STRING_ERROR);
  }

  @Test public void should_reject_wrong_native_option_context() {
    rejected("enum E { E0=0 [retention=RETENTION_RUNTIME]; }", null);
  }

  @Test public void should_reject_wrong_metadata_enum_member() {
    rejected("message Feature { optional bool x=1 [targets=RETENTION_RUNTIME]; }", null);
  }

  @Test public void should_reject_pseudo_options_on_enum_values() {
    rejected("enum E { E0=0 [json_name='x']; }", DataTypeValidator.OPTION_CONTEXT_ERROR);
    rejected("enum E { E0=0 [default=0]; }", DataTypeValidator.OPTION_CONTEXT_ERROR);
  }

  @Test public void should_reject_default_on_repeated_fields_and_messages() {
    rejected("message M { repeated int32 x=1 [default=0]; }", DataTypeValidator.OPTION_CONTEXT_ERROR);
    rejected("message M { optional Child x=1 [default={}]; }", DataTypeValidator.OPTION_CONTEXT_ERROR);
  }

  @Test public void should_reject_source_features_and_manual_map_entry() {
    rejected("message M { option features.field_presence=EXPLICIT; }", DataTypeValidator.OPTION_CONTEXT_ERROR);
    rejected("message M { option map_entry=true; }", DataTypeValidator.OPTION_CONTEXT_ERROR);
  }

  @Test public void should_not_reject_ordinary_fields_named_features() {
    accepted("message Ordinary { optional bool features=1; } "
        + "extend google.protobuf.MessageOptions { optional Ordinary ordinary=50100; } "
        + "message M { option (ordinary)={features:true}; }");
  }

  @Test public void should_check_packed_lazy_and_jstype_applicability() {
    accepted("message M { repeated int32 x=1 [packed=true]; optional Child y=2 [lazy=true]; "
        + "optional int64 z=3 [jstype=JS_STRING]; }");
    rejected("message M { optional int32 x=1 [packed=true]; }", DataTypeValidator.OPTION_CONTEXT_ERROR);
    rejected("message M { optional int32 x=1 [lazy=true]; }", DataTypeValidator.OPTION_CONTEXT_ERROR);
    rejected("message M { optional int32 x=1 [jstype=JS_STRING]; }", DataTypeValidator.OPTION_CONTEXT_ERROR);
  }

  @Test public void should_enforce_custom_option_target_metadata_when_used_not_when_declared() {
    accepted("extend google.protobuf.MessageOptions { optional string restricted=50100 [targets=TARGET_TYPE_MESSAGE]; } "
        + "message M { option (restricted)='ok'; }");
    rejected("extend google.protobuf.MessageOptions { optional string restricted=50100 [targets=TARGET_TYPE_FIELD]; } "
        + "message M { option (restricted)='wrong target'; }", DataTypeValidator.OPTION_CONTEXT_ERROR);
  }

  @Test public void should_accept_extension_declaration_strings_without_implementation_imports() {
    accepted("message M { extensions 536000000 [declaration={number:536000000 "
        + "full_name:'.missing.implementation' type:'.missing.Payload'}]; }");
  }

  @Test public void should_reject_declaration_number_outside_range() {
    rejected("message M { extensions 100 to 199 [declaration={number:200 full_name:'.x.y' type:'string'}]; }",
        DataTypeValidator.EXTENSION_DECLARATION_ERROR);
  }

  @Test public void should_check_every_range_of_an_annotated_multi_range_statement() {
    rejected("message M { extensions 100,200 [declaration={number:100 full_name:'.x.y' type:'string'}]; }",
        DataTypeValidator.EXTENSION_DECLARATION_ERROR);
  }

  @Test public void should_accept_reserved_declaration_without_name_or_type() {
    accepted("message M { extensions 100 [declaration={number:100 reserved:true}]; }");
  }

  @Test public void should_accept_reserved_declaration_with_both_name_and_type() {
    // v36.2 ValidateExtensionDeclaration allows both names or neither for a reserved declaration.
    accepted("message M { extensions 100 [declaration={number:100 reserved:true "
        + "full_name:'.removed.extension' type:'string'}]; }");
  }

  @Test public void should_reject_using_a_named_reserved_extension_declaration() {
    rejected("message M { extensions 100 [declaration={number:100 reserved:true "
        + "full_name:'.actual' type:'string'}]; } extend M { optional string actual=100; }",
        DataTypeValidator.EXTENSION_DECLARATION_ERROR);
  }

  @Test public void should_reject_half_specified_reserved_declaration() {
    rejected("message M { extensions 100 [declaration={number:100 reserved:true full_name:'.x.y'}]; }",
        DataTypeValidator.EXTENSION_DECLARATION_ERROR);
    rejected("message M { extensions 100 [declaration={number:100 reserved:true type:'string'}]; }",
        DataTypeValidator.EXTENSION_DECLARATION_ERROR);
  }

  @Test public void should_reject_unqualified_declaration_names_and_types() {
    rejected("message M { extensions 100 [declaration={number:100 full_name:'x.y' type:'Payload'}]; }",
        DataTypeValidator.EXTENSION_DECLARATION_ERROR);
  }

  @Test public void should_reject_duplicate_declaration_numbers_and_names_in_owner() {
    rejected("message M { extensions 100 [declaration={number:100 full_name:'.x.y' type:'string'},"
        + "declaration={number:100 full_name:'.x.z' type:'string'}]; }", DataTypeValidator.EXTENSION_DECLARATION_ERROR);
    rejected("message M { extensions 100 [declaration={number:100 full_name:'.x.y' type:'string'}]; "
        + "extensions 200 [declaration={number:200 full_name:'.x.y' type:'string'}]; }",
        DataTypeValidator.EXTENSION_DECLARATION_ERROR);
  }

  @Test public void should_validate_available_actual_extensions_against_declarations() {
    accepted("message M { extensions 100 [declaration={number:100 full_name:'.actual' type:'string'}]; } "
        + "extend M { optional string actual=100; }");
    rejected("message M { extensions 100 [declaration={number:100 full_name:'.actual' type:'string'}]; } "
        + "extend M { optional int32 actual=100; }", DataTypeValidator.EXTENSION_DECLARATION_ERROR);
  }

  @Test public void should_reject_missing_extension_in_declaration_verification_mode() {
    rejected("message M { extensions 100 [verification=DECLARATION]; } extend M { optional string actual=100; }",
        DataTypeValidator.EXTENSION_DECLARATION_ERROR);
  }

  @Test public void should_reject_unverified_ranges_with_declarations() {
    rejected("message M { extensions 100 [verification=UNVERIFIED,declaration={number:100 reserved:true}]; }",
        DataTypeValidator.EXTENSION_DECLARATION_ERROR);
  }

  @Test public void should_reject_json_name_nul_extension_and_collisions() {
    rejected("message M { optional int32 x=1 [json_name='a\\000b']; }", DataTypeValidator.JSON_NAME_ERROR);
    rejected("extend google.protobuf.MessageOptions { optional int32 x=50100 [json_name='other']; }",
        DataTypeValidator.JSON_NAME_ERROR);
    rejected("message M { optional int32 x=1 [json_name='shared']; optional int32 y=2 [json_name='shared']; }",
        DataTypeValidator.JSON_NAME_ERROR);
  }

  @Test public void should_check_default_json_names_even_when_overrides_are_distinct() {
    List<Issue> issues = validate("syntax='proto3'; message M { int32 foo_bar=1 [json_name='first']; "
        + "int32 fooBar=2 [json_name='second']; }");
    assertTrue(issues.toString(), hasCode(issues, DataTypeValidator.JSON_NAME_ERROR));
  }

  @Test public void should_honor_native_legacy_json_conflict_option() {
    accepted("message M { option deprecated_legacy_json_field_conflicts=true; "
        + "optional int32 foo_bar=1; optional int32 fooBar=2; }");
  }

  @Test public void should_accept_exact_modern_descriptor_and_java_feature_source_texts() throws Exception {
    for (String member : new String[] {"descriptor.proto", "java_features.proto"}) {
      String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
          "resources/protobuf-java-4.36.2/google/protobuf", member)), java.nio.charset.StandardCharsets.UTF_8);
      List<Issue> issues = validate(source);
      assertTrue(member + ": " + issues, issues.isEmpty());
      assertTrue(xtext.resource().getErrors().toString(), xtext.resource().getErrors().isEmpty());
    }
  }

  @Test public void should_reject_expanded_any_conflicting_with_later_dotted_payload_assignment() {
    rejected("option (config).any={[example.test/Child]{id:7}}; option (config).any.value='x';",
        DataTypeValidator.OPTION_ASSIGNMENT_ERROR);
  }

  @Test public void should_resolve_real_extension_members_in_dotted_option_paths() {
    accepted("message Extra { extensions 100 to 199; } extend Extra { optional Child detail=100; } "
        + "extend google.protobuf.FileOptions { optional Extra extra=50100; } "
        + "option (extra).(detail).id=7;");
    com.google.eclipse.protobuf.protobuf.IndexedElement linked =
        xtext.findAll(com.google.eclipse.protobuf.protobuf.ExtensionOptionField.class).get(0).getTarget();
    org.eclipse.emf.ecore.EObject declared = null;
    for (com.google.eclipse.protobuf.protobuf.MessageField field :
        xtext.findAll(com.google.eclipse.protobuf.protobuf.MessageField.class)) {
      if ("detail".equals(field.getName())) declared = field;
    }
    assertSame(declared, linked);
  }

  @Test public void should_reject_dotted_extension_member_of_wrong_extendee() {
    rejected("message Extra { extensions 100 to 199; } message Other { extensions 100 to 199; } "
        + "extend Other { optional Child wrong=100; } "
        + "extend google.protobuf.FileOptions { optional Extra extra=50100; } "
        + "option (extra).(wrong).id=7;", null);
  }

  @Test public void should_require_parentheses_for_dotted_extension_members() {
    rejected("message Extra { extensions 100 to 199; } extend Extra { optional Child detail=100; } "
        + "extend google.protobuf.FileOptions { optional Extra extra=50100; } "
        + "option (extra).detail.id=7;", null);
  }

  @Test public void should_preserve_native_and_custom_group_field_and_body_option_contexts() {
    accepted("extend google.protobuf.FieldOptions { optional string group_note=50100; } "
        + "extend google.protobuf.MessageOptions { optional string body_note=50101; } "
        + "message M { optional group G=1 [deprecated=true,json_name='group', (group_note)='field'] { "
        + "option deprecated=true; option (body_note)='type'; optional int32 x=1; } }");
  }

  @Test public void should_accept_all_integer_type_boundaries_and_reject_width_overflow() {
    accepted("extend google.protobuf.FileOptions { optional int64 i64=50100; optional uint32 u32=50101; } "
        + "option (i64)=-9223372036854775808; option (u32)=4294967295;");
    rejected("extend google.protobuf.FileOptions { optional int64 i64=50100; } "
        + "option (i64)=9223372036854775808;", DataTypeValidator.OPTION_TYPE_ERROR);
    rejected("extend google.protobuf.FileOptions { optional uint32 u32=50100; } "
        + "option (u32)=4294967296;", DataTypeValidator.OPTION_TYPE_ERROR);
  }

  @Test public void should_reject_qualified_ordinary_enum_values_and_outer_option_trailing_comma() {
    for (String invalid : new String[] {"option (keyword)=Keyword.true;",
        "message M { optional bool field=1 [retention=RETENTION_RUNTIME,]; }"}) {
      List<Issue> issues = validate("// ignore errors\n" + OPTIONS + invalid);
      boolean parseError = false;
      for (Issue issue : issues) parseError |= issue.isSyntaxError();
      assertTrue(issues.toString(), parseError);
    }
  }

  @Test public void should_accept_source_and_runtime_retention_at_roots_and_nested_option_fields() {
    accepted("message Retained { optional string runtime_value=1 [retention=RETENTION_RUNTIME]; "
        + "optional string source_value=2 [retention=RETENTION_SOURCE]; } "
        + "extend google.protobuf.FileOptions { "
        + "optional Retained runtime_option=50100 [retention=RETENTION_RUNTIME]; "
        + "optional Retained source_option=50101 [retention=RETENTION_SOURCE]; } "
        + "option (runtime_option)={runtime_value:'r' source_value:'s'}; "
        + "option (source_option)={runtime_value:'r' source_value:'s'};");
  }

  @Test public void should_enforce_targets_even_on_source_retained_nested_option_fields() {
    rejected("message Retained { optional string source_value=1 "
        + "[retention=RETENTION_SOURCE,targets=TARGET_TYPE_MESSAGE]; } "
        + "extend google.protobuf.FileOptions { optional Retained retained=50100 [retention=RETENTION_SOURCE]; } "
        + "option (retained)={source_value:'wrong target'};", DataTypeValidator.OPTION_CONTEXT_ERROR);
  }

  @Test public void should_reject_wrong_retention_enum_and_context() {
    rejected("message M { optional bool value=1 [retention=TARGET_TYPE_FILE]; }", null);
    rejected("enum E { E0=0 [retention=RETENTION_SOURCE]; }", null);
  }

  @Test public void should_check_json_collisions_between_direct_group_fields() {
    rejected("message M { optional group G=1 { optional string first=1 [json_name='same']; "
        + "optional string second=2 [json_name='same']; } }", DataTypeValidator.JSON_NAME_ERROR);
  }

  @Test public void should_keep_nested_message_and_group_json_names_in_their_own_scopes() {
    accepted("message M { optional group G=1 { optional string direct=1 [json_name='same']; "
        + "message Nested { optional string nested=1 [json_name='same']; } "
        + "optional group Inner=3 { optional string inner=1 [json_name='same']; } } }");
  }

  private boolean hasCode(List<Issue> issues, String code) {
    for (Issue issue : issues) if (code.equals(issue.getCode())) return true;
    return false;
  }
}
