package com.google.eclipse.protobuf.validation;

import static com.google.eclipse.protobuf.junit.core.IntegrationTestModule.integrationTestModule;
import static com.google.eclipse.protobuf.junit.core.XtextRule.overrideRuntimeModuleWith;
import static org.junit.Assert.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.diagnostics.Diagnostic;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.ui.editor.preferences.IPreferenceStoreAccess;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.util.StringInputStream;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.eclipse.xtext.validation.Issue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.eclipse.protobuf.junit.core.XtextRule;
import com.google.eclipse.protobuf.model.util.Literals;
import com.google.eclipse.protobuf.model.util.MessageFields;
import com.google.eclipse.protobuf.model.util.Syntaxes;
import com.google.eclipse.protobuf.preferences.general.PreferenceNames;
import com.google.eclipse.protobuf.protobuf.MessageField;
import com.google.eclipse.protobuf.protobuf.ModifierEnum;
import com.google.eclipse.protobuf.protobuf.ReservedName;
import com.google.eclipse.protobuf.scoping.ProtoDescriptorProvider;
import com.google.inject.Inject;

/** Real parser/linker/validator contracts; no mocked feature declarations or scopes. */
public class EditionValidator_Test {
  @Rule public XtextRule xtext = overrideRuntimeModuleWith(integrationTestModule());
  @Rule public TemporaryFolder temporary = new TemporaryFolder();
  @Inject private IResourceValidator validator;
  @Inject private MessageFields fields;
  @Inject private Literals literals;
  @Inject private Syntaxes syntaxes;
  @Inject private IPreferenceStoreAccess preferences;
  @Inject private ProtoDescriptorProvider descriptors;

  private static final String JAVA_IMPORT = "import '" + new File(
      "resources/protobuf-java-4.36.2/google/protobuf/java_features.proto").toURI() + "'; ";

  @Test public void should_accept_both_years_and_preserve_unlabeled_model_fields() {
    for (String year : new String[] {"2023", "2024"}) {
      assertClean(edition(year, "message M { int32 value = 1; }"));
      assertEquals(ModifierEnum.UNSPECIFIED, named("value", MessageField.class).getModifier());
      assertTrue(syntaxes.isEditions(xtext.root()));
      assertFalse(syntaxes.isProto2(xtext.root()));
      assertFalse(syntaxes.isProto3(xtext.root()));
      assertTrue(fields.supportsExplicitDefault(named("value", MessageField.class)));
    }
    assertClean("edition = '20' \"24\"; message M { int32 value = 1; }");
  }

  @Test public void should_reject_only_unsupported_nonempty_years_at_the_header() {
    for (String year : new String[] {"2025", "2026", "proto2"}) {
      List<Issue> issues = validate(edition(year, "message M { int32 value = 1; }"));
      assertCount(issues, EditionValidator.UNSUPPORTED_EDITION, Severity.ERROR, 1);
      assertOn(issues, EditionValidator.UNSUPPORTED_EDITION, xtext.root().getEdition());
      assertNoCode(issues, ProtobufJavaValidator.SYNTAX_IS_NOT_KNOWN_ERROR);
      assertNoCode(issues, ProtobufJavaValidator.MISSING_MODIFIER_ERROR);
      assertFalse(syntaxes.isProto2(xtext.root()));
      assertFalse(syntaxes.isProto3(xtext.root()));
      assertFalse(syntaxes.isEditions(xtext.root()));
    }
  }

  @Test public void should_keep_invalid_explicit_headers_instead_of_falling_back_to_proto2() {
    for (String source : new String[] {
        "edition = ''; message M { int32 value = 1; }",
        "edition = ; message M { int32 value = 1; }",
        "edition = '2024;\nmessage M { int32 value = 1; }"}) {
      List<Issue> issues = validate("// ignore errors\n" + source);
      assertHasError(issues);
      assertNotNull(xtext.root().getEdition());
      assertFalse(syntaxes.isProto2(xtext.root()));
      assertNoCode(issues, ProtobufJavaValidator.SYNTAX_IS_NOT_KNOWN_ERROR);
      assertNoCode(issues, ProtobufJavaValidator.MISSING_MODIFIER_ERROR);
    }
  }

  @Test public void should_preserve_a_bare_edition_header_without_diagnosing_a_missing_year_as_unsupported() {
    List<Issue> issues = validate("// ignore errors\nedition");
    assertHasError(issues);
    assertNotNull(xtext.root().getEdition());
    assertFalse(syntaxes.isProto2(xtext.root()));
    assertFalse(syntaxes.isProto3(xtext.root()));
    assertNoCode(issues, EditionValidator.UNSUPPORTED_EDITION);
    assertNoCode(issues, ProtobufJavaValidator.SYNTAX_IS_NOT_KNOWN_ERROR);
  }

  @Test public void should_recover_duplicate_mixed_and_late_headers_with_parser_errors() {
    for (String source : new String[] {
        "edition='2023'; edition='2024'; message M { int32 value=1; }",
        "edition='2024'; syntax='proto3'; message M { int32 value=1; }",
        "syntax='proto3'; edition='2024'; message M { int32 value=1; }",
        "message M { optional int32 value=1; } edition='2024';"}) {
      List<Issue> issues = validate("// ignore errors\n" + source);
      boolean syntaxError = false;
      for (Issue issue : issues) syntaxError |= issue.isSyntaxError();
      assertTrue(issues.toString(), syntaxError);
      assertNotNull(named("M", com.google.eclipse.protobuf.protobuf.Message.class));
      for (Issue issue : issues) assertNotEquals(issue.toString(), Severity.WARNING, issue.getSeverity());
    }
  }

  @Test public void should_reject_missing_native_values_without_recovering_them_as_fields() {
    for (String dialect : new String[] {"2023", "2024", "proto2", "proto3"}) {
      String header = dialect.startsWith("proto") ? "syntax='" + dialect + "'; " : "edition='" + dialect + "'; ";
      String source = header + "message M { option deprecated=; "
          + ("proto2".equals(dialect) ? "optional " : "") + "int32 value=1; }";
      List<Issue> issues = validate("// ignore errors\n" + source);
      assertCode(issues, Diagnostic.SYNTAX_DIAGNOSTIC, Severity.ERROR);
      assertNoCode(issues, ProtobufJavaValidator.MISSING_MODIFIER_ERROR);
      assertClean(source.replace("deprecated=;", "deprecated=true;"));
    }
  }

  @Test public void should_preserve_syntaxless_proto2_and_proto3_optional_contracts() {
    List<Issue> issues = validate("message M { message Nested { int32 value=1; } }");
    assertCode(issues, ProtobufJavaValidator.MISSING_MODIFIER_ERROR, Severity.ERROR);
    assertNoCode(issues, EditionValidator.EDITION_FIELD_MODIFIER);
    assertClean("syntax='proto3'; message M { optional int32 value=1; }");
    issues = validate("syntax='proto3'; message M { optional int32 value=1 [default=7]; }");
    assertCode(issues, ProtobufJavaValidator.PROTO3_DEFAULT_ERROR, Severity.ERROR);
    assertNoCode(issues, EditionValidator.EDITION_DEFAULT);
  }

  @Test public void should_accept_repetition_maps_oneofs_extensions_messageset_and_rpc_streaming() {
    for (String year : new String[] {"2023", "2024"}) {
      assertClean(edition(year, "message M { repeated int32 values=1; map<string,int32> entries=2; "
          + "oneof choice { int32 member=3; string text=4; } extensions 100 to 199; } "
          + "extend M { int32 extension_value=100; } "
          + "message MessageSet { option message_set_wire_format=true; extensions 4 to max; } "
          + "message Payload {} extend MessageSet { Payload payload=100; } "
          + "service Echo { rpc Call(stream M) returns (stream M); }"));
    }
  }

  @Test public void should_reject_legacy_labels_without_duplicating_map_or_oneof_label_errors() {
    for (String year : new String[] {"2023", "2024"}) {
      List<Issue> issues = validate(edition(year, "message M { optional int32 optional_value=1; "
          + "required int32 required_value=2; optional map<string,int32> entries=3; "
          + "oneof choice { optional int32 member=4; } extensions 100 to 199; } "
          + "extend M { optional int32 extension_value=100; }"));
      assertCount(issues, EditionValidator.EDITION_FIELD_MODIFIER, Severity.ERROR, 3);
      assertCount(issues, ProtobufJavaValidator.MAP_WITH_MODIFIER_ERROR, Severity.ERROR, 1);
      assertCount(issues, ProtobufJavaValidator.ONEOF_FIELD_WITH_MODIFIER_ERROR, Severity.ERROR, 1);
      assertNoCode(issues, ProtobufJavaValidator.MISSING_MODIFIER_ERROR);
    }
  }

  @Test public void should_reject_groups_and_standalone_streams_but_not_rpc_streaming() {
    for (String year : new String[] {"2023", "2024"}) {
      List<Issue> issues = validate(edition(year, "message M { group Body=1 { int32 value=1; } } "
          + "service Echo { stream Old(M,M); rpc Call(stream M) returns (stream M); }"));
      assertCount(issues, EditionValidator.EDITION_LEGACY_SYNTAX, Severity.ERROR, 2);
    }
  }

  @Test public void should_validate_reserved_name_spelling_by_dialect() {
    assertClean(edition("2024", "message M { reserved old_value; int32 value=1; } "
        + "enum E { reserved OLD_VALUE; ZERO=0; }"));
    for (String header : new String[] {"syntax='proto2';", "syntax='proto3';"}) {
      List<Issue> issues = validate(header + " message M { reserved old_value; } "
          + "enum E { reserved OLD_VALUE; ZERO=0; }");
      assertCount(issues, EditionValidator.EDITION_RESERVED_NAME, Severity.ERROR, 2);
    }
    List<Issue> issues = validate(edition("2023", "message M { reserved 'old_value'; } "
        + "enum E { reserved 'OLD_VALUE'; ZERO=0; }"));
    assertCount(issues, EditionValidator.EDITION_RESERVED_NAME, Severity.ERROR, 2);
  }

  @Test public void should_report_duplicate_identifier_reservations_and_live_name_conflicts() {
    List<Issue> issues = validate(edition("2023", "message M { reserved old_value, old_value; "
        + "int32 old_value=1; } enum E { reserved OLD_VALUE, OLD_VALUE; ZERO=0; OLD_VALUE=1; }"));
    int duplicates = 0;
    for (ReservedName reserved : EcoreUtil2.getAllContentsOfType(xtext.root(), ReservedName.class)) {
      if (hasErrorOn(issues, reserved)) duplicates++;
    }
    assertEquals(issues.toString(), 2, duplicates);
    assertTrue(issues.toString(), hasErrorOn(issues, named("old_value", MessageField.class)));
    assertTrue(issues.toString(), hasErrorOn(issues,
        named("OLD_VALUE", com.google.eclipse.protobuf.protobuf.Literal.class)));
  }

  @Test public void should_reject_mixed_numeric_and_identifier_reservations_in_both_contexts() {
    List<Issue> issues = validate(edition("2023", "message M { reserved 7, old_value; } "
        + "enum E { reserved 7, OLD_VALUE; ZERO=0; }"));
    int mixed = 0;
    for (Issue issue : issues) if (Messages.reservedIndexAndName.equals(issue.getMessage())) mixed++;
    assertEquals(issues.toString(), 2, mixed);
    assertNoCode(issues, EditionValidator.EDITION_RESERVED_NAME);
  }

  @Test public void should_allow_explicit_defaults_and_merge_dotted_and_aggregate_presence_overrides() {
    for (String fileAssignment : new String[] {"option features.field_presence=IMPLICIT;",
        "option features={field_presence:IMPLICIT};", "option features=<field_presence:IMPLICIT>;"}) {
      List<Issue> issues = validate(edition("2023", fileAssignment + " message M { "
          + "int32 inherited=1 [default=7]; int32 dotted=2 [features.field_presence=EXPLICIT,default=7]; "
          + "int32 aggregate=3 [features={field_presence:EXPLICIT},default=7]; "
          + "message Nested { int32 nested=1 [default=7]; } "
          + "message Pinned { int32 pinned=1 [features.field_presence=EXPLICIT,default=7]; } } "
          + "message Sibling { int32 sibling=1 [default=7]; }"));
      assertCount(issues, EditionValidator.EDITION_DEFAULT, Severity.ERROR, 3);
      for (String name : new String[] {"inherited", "nested", "sibling"}) {
        assertFalse(fields.supportsExplicitDefault(named(name, MessageField.class)));
        assertOn(issues, EditionValidator.EDITION_DEFAULT, named(name, MessageField.class));
      }
      for (String name : new String[] {"dotted", "aggregate", "pinned"}) {
        assertTrue(fields.supportsExplicitDefault(named(name, MessageField.class)));
        assertFalse(issues.toString(), hasErrorOn(issues, named(name, MessageField.class)));
      }
    }
  }

  @Test public void should_keep_intrinsic_presence_and_forbid_non_scalar_or_non_singular_defaults() {
    assertClean(edition("2023", "option features.field_presence=IMPLICIT; enum E { ZERO=0; } "
        + "message M { M child=1; oneof choice { int32 member=2; } extensions 100 to 199; } "
        + "extend M { int32 extension_value=100 [default=7]; }"));
    List<Issue> issues = validate(edition("2023", "enum E { ZERO=0; } message M { "
        + "int32 required_value=1 [features.field_presence=LEGACY_REQUIRED,default=7]; "
        + "E enumeration=2 [default=ZERO]; repeated int32 values=3 [default=7]; "
        + "M child=4 [default=7]; map<string,int32> entries=5 [default=7]; "
        + "oneof choice { int32 member=6 [default=7]; } }"));
    assertCount(issues, EditionValidator.EDITION_DEFAULT, Severity.ERROR, 4);
    assertTrue(fields.supportsExplicitDefault(named("required_value", MessageField.class)));
    assertTrue(fields.supportsExplicitDefault(named("enumeration", MessageField.class)));
  }

  @Test public void should_retain_duplicate_leaf_detection_across_option_spellings() {
    List<Issue> issues = validate(edition("2023", "message M { int32 value=1 "
        + "[features.field_presence=EXPLICIT,features={field_presence:IMPLICIT}]; }"));
    assertCode(issues, DataTypeValidator.OPTION_ASSIGNMENT_ERROR, Severity.ERROR);
  }

  @Test public void should_require_zero_only_for_effectively_open_enums() {
    for (String year : new String[] {"2023", "2024"}) {
      List<Issue> issues = validate(edition(year, "enum E { FIRST=7; }"));
      assertTrue(issues.toString(), hasErrorOn(issues,
          named("FIRST", com.google.eclipse.protobuf.protobuf.Literal.class)));
      assertClean(edition(year, "option features.enum_type=CLOSED; enum E { FIRST=7; }"));
      assertClean(edition(year, "enum E { option features.enum_type=CLOSED; FIRST=7; }"));
      issues = validate(edition(year, "enum E { option features.enum_type=CLOSED; FIRST=7; } "
          + "message M { map<string,E> entries=1; }"));
      assertTrue(issues.toString(), hasErrorOn(issues, named("entries", MessageField.class)));
    }
  }

  @Test public void should_reject_closed_enum_consumption_only_for_proto3_or_implicit_fields() throws Exception {
    File provider = temporary.newFile("closed.proto");
    Files.write(provider.toPath(), edition("2023", "package source; enum Closed { "
        + "option features.enum_type=CLOSED; ZERO=0; }").getBytes(StandardCharsets.UTF_8));
    String imported = "import '" + provider.toURI() + "'; message M { ";
    List<Issue> issues = validate("syntax='proto3'; " + imported + "source.Closed value=1; }");
    assertNoLinking(issues);
    assertTrue(issues.toString(), hasErrorOn(issues, named("value", MessageField.class)));
    assertClean(edition("2023", imported + "source.Closed value=1; }"));
    issues = validate(edition("2023", imported
        + "source.Closed value=1 [features.field_presence=IMPLICIT]; }"));
    assertNoLinking(issues);
    assertCode(issues, EditionValidator.EDITION_FEATURE, Severity.ERROR);
    assertClean(edition("2023", "import '" + provider.toURI() + "'; "
        + "option features.field_presence=IMPLICIT; message M { source.Closed value=1 "
        + "[features.field_presence=EXPLICIT]; }"));
  }

  @Test public void should_preserve_proto2_closed_enum_imports() throws Exception {
    File provider = temporary.newFile("legacy.proto");
    Files.write(provider.toPath(), ("syntax='proto2'; package legacy; enum E { FIRST=7; }")
        .getBytes(StandardCharsets.UTF_8));
    assertClean(edition("2023", "import '" + provider.toURI() + "'; message M { legacy.E value=1; }"));
  }

  @Test public void should_validate_explicit_feature_field_kinds_in_dotted_and_aggregate_forms() {
    String[][] invalid = {
        {"M value=1", "field_presence", "IMPLICIT"},
        {"repeated int32 value=1", "field_presence", "EXPLICIT"},
        {"map<string,int32> value=1", "field_presence", "EXPLICIT"},
        {"int32 value=1", "repeated_field_encoding", "PACKED"},
        {"string value=1", "repeated_field_encoding", "PACKED"},
        {"repeated string value=1", "repeated_field_encoding", "PACKED"},
        {"int32 value=1", "utf8_validation", "VERIFY"},
        {"map<int32,int32> value=1", "utf8_validation", "VERIFY"},
        {"int32 value=1", "message_encoding", "DELIMITED"},
        {"map<string,int32> value=1", "message_encoding", "DELIMITED"},
        {"int32 value=1", "field_presence", "FIELD_PRESENCE_UNKNOWN"},
        {"int32 value=1", "enum_type", "CLOSED"}
    };
    for (String[] row : invalid) {
      for (boolean aggregate : new boolean[] {false, true}) {
        String declaration = "message M { " + row[0];
        List<Issue> issues = validate(edition("2023", declaration + " ["
            + assignment(row[1], row[2], aggregate) + "]; }"));
        assertNoLinking(issues);
        assertCode(issues, EditionValidator.EDITION_FEATURE, Severity.ERROR);
      }
    }
  }

  @Test public void should_accept_explicit_feature_assignments_only_on_compatible_kinds() {
    for (boolean aggregate : new boolean[] {false, true}) {
      String source = "message M { repeated string strings=1 ["
          + assignment("repeated_field_encoding", "EXPANDED", aggregate) + "]; "
          + "repeated int32 numbers=2 [" + assignment("repeated_field_encoding", "PACKED", aggregate) + "]; "
          + "string text=3 [" + assignment("utf8_validation", "VERIFY", aggregate) + "]; "
          + "map<string,int32> keys=4 [" + assignment("utf8_validation", "VERIFY", aggregate) + "]; "
          + "map<int32,string> values=5 [" + assignment("utf8_validation", "VERIFY", aggregate) + "]; "
          + "repeated M children=6 [" + assignment("message_encoding", "DELIMITED", aggregate) + "]; }";
      assertClean(edition("2023", source));
      assertClean(edition("2023", "option features.repeated_field_encoding=PACKED; "
          + "option features.utf8_validation=VERIFY; message M { int32 number=1; string text=2; M child=3; }"));
    }
  }

  @Test public void should_reject_file_required_presence_and_explicit_presence_on_extensions_and_oneofs() {
    for (String body : new String[] {
        "option features.field_presence=LEGACY_REQUIRED; message M {}",
        "message M { oneof choice { int32 value=1 [features.field_presence=EXPLICIT]; } }",
        "message M { extensions 100 to 199; } extend M { int32 value=100 [features.field_presence=EXPLICIT]; }",
        "message M { extensions 100 to 199; } extend M { int32 value=100 [features.field_presence=LEGACY_REQUIRED]; }"}) {
      assertCode(validate(edition("2023", body)), EditionValidator.EDITION_FEATURE, Severity.ERROR);
    }
  }

  @Test public void should_reject_legacy_packing_and_java_utf8_true_but_preserve_false() {
    for (String value : new String[] {"true", "false"}) {
      assertCode(validate(edition("2023", "message M { repeated int32 values=1 [packed=" + value + "]; }")),
          EditionValidator.EDITION_FEATURE, Severity.ERROR);
    }
    assertCode(validate(edition("2023", "option java_string_check_utf8=true; message M {}")),
        EditionValidator.EDITION_FEATURE, Severity.ERROR);
    assertClean(edition("2023", "option java_string_check_utf8=false; message M {}"));
  }

  @Test public void should_apply_support_windows_and_targets_even_to_explicit_false() {
    assertCode(validate(edition("2023", JAVA_IMPORT + "option features.(pb.java).large_enum=false;")),
        EditionValidator.FEATURE_SUPPORT, Severity.ERROR);
    assertClean(edition("2024", JAVA_IMPORT + "option features.(pb.java).large_enum=false; "
        + "enum E { option features.(pb.java).large_enum=true; ZERO=0; }"));
    for (String year : new String[] {"2023", "2024"}) {
      assertCode(validate(edition(year, "message M { int32 value=1 "
          + "[features.enforce_proto_limits=LEGACY_NO_EXPLICIT_LIMITS]; }")),
          EditionValidator.FEATURE_SUPPORT, Severity.ERROR);
    }
    assertCode(validate(edition("2023", "option features.enforce_naming_style=STYLE2024;")),
        EditionValidator.FEATURE_SUPPORT, Severity.ERROR);
    assertCode(validate(edition("2024", JAVA_IMPORT + "message M { "
        + "option features.(pb.java).large_enum=false; }")), EditionValidator.EDITION_FEATURE, Severity.ERROR);
    assertCode(validate(edition("2024", JAVA_IMPORT
        + "option features.(pb.java).use_old_outer_classname_default=false;")),
        EditionValidator.FEATURE_SUPPORT, Severity.ERROR);
    for (String value : new String[] {"true", "false"}) {
      assertCode(validate(edition("2024", "option java_multiple_files=" + value + ";")),
          EditionValidator.FEATURE_SUPPORT, Severity.ERROR);
      assertClean(edition("2023", "option java_multiple_files=" + value + ";"));
    }
  }

  @Test public void should_keep_available_java_features_deprecated_not_removed_and_validate_value_windows() {
    List<Issue> issues = validate(edition("2024", JAVA_IMPORT
        + "option features.(pb.java).utf8_validation=VERIFY;"));
    assertCount(issues, EditionValidator.FEATURE_DEPRECATED, Severity.WARNING, 1);
    assertNoErrors(issues);
    assertClean(edition("2023", JAVA_IMPORT + "option features.(pb.java).utf8_validation=VERIFY;"));
    issues = validate(edition("2024", JAVA_IMPORT
        + "message M { option features.(pb.java).nest_in_file_class=LEGACY; }"));
    assertCode(issues, EditionValidator.FEATURE_SUPPORT, Severity.ERROR);
    assertClean(edition("2024", JAVA_IMPORT
        + "message M { option features.(pb.java).nest_in_file_class=YES; } "
        + "enum E { option features.(pb.java).nest_in_file_class=YES; ZERO=0; } "
        + "service Echo { option features.(pb.java).nest_in_file_class=YES; }"));
    assertCode(validate(edition("2024", JAVA_IMPORT + "message M { message Nested { "
        + "option features.(pb.java).nest_in_file_class=YES; } }")),
        EditionValidator.EDITION_FEATURE, Severity.ERROR);
    issues = validate(edition("2024", JAVA_IMPORT + "message M { int32 value=1 "
        + "[features.(pb.java).legacy_closed_enum=false]; }"));
    assertCode(issues, EditionValidator.EDITION_FEATURE, Severity.ERROR);
    issues = validate(edition("2024", JAVA_IMPORT + "message M { int32 value=1 "
        + "[features = { [pb.java]: { legacy_closed_enum: 0 } }]; }"));
    assertCode(issues, EditionValidator.EDITION_FEATURE, Severity.ERROR);
    issues = validate(edition("2024", JAVA_IMPORT + "enum E { ZERO=0; } message M { E value=1 "
        + "[features.(pb.java).legacy_closed_enum=false]; }"));
    assertNoErrors(issues);
    assertCode(issues, EditionValidator.FEATURE_DEPRECATED, Severity.WARNING);
    issues = validate(edition("2024", JAVA_IMPORT + "enum E { ZERO=0; } message M { E value=1 "
        + "[features = { [pb.java]: { legacy_closed_enum: 0 } }]; }"));
    assertNoErrors(issues);
    assertCode(issues, EditionValidator.FEATURE_DEPRECATED, Severity.WARNING);
  }

  @Test public void should_apply_json_policy_without_disabling_explicit_name_collisions() {
    String defaultCollision = "message M { int32 foo_bar=1; int32 fooBar=2; }";
    List<Issue> issues = validate(edition("2023", defaultCollision));
    assertCode(issues, DataTypeValidator.JSON_NAME_ERROR, Severity.ERROR);
    issues = validate(edition("2023", "option features.json_format=LEGACY_BEST_EFFORT; " + defaultCollision));
    assertNoErrors(issues);
    assertCode(issues, DataTypeValidator.JSON_NAME_ERROR, Severity.WARNING);
    issues = validate(edition("2023", "message M { option features.json_format=LEGACY_BEST_EFFORT; "
        + "int32 first=1 [json_name='same']; int32 second=2 [json_name='same']; }"));
    assertCode(issues, DataTypeValidator.JSON_NAME_ERROR, Severity.ERROR);
    issues = validate(edition("2023", "option features.json_format=LEGACY_BEST_EFFORT; "
        + "enum E { E_FOO=0; FOO=1; }"));
    assertHasError(issues);
  }

  @Test public void should_validate_2024_names_at_the_named_source_attribute() {
    for (String body : new String[] {
        "message bad_name {}", "enum bad_name { ZERO=0; }", "service bad_name {}",
        "message M { int32 BadName=1; }", "message M { oneof BadName { int32 value=1; } }",
        "enum E { bad_value=0; }", "package BadPackage; message M {}",
        "message M {} service Echo { rpc bad_name(M) returns (M); }",
        "message M { int32 trailing_=1; }", "message M { int32 bad_1=1; }",
        "message M { int32 _leading=1; }"}) {
      List<Issue> issues = validate(edition("2024", body));
      assertCode(issues, EditionValidator.EDITION_NAMING_STYLE, Severity.ERROR);
      for (Issue issue : issues) {
        if (EditionValidator.EDITION_NAMING_STYLE.equals(issue.getCode())) {
          assertNotNull(issue.getOffset());
          assertTrue(issue.getLength() > 0);
          assertTrue(issue.getMessage(), issue.getMessage().contains("STYLE_LEGACY"));
          EObject target = xtext.resource().getEObject(issue.getUriToProblem().fragment());
          assertNotNull(target);
          List<INode> names = NodeModelUtils.findNodesForFeature(target,
              target.eClass().getEStructuralFeature(target instanceof com.google.eclipse.protobuf.protobuf.Package
                  ? "importedNamespace" : "name"));
          assertEquals(issue.toString(), 1, names.size());
          assertEquals(names.get(0).getOffset(), issue.getOffset().intValue());
          assertEquals(names.get(0).getLength(), issue.getLength().intValue());
        }
      }
      assertClean(edition("2024", "option features.enforce_naming_style=STYLE_LEGACY; " + body));
    }
    assertClean(edition("2024", "package foo.bar_baz; message HTTP2Request { int32 value2=1; "
        + "oneof choice_name { int32 other_value=2; } } enum Status { STATUS_OK=0; } "
        + "service HTTPService { rpc GetURL(HTTP2Request) returns (HTTP2Request); }"));
  }

  @Test public void should_inherit_naming_overrides_without_hiding_independent_conflicts() {
    assertClean(edition("2024", "message Outer { option features.enforce_naming_style=STYLE_LEGACY; "
        + "message bad_name { int32 BadField=1; } }"));
    List<Issue> issues = validate(edition("2024", "message Outer { "
        + "option features.enforce_naming_style=STYLE_LEGACY; message bad_name {} } message bad_sibling {}"));
    assertCount(issues, EditionValidator.EDITION_NAMING_STYLE, Severity.ERROR, 1);
    issues = validate(edition("2024", "option features.enforce_naming_style=STYLE_LEGACY; "
        + "message bad_name { int32 BadField=1; int32 BadField=2; }"));
    assertHasError(issues);
    assertNoCode(issues, EditionValidator.EDITION_NAMING_STYLE);
  }

  @Test public void should_apply_style2026_collision_rules_without_enabling_later_source_editions() {
    for (String declaration : new String[] {
        "int32 descriptor=1;", "int32 name=1; int32 has_name=2;",
        "int32 name=1; int32 get_name=2;", "int32 name=1; int32 set_name=2;",
        "int32 name=1; int32 clear_name=2;", "int32 name=1; int32 name_value=2;",
        "oneof name { int32 value=1; } int32 has_name=2;"}) {
      assertClean(edition("2024", "message M { " + declaration + " }"));
      List<Issue> issues = validate(edition("2024", "option features.enforce_naming_style=STYLE2026; "
          + "message M { " + declaration + " }"));
      assertCode(issues, EditionValidator.EDITION_NAMING_STYLE, Severity.ERROR);
      assertNoCode(issues, EditionValidator.FEATURE_SUPPORT);
    }
    assertClean(edition("2024", "option features.enforce_naming_style=STYLE2026; "
        + "message M { int32 has_absent=1; int32 absent_value=2; }"));
  }

  @Test public void should_validate_custom_feature_uses_in_dotted_and_aggregate_forms() {
    for (String use : new String[] {"option features.(custom).enabled=false;",
        "option features={ [custom]: { enabled:false } };",
        "option features=< [custom]: < enabled:false > >;"}) {
      assertClean(edition("2024", customFeature("bool enabled", metadata()) + use));
    }
    List<Issue> issues = validate(edition("2024", customFeature("bool enabled", metadata())
        + "option features.(custom).enabled=false; option features={ [custom]: { enabled:true } };"));
    assertCode(issues, DataTypeValidator.OPTION_ASSIGNMENT_ERROR, Severity.ERROR);
  }

  @Test public void should_report_malformed_custom_definitions_on_real_defining_fields() {
    for (String declaration : new String[] {"repeated bool enabled", "required bool enabled", "string enabled"}) {
      List<Issue> issues = validate(edition("2023", customFeature(declaration, metadata())));
      assertOn(issues, EditionValidator.INVALID_FEATURE_DEFINITION, named("enabled", MessageField.class));
    }
    for (String metadata : new String[] {
        metadata().replace("value:'true'", "value:'not_bool'"),
        metadata().replace("targets=TARGET_TYPE_FILE,", ""),
        metadata().replace("feature_support={edition_introduced:EDITION_2023},", ""),
        "targets=TARGET_TYPE_FILE,feature_support={edition_introduced:EDITION_2023}",
        metadata().replace("edition_introduced:EDITION_2023", "edition_introduced:EDITION_2024,edition_removed:EDITION_2023"),
        metadata().replace("edition_introduced:EDITION_2023", "edition_introduced:EDITION_2023,edition_deprecated:EDITION_2024"),
        metadata().replace("edition_introduced:EDITION_2023", "edition_introduced:EDITION_2023,edition_removed:EDITION_2024"),
        metadata().replace("edition_introduced:EDITION_2023", "edition_introduced:EDITION_2023,"
            + "edition_removed:EDITION_2024,removal_error:'Use replacement'")
            + ",edition_defaults={edition:EDITION_2026,value:'true'}",
        metadata().replace("edition_introduced:EDITION_2023", "edition_introduced:EDITION_2024")
            + ",edition_defaults={edition:EDITION_2023,value:'false'}",
        metadata() + ",edition_defaults={edition:EDITION_2023,value:'not_bool'}"}) {
      List<Issue> issues = validate(edition("2023", customFeature("bool enabled", metadata)));
      assertOn(issues, EditionValidator.INVALID_FEATURE_DEFINITION, named("enabled", MessageField.class));
    }
  }

  @Test public void should_preserve_partial_support_metadata_on_ordinary_options_and_ordinary_features_names() {
    assertClean("syntax='proto2'; import 'google/protobuf/descriptor.proto'; "
        + "message Ordinary { optional bool features=1 [feature_support={edition_removed:EDITION_2024}]; } "
        + "extend google.protobuf.FileOptions { optional Ordinary config=51000; } "
        + "option (config).features=false;");
    assertClean(edition("2024", "import 'google/protobuf/descriptor.proto'; "
        + "message Ordinary { bool features=1; } extend google.protobuf.FileOptions { Ordinary config=51000; } "
        + "option (config).features=false;"));
  }

  @Test public void should_report_one_header_error_for_missing_or_incomplete_selected_metadata() throws Exception {
    IPreferenceStore store = preferences.getWritablePreferenceStore();
    boolean wasDefault = store.isDefault(PreferenceNames.DESCRIPTOR_PROTO_PATH);
    String previous = store.getString(PreferenceNames.DESCRIPTOR_PROTO_PATH);
    try {
      for (boolean missing : new boolean[] {true, false}) {
        xtext.parseText(edition("2024", "message M { optional int32 value=1; }"));
        URI selected = URI.createURI("file:/edition-tests/" + (missing ? "missing" : "old") + ".proto");
        if (!missing) {
          Resource descriptor = xtext.resource().getResourceSet().createResource(selected);
          descriptor.load(new StringInputStream("syntax='proto2'; package google.protobuf; "
              + "message FileOptions {} message FeatureSet {}"), null);
        }
        store.setValue(PreferenceNames.DESCRIPTOR_PROTO_PATH, selected.toString());
        descriptors.invalidateCacheFor(null);
        List<Issue> issues = issues();
        assertCount(issues, EditionValidator.EDITIONS_FEATURES_UNAVAILABLE, Severity.ERROR, 1);
        assertOn(issues, EditionValidator.EDITIONS_FEATURES_UNAVAILABLE, xtext.root().getEdition());
        assertCode(issues, EditionValidator.EDITION_FIELD_MODIFIER, Severity.ERROR);
        assertNoCode(issues, EditionValidator.EDITION_NAMING_STYLE);
      }
    } finally {
      if (wasDefault) store.setToDefault(PreferenceNames.DESCRIPTOR_PROTO_PATH);
      else store.setValue(PreferenceNames.DESCRIPTOR_PROTO_PATH, previous);
      descriptors.invalidateCacheFor(null);
    }
  }

  @Test public void should_allocate_zero_for_open_enums_and_honor_reserved_numbers_for_closed_enums() {
    for (String year : new String[] {"2023", "2024"}) {
      validate(edition(year, "enum E { FIRST=7; }"));
      assertEquals(0L, literals.calculateNewIndexOf(named("FIRST", com.google.eclipse.protobuf.protobuf.Literal.class)));
      validate(edition(year, "enum E { reserved 0; FIRST=7; }"));
      assertEquals(Long.MIN_VALUE,
          literals.calculateNewIndexOf(named("FIRST", com.google.eclipse.protobuf.protobuf.Literal.class)));
      assertClean(edition(year, "enum E { option features.enum_type=CLOSED; reserved 0 to 6; FIRST=7; }"));
      assertEquals(7L, literals.calculateNewIndexOf(named("FIRST", com.google.eclipse.protobuf.protobuf.Literal.class)));
    }
  }

  @Test public void should_apply_custom_support_boundaries_to_false_and_warn_only_during_deprecation() {
    String lifetime = metadata().replace("edition_introduced:EDITION_2023",
        "edition_introduced:EDITION_2023,edition_deprecated:EDITION_2024,"
        + "deprecation_warning:'Prefer another feature',edition_removed:EDITION_2026,removal_error:'Use replacement'");
    assertClean(edition("2023", customFeature("bool enabled", lifetime)
        + "option features.(custom).enabled=false;"));
    List<Issue> issues = validate(edition("2024", customFeature("bool enabled", lifetime)
        + "option features={ [custom]: {enabled:false} } ;"));
    assertNoErrors(issues);
    assertCount(issues, EditionValidator.FEATURE_DEPRECATED, Severity.WARNING, 1);
    assertCodeMessage(issues, EditionValidator.FEATURE_DEPRECATED, "Prefer another feature");
    String removed = metadata().replace("edition_introduced:EDITION_2023",
        "edition_introduced:EDITION_2023,edition_removed:EDITION_2024,removal_error:'Use replacement'")
        + ",edition_defaults={edition:EDITION_2024,value:'false'}";
    assertClean(edition("2023", customFeature("bool enabled", removed)
        + "option features.(custom).enabled=false;"));
    assertClean(edition("2024", customFeature("bool enabled", removed)));
    issues = validate(edition("2024", customFeature("bool enabled", removed)
        + "option features.(custom).enabled=false;"));
    assertCount(issues, EditionValidator.FEATURE_SUPPORT, Severity.ERROR, 1);
    assertCodeMessage(issues, EditionValidator.FEATURE_SUPPORT, "2024");
    assertCodeMessage(issues, EditionValidator.FEATURE_SUPPORT, "enabled");
    assertCodeMessage(issues, EditionValidator.FEATURE_SUPPORT, "Use replacement");
    assertNoCode(issues, EditionValidator.FEATURE_DEPRECATED);
    String introduced = metadata().replace("edition_introduced:EDITION_2023", "edition_introduced:EDITION_2024");
    issues = validate(edition("2023", customFeature("bool enabled", introduced)
        + "option features.(custom).enabled=false;"));
    assertCode(issues, EditionValidator.FEATURE_SUPPORT, Severity.ERROR);
    assertCodeMessage(issues, EditionValidator.FEATURE_SUPPORT, "2023");
    assertCodeMessage(issues, EditionValidator.FEATURE_SUPPORT, "2024");
    assertClean(edition("2024", customFeature("bool enabled", introduced)
        + "option features.(custom).enabled=false;"));
  }

  @Test public void should_reject_invalid_feature_extension_shapes_and_payload_oneofs_or_extensions() {
    String valid = customFeature("bool enabled", metadata());
    for (String declaration : new String[] {"repeated CustomFeatures custom", "bool custom"}) {
      List<Issue> issues = validate(edition("2023", valid.replace("CustomFeatures custom", declaration)));
      assertOn(issues, EditionValidator.INVALID_FEATURE_DEFINITION, named("custom", MessageField.class));
    }
    for (String extra : new String[] {"extensions 100 to 199;",
        "message Nested { extensions 100 to 199; }",
        "oneof choice { bool other=2 [" + metadata() + "]; }"}) {
      List<Issue> issues = validate(edition("2023", valid.replace("message CustomFeatures {",
          "message CustomFeatures { " + extra)));
      assertCode(issues, EditionValidator.INVALID_FEATURE_DEFINITION, Severity.ERROR);
    }
  }

  @Test public void should_validate_enum_feature_defaults_and_refined_value_support_windows() {
    String definition = "import 'google/protobuf/descriptor.proto'; "
        + "extend google.protobuf.FeatureSet { CustomFeatures custom=9995; } "
        + "message CustomFeatures { enum Mode { MODE_ZERO=0; MODE_RETIRED=1 "
        + "[feature_support={edition_introduced:EDITION_2023,edition_removed:EDITION_2024,"
        + "removal_error:'Use MODE_ZERO'}]; } Mode mode=1 ["
        + metadata().replace("value:'true'", "value:'MODE_ZERO'") + "]; } ";
    assertClean(edition("2024", definition + "option features.(custom).mode=MODE_ZERO;"));
    assertClean(edition("2023", definition + "option features.(custom).mode=MODE_RETIRED;"));
    List<Issue> issues = validate(edition("2024", definition + "option features.(custom).mode=MODE_RETIRED;"));
    assertCode(issues, EditionValidator.FEATURE_SUPPORT, Severity.ERROR);
    assertCodeMessage(issues, EditionValidator.FEATURE_SUPPORT, "MODE_RETIRED");
    issues = validate(edition("2023", definition.replace("value:'MODE_ZERO'", "value:'MISSING'")));
    assertOn(issues, EditionValidator.INVALID_FEATURE_DEFINITION, named("mode", MessageField.class));
    issues = validate(edition("2023", definition.replace("edition_introduced:EDITION_2023,edition_removed",
        "edition_introduced:EDITION_PROTO3,edition_removed")));
    assertCode(issues, EditionValidator.INVALID_FEATURE_DEFINITION, Severity.ERROR);
  }

  @Test public void should_apply_ordinary_option_field_and_enum_value_windows_through_all_paths() {
    String declarations = "import 'google/protobuf/descriptor.proto'; "
        + "message Settings { bool enabled=1 [feature_support={edition_removed:EDITION_2024,"
        + "removal_error:'Use replacement'}]; enum Mode { MODE_ZERO=0; MODE_RETIRED=1 "
        + "[feature_support={edition_removed:EDITION_2024,removal_error:'Use MODE_ZERO'}]; } "
        + "Mode mode=2; } extend google.protobuf.FileOptions { Settings settings=51000; } ";
    for (String use : new String[] {"option (settings).enabled=false;",
        "option (settings)={enabled:false};", "option (settings).mode=MODE_RETIRED;",
        "option (settings)=<mode:MODE_RETIRED>;"}) {
      assertClean(edition("2023", declarations + use));
      List<Issue> issues = validate(edition("2024", declarations + use));
      assertNoLinking(issues);
      assertCount(issues, EditionValidator.FEATURE_SUPPORT, Severity.ERROR, 1);
      assertNoCode(issues, EditionValidator.INVALID_FEATURE_DEFINITION);
    }
  }

  @Test public void should_recompute_source_diagnostics_when_custom_support_metadata_changes() throws Exception {
    String source = edition("2023", customFeature("bool enabled", metadata().replace(
        "edition_introduced:EDITION_2023", "edition_introduced:EDITION_2024"))
        + "option features.(custom).enabled=false;");
    assertCode(validate(source), EditionValidator.FEATURE_SUPPORT, Severity.ERROR);
    xtext.resource().reparse(source.replace("edition_introduced:EDITION_2024", "edition_introduced:EDITION_2023"));
    List<Issue> issues = issues();
    assertTrue(issues.toString(), issues.isEmpty());
    xtext.resource().reparse(source);
    assertCode(issues(), EditionValidator.FEATURE_SUPPORT, Severity.ERROR);
  }

  @Test public void should_reject_inherited_implicit_closed_enum_fields_without_rejecting_explicit_siblings() {
    List<Issue> issues = validate(edition("2023", "option features.field_presence=IMPLICIT; "
        + "enum E { option features.enum_type=CLOSED; ZERO=0; } message M { E implicit_value=1; "
        + "E explicit_value=2 [features.field_presence=EXPLICIT]; }"));
    assertCount(issues, EditionValidator.EDITION_FEATURE, Severity.ERROR, 1);
    assertOn(issues, EditionValidator.EDITION_FEATURE, named("implicit_value", MessageField.class));
    assertFalse(issues.toString(), hasErrorOn(issues, named("explicit_value", MessageField.class)));
  }

  private void assertCodeMessage(List<Issue> issues, String code, String text) {
    for (Issue issue : issues) {
      if (code.equals(issue.getCode()) && issue.getMessage().contains(text)) return;
    }
    fail("Expected " + code + " mentioning " + text + ", got " + issues);
  }

  private String edition(String year, String body) {
    return "edition='" + year + "'; " + body;
  }

  private String assignment(String name, String value, boolean aggregate) {
    return aggregate ? "features={" + name + ":" + value + "}" : "features." + name + "=" + value;
  }

  private String metadata() {
    return "targets=TARGET_TYPE_FILE,feature_support={edition_introduced:EDITION_2023},"
        + "edition_defaults={edition:EDITION_LEGACY,value:'true'}";
  }

  private String customFeature(String declaration, String metadata) {
    return "import 'google/protobuf/descriptor.proto'; "
        + "extend google.protobuf.FeatureSet { CustomFeatures custom=9995; } "
        + "message CustomFeatures { " + declaration + "=1 [" + metadata + "]; } ";
  }

  private List<Issue> validate(String source) {
    xtext.parseText(source);
    return issues();
  }

  private List<Issue> issues() {
    return validator.validate(xtext.resource(), CheckMode.ALL, CancelIndicator.NullImpl);
  }

  private void assertClean(String source) {
    List<Issue> issues = validate(source);
    assertTrue(issues.toString(), issues.isEmpty());
    assertTrue(xtext.resource().getErrors().toString(), xtext.resource().getErrors().isEmpty());
  }

  private void assertHasError(List<Issue> issues) {
    for (Issue issue : issues) if (issue.getSeverity() == Severity.ERROR) return;
    fail("Expected an error, got " + issues);
  }

  private void assertNoErrors(List<Issue> issues) {
    for (Issue issue : issues) assertFalse(issue.toString(), issue.getSeverity() == Severity.ERROR);
  }

  private void assertNoLinking(List<Issue> issues) {
    assertNoCode(issues, Diagnostic.LINKING_DIAGNOSTIC);
  }

  private void assertCode(List<Issue> issues, String code, Severity severity) {
    for (Issue issue : issues) if (code.equals(issue.getCode()) && severity == issue.getSeverity()) return;
    fail("Expected " + severity + " " + code + ", got " + issues);
  }

  private void assertNoCode(List<Issue> issues, String code) {
    for (Issue issue : issues) assertFalse(issue.toString(), code.equals(issue.getCode()));
  }

  private void assertCount(List<Issue> issues, String code, Severity severity, int expected) {
    int count = 0;
    for (Issue issue : issues) if (code.equals(issue.getCode()) && severity == issue.getSeverity()) count++;
    assertEquals(issues.toString(), expected, count);
  }

  private void assertOn(List<Issue> issues, String code, EObject element) {
    for (Issue issue : issues) {
      if (code.equals(issue.getCode()) && issue.getSeverity() == Severity.ERROR && within(issue, element)) return;
    }
    fail("Expected " + code + " on " + element + ", got " + issues);
  }

  private boolean hasErrorOn(List<Issue> issues, EObject element) {
    for (Issue issue : issues) if (issue.getSeverity() == Severity.ERROR && within(issue, element)) return true;
    return false;
  }

  private boolean within(Issue issue, EObject element) {
    INode node = NodeModelUtils.getNode(element);
    return node != null && issue.getOffset() != null && issue.getOffset() >= node.getOffset()
        && issue.getOffset() < node.getEndOffset();
  }

  private <T extends EObject> T named(String name, Class<T> type) {
    for (T element : EcoreUtil2.getAllContentsOfType(xtext.root(), type)) {
      if (name.equals(element.eGet(element.eClass().getEStructuralFeature("name")))) return element;
    }
    throw new AssertionError("Missing declaration " + name + " of " + type.getSimpleName());
  }
}
