package com.google.eclipse.protobuf.ui.contentassist;

import static com.google.eclipse.protobuf.junit.core.XtextRule.createWith;
import static com.google.eclipse.protobuf.preferences.general.PreferenceNames.DESCRIPTOR_PROTO_PATH;
import static com.google.eclipse.protobuf.validation.ProtobufJavaValidator.*;
import static com.google.eclipse.protobuf.validation.DataTypeValidator.EXPECTED_BOOL_ERROR;
import static com.google.eclipse.protobuf.validation.EditionValidator.*;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.ui.editor.XtextEditor;
import org.eclipse.xtext.ui.editor.contentassist.ConfigurableCompletionProposal;
import org.eclipse.xtext.ui.editor.contentassist.ContentAssistContext;
import org.eclipse.xtext.ui.editor.contentassist.ICompletionProposalAcceptor;
import org.eclipse.xtext.ui.editor.preferences.IPreferenceStoreAccess;
import org.eclipse.xtext.ui.editor.quickfix.IssueResolution;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.eclipse.xtext.validation.Issue;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.google.eclipse.protobuf.junit.core.XtextRule;
import com.google.eclipse.protobuf.ui.plugin.ProtobufEditorPlugIn;
import com.google.eclipse.protobuf.ui.quickfix.ProtobufQuickfixProvider;
import com.google.inject.Inject;

/** Uses the generated content-assist parser, real editor proposals, and real quickfix document edits. */
public class EditorAssistance_Test {
  private static final String DESCRIPTOR_IMPORT = "import \"google/protobuf/descriptor.proto\"; ";
  private static final String CUSTOM = "syntax = \"proto2\"; " + DESCRIPTOR_IMPORT
      + "import \"google/protobuf/any.proto\"; "
      + "enum Mode { ZERO=0; true=1; inf=2; } "
      + "message Detail { optional bool enabled=1; optional Mode mode=2; } "
      + "message Settings { optional Detail detail=1; repeated Mode modes=2; "
      + "map<string, Detail> entries=3; optional google.protobuf.Any any=4; } "
      + "extend google.protobuf.FileOptions { optional Settings settings=50001; } ";

  @Rule public XtextRule xtext = createWith(ProtobufEditorPlugIn.injector());
  @Inject private ContentAssistContext.Factory contexts;
  @Inject private ProtobufProposalProvider proposals;
  @Inject private ProtobufQuickfixProvider fixes;
  @Inject private IResourceValidator validator;
  @Inject private IPreferenceStoreAccess stores;

  private IProject project;
  private XtextEditor editor;
  private IPreferenceStore store;
  private String oldDescriptor;
  private int offset;

  @Before public void setUp() throws Exception {
    onUi(() -> {
      project = ResourcesPlugin.getWorkspace().getRoot().getProject("editor-assistance-" + System.nanoTime());
      project.create(null);
      project.open(null);
      store = stores.getWritablePreferenceStore();
      oldDescriptor = store.getString(DESCRIPTOR_PROTO_PATH);
      store.setValue(DESCRIPTOR_PROTO_PATH, "google/protobuf/descriptor.proto");
      write("legacy.proto", "syntax=\"proto2\"; package legacy; enum Closed { ZERO=0; } message Payload {}");
      return null;
    });
  }

  @After public void tearDown() throws Exception {
    onUi(() -> {
      if (editor != null) {
        editor.getSite().getPage().closeEditor(editor, false);
      }
      if (store != null) {
        store.setValue(DESCRIPTOR_PROTO_PATH, oldDescriptor);
      }
      if (project != null && project.exists()) {
        project.delete(true, true, null);
      }
      return null;
    });
  }

  @Test public void should_apply_proto3_syntax_in_all_three_positions() throws Exception {
    for (String input : new String[] {"|", "syntax |", "syntax = |"}) {
      onUi(() -> {
        Map<String, ICompletionProposal> choices = complete(input);
        String proposal = input.equals("|") ? "syntax = \"proto3\";"
            : input.equals("syntax |") ? "= \"proto3\";" : "\"proto3\";";
        apply(choices, proposal);
        assertEquals("syntax = \"proto3\";", editor.getDocument().get());
        assertNoErrors();
        return null;
      });
    }
  }

  @Test public void should_filter_declarations_without_filtering_keyword_identifiers() throws Exception {
    onUi(() -> {
      Map<String, ICompletionProposal> choices = complete("syntax=\"proto3\"; message M { | }");
      absent(choices, "required", "group", "extensions");
      present(choices, "optional", "repeated", "map");
      choices = complete("syntax=\"proto3\"; message M { oneof choice { | } }");
      absent(choices, "optional", "required", "repeated", "map", "group", "extensions");
      choices = complete("syntax=\"proto3\"; service S { | }");
      present(choices, "rpc");
      absent(choices, "stream");
      choices = complete("syntax=\"proto3\"; message M { string | = 1; }");
      apply(choices, "required");
      assertTrue(editor.getDocument().get().contains("string required = 1"));
      choices = complete("syntax=\"proto3\"; message M { string req| = 1; }");
      apply(choices, "required");
      assertTrue(editor.getDocument().get().contains("string required = 1"));
      assertNoErrors();
      return null;
    });
  }

  @Test public void should_filter_map_types_and_closed_enums_but_keep_messages() throws Exception {
    onUi(() -> {
      String prefix = "syntax=\"proto3\"; import \"legacy.proto\"; message M { ";
      Map<String, ICompletionProposal> keys = complete(prefix + "map<|, string> values=1; }");
      present(keys, "bool", "string", "int32", "int64", "uint32", "uint64", "sint32", "sint64",
          "fixed32", "fixed64", "sfixed32", "sfixed64");
      absent(keys, "float", "double", "bytes", "map", "legacy.Closed", "legacy.Payload");
      keys = complete(prefix + "map< /* key */ |, string> values=1; }");
      present(keys, "bool", "string", "uint32");
      absent(keys, "float", "double", "bytes", "map", "legacy.Closed", "legacy.Payload");
      Map<String, ICompletionProposal> values = complete(prefix + "map<string, |> values=1; }");
      present(values, "string", "double", "legacy.Payload");
      absent(values, "map", "legacy.Closed");
      apply(values, "legacy.Payload");
      assertNoErrors();
      Map<String, ICompletionProposal> ordinary = complete(prefix + "| value=1; }");
      absent(ordinary, "legacy.Closed");
      present(ordinary, "legacy.Payload");
      return null;
    });
  }

  @Test public void should_offer_only_proto3_option_extendees() throws Exception {
    onUi(() -> {
      Map<String, ICompletionProposal> choices = complete("syntax=\"proto3\"; " + DESCRIPTOR_IMPORT
          + "message FileOptions {} extend | { bool enabled=50001; }");
      present(choices, "google.protobuf.FileOptions", "google.protobuf.OneofOptions", "google.protobuf.MethodOptions");
      absent(choices, "FileOptions", "google.protobuf.FeatureSet", "google.protobuf.DescriptorProto");
      apply(choices, "google.protobuf.FileOptions");
      assertNoErrors();
      return null;
    });
  }

  @Test public void should_use_context_specific_native_options_and_cardinality() throws Exception {
    onUi(() -> {
      Map<String, ICompletionProposal> choices = complete("syntax=\"proto3\"; message M { optional int32 x=1 [|]; }");
      present(choices, "deprecated");
      absent(choices, "default", "packed", "features", "x", "M.x", "go_package");
      choices = complete("syntax=\"proto3\"; message M { repeated int32 x=1 [|]; }");
      present(choices, "packed");
      choices = complete("syntax=\"proto2\"; enum E { ZERO=0 [|]; }");
      present(choices, "feature_support", "deprecated");
      absent(choices, "default", "json_name", "packed", "retention", "features");
      absent(choices, "ZERO", "E.ZERO");
      choices = complete("syntax=\"proto2\"; message M { extensions 100 to max [|]; }");
      present(choices, "declaration", "verification");
      absent(choices, "default", "json_name", "feature_support", "packed", "features");
      absent(choices, "M.x", "google.protobuf.FieldOptions.retention");
      choices = complete("syntax=\"proto2\"; message M { optional int32 x=1 "
          + "[retention=RETENTION_RUNTIME, targets=TARGET_TYPE_FILE, |=TARGET_TYPE_FIELD]; }");
      present(choices, "targets", "edition_defaults");
      absent(choices, "retention");
      apply(choices, "targets");
      assertNoErrors();
      choices = complete("syntax=\"proto2\"; message M { optional int32 x=1 "
          + "[edition_defaults={edition:EDITION_PROTO2 value:\"0\"}, "
          + "|={edition:EDITION_PROTO3 value:\"1\"}]; }");
      apply(choices, "edition_defaults");
      assertNoErrors();
      choices = complete("syntax=\"proto2\"; message M { extensions 100 to max "
          + "[declaration={number:100 full_name:\".x\" type:\"int32\"}, "
          + "|={number:101 full_name:\".y\" type:\"int32\"}]; }");
      apply(choices, "declaration");
      assertNoErrors();
      choices = complete("syntax=\"proto2\"; message M { optional int32 x=1 "
          + "[feature_support={edition_introduced:EDITION_PROTO2}, |]; }");
      absent(choices, "feature_support");
      choices = complete("syntax=\"proto2\"; message M { option | = true; }");
      absent(choices, "features", "map_entry");
      return null;
    });
  }

  @Test public void should_apply_typed_native_values_and_simple_keyword_enum_literals() throws Exception {
    onUi(() -> {
      Map<String, ICompletionProposal> choices = complete("syntax=\"proto2\"; message M { optional int32 x=1 "
          + "[retention=|]; }");
      apply(choices, "RETENTION_RUNTIME");
      assertNoErrors();
      choices = complete("syntax=\"proto2\"; message M { extensions 100 to max [verification=|]; }");
      apply(choices, "UNVERIFIED");
      absent(choices, "ExtensionRangeOptions.UNVERIFIED", "google.protobuf.ExtensionRangeOptions.UNVERIFIED");
      assertNoErrors();
      choices = complete("syntax=\"proto2\"; enum E { ZERO=0 [feature_support={edition_introduced: |}]; }");
      apply(choices, "EDITION_2024");
      assertNoErrors();
      choices = complete(CUSTOM + "option (settings).detail = { mode: | };");
      present(choices, "ZERO", "true", "inf");
      absent(choices, "Mode.ZERO", "Mode.true", "false", "nan");
      apply(choices, "true");
      assertNoErrors();
      return null;
    });
  }

  @Test public void should_apply_native_dotted_and_range_aggregate_members() throws Exception {
    onUi(() -> {
      Map<String, ICompletionProposal> choices = complete("syntax=\"proto2\"; message M { optional int32 x=1 "
          + "[feature_support.|=EDITION_2024]; }");
      apply(choices, "edition_introduced");
      assertNoErrors();
      choices = complete("syntax=\"proto2\"; message M { extensions 100 to 199 "
          + "[verification=DECLARATION, declaration={ |:100 full_name:\".ext\" type:\"int32\" }]; }");
      present(choices, "number", "full_name", "type", "reserved");
      absent(choices, "retention", "feature_support", "java_package");
      apply(choices, "number");
      assertNoErrors();
      return null;
    });
  }

  @Test public void should_apply_dotted_aggregate_map_and_any_values() throws Exception {
    onUi(() -> {
      Map<String, ICompletionProposal> choices = complete(CUSTOM + "option (settings).| = { enabled:true };");
      present(choices, "detail", "modes", "entries", "any");
      absent(choices, "settings", "Settings.detail", "Detail.enabled", "google.protobuf.FileOptions.java_package");
      apply(choices, "detail");
      assertNoErrors();
      choices = complete(CUSTOM + "option (settings) = { detail: { |:true } };");
      present(choices, "enabled", "mode");
      absent(choices, "detail", "settings", "Detail.enabled", "Settings.detail");
      apply(choices, "enabled");
      assertNoErrors();
      choices = complete(CUSTOM + "option (settings) = { entries: { key: | value: {enabled:true} } };");
      apply(choices, "\"\"");
      assertNoErrors();
      choices = complete(CUSTOM + "option (settings) = { entries: { key:\"x\" value: { enabled: | } } };");
      apply(choices, "true");
      assertNoErrors();
      choices = complete(CUSTOM + "option (settings) = { any: { [example.com/Detail] { enabled: | } } };");
      apply(choices, "false");
      assertNoErrors();
      choices = complete("syntax=\"proto3\"; message M { string text=1 [json_name=|]; }");
      apply(choices, "\"\"");
      assertNoErrors();
      return null;
    });
  }

  @Test public void should_offer_real_oneof_and_method_option_sources() throws Exception {
    onUi(() -> {
      String prefix = "syntax=\"proto3\"; " + DESCRIPTOR_IMPORT
          + "extend google.protobuf.OneofOptions { string note=50001; } "
          + "extend google.protobuf.MethodOptions { bool route=50002; } ";
      Map<String, ICompletionProposal> choices = complete(prefix
          + "message M { oneof choice { option (|)=\"text\"; string value=1; } }");
      apply(choices, "note");
      assertNoErrors();
      choices = complete(prefix + "message M {} service S { rpc Get(M) returns(M) { option (route)=|; } }");
      apply(choices, "true");
      assertNoErrors();
      return null;
    });
  }

  @Test public void should_derive_unique_map_names_across_oneof_members() throws Exception {
    onUi(() -> {
      Map<String, ICompletionProposal> choices = complete("syntax=\"proto3\"; message Foo {} message M { "
          + "oneof choice { string fooMap=1; string fooMap1=2; } map<string,Foo> | = 3; }");
      apply(choices, "fooMap2");
      assertNoErrors();
      return null;
    });
  }

  @Test public void should_preserve_proto2_default_shorthand_and_omit_proto3_default_values() throws Exception {
    onUi(() -> {
      Map<String, ICompletionProposal> choices = complete("syntax=\"proto2\"; message M { optional bool enabled=1 |; }");
      present(choices, "[default = ]");
      choices = complete("syntax=\"proto3\"; message M { optional bool enabled=1 |; }");
      present(choices, "[");
      absent(choices, "[default = ]");
      choices = complete("syntax=\"proto3\"; message M { optional bool enabled=1 [default=|]; }");
      absent(choices, "true", "false");
      return null;
    });
  }

  @Test public void should_remove_entire_proto3_defaults_and_the_correct_separator() throws Exception {
    for (String options : new String[] {"default=7", "default=7, deprecated=true", "deprecated=true, default=7",
        "deprecated=true, default=7, json_name=\"renamed\""}) {
      onUi(() -> {
        open("syntax=\"proto3\"; message M { int32 count=1 [" + options + "]; }");
        applyFix(PROTO3_DEFAULT_ERROR, "Remove explicit default");
        String text = editor.getDocument().get();
        assertFalse(text.contains("default"));
        if (options.equals("default=7")) {
          assertFalse(text.contains("["));
        } else {
          assertTrue(text.contains("deprecated=true"));
        }
        assertNoErrors();
        return null;
      });
    }
  }

  @Test public void should_remove_map_and_oneof_modifiers_and_change_required_to_optional() throws Exception {
    onUi(() -> {
      open("syntax=\"proto3\"; message M { optional map<string,int32> counts=1; }");
      applyFix(MAP_WITH_MODIFIER_ERROR, "Remove modifier");
      assertTrue(editor.getDocument().get().contains("map<string,int32> counts"));
      assertFalse(editor.getDocument().get().contains("optional"));
      assertNoErrors();
      open("syntax=\"proto3\"; message M { oneof choice { optional int32 count=1; } }");
      applyFix(ONEOF_FIELD_WITH_MODIFIER_ERROR, "Remove modifier");
      assertFalse(editor.getDocument().get().contains("optional"));
      assertNoErrors();
      open("syntax=\"proto3\"; message M { required int32 count=1; }");
      applyFix(REQUIRED_IN_PROTO3_ERROR, "optional");
      assertTrue(editor.getDocument().get().contains("optional"));
      assertFalse(editor.getDocument().get().contains("required"));
      assertNoErrors();
      return null;
    });
  }

  @Test public void should_serialize_canonical_boolean_quickfixes_without_value_only_proto3_defaults() throws Exception {
    for (String value : new String[] {"true", "false"}) {
      onUi(() -> {
        open("syntax=\"proto3\"; message M { int32 count=1 [deprecated=\"bad\"]; }");
        applyFix(EXPECTED_BOOL_ERROR, value);
        assertTrue(editor.getDocument().get().contains(value));
        assertNoErrors();
        open("syntax=\"proto3\"; message M { bool enabled=1 [default=\"bad\"]; }");
        for (Issue issue : issues()) {
          if (EXPECTED_BOOL_ERROR.equals(issue.getCode())) {
            assertTrue(fixes.getResolutions(issue).isEmpty());
          }
        }
        return null;
      });
    }
  }

  @Test public void should_apply_both_editions_in_all_three_header_positions() throws Exception {
    for (String year : new String[] {"2023", "2024"}) {
      for (String input : new String[] {"|", "edition |", "edition = |"}) {
        onUi(() -> {
          Map<String, ICompletionProposal> choices = complete(input);
          String suffix = "\"" + year + "\";";
          apply(choices, input.equals("|") ? "edition = " + suffix
              : input.equals("edition |") ? "= " + suffix : suffix);
          assertEquals("edition = " + suffix, editor.getDocument().get());
          assertNoErrors();
          return null;
        });
      }
    }
  }

  @Test public void should_not_offer_a_second_header_even_after_recovered_headers() throws Exception {
    onUi(() -> {
      for (String prefix : new String[] {"edition=\"2023\"; ", "edition=\"2024\"; ",
          "syntax=\"proto2\"; ", "message M {} edition=\"2024\"; "}) {
        Map<String, ICompletionProposal> choices = complete(prefix + "|");
        absent(choices, "syntax", "edition", "syntax = \"proto2\";", "syntax = \"proto3\";",
            "edition = \"2023\";", "edition = \"2024\";");
      }
      return null;
    });
  }

  @Test public void should_repair_unsupported_editions_without_changing_dialect() throws Exception {
    for (String year : new String[] {"2023", "2024"}) {
      onUi(() -> {
        open("edition=\"2025\"; message M { int32 value=1; }");
        applyFix(UNSUPPORTED_EDITION, year);
        assertTrue(editor.getDocument().get().contains(year));
        assertFalse(editor.getDocument().get().contains("syntax"));
        assertNoErrors();
        return null;
      });
    }
  }

  @Test public void should_remove_both_forbidden_edition_field_modifiers() throws Exception {
    for (String year : new String[] {"2023", "2024"}) {
      for (String modifier : new String[] {"optional", "required"}) {
        onUi(() -> {
          open("edition=\"" + year + "\"; message M { " + modifier + " int32 value=1; }");
          applyFix(EDITION_FIELD_MODIFIER, "Remove modifier");
          assertFalse(editor.getDocument().get().contains(modifier));
          assertTrue(editor.getDocument().get().contains("int32 value=1"));
          assertNoErrors();
          return null;
        });
      }
    }
  }

  @Test public void should_remove_entire_edition_defaults_preserving_all_neighbor_options() throws Exception {
    for (String options : new String[] {"default=7", "default=7, deprecated=true",
        "deprecated=true, default=7", "deprecated=true, default=7, json_name=\"renamed\""}) {
      onUi(() -> {
        open("edition=\"2023\"; option features.field_presence=IMPLICIT; "
            + "message M { int32 count=1 [" + options + "]; }");
        applyFix(EDITION_DEFAULT, "Remove explicit default");
        String text = editor.getDocument().get();
        assertFalse(text.contains("default"));
        if (options.equals("default=7")) assertFalse(text.contains("["));
        if (options.contains("deprecated")) assertTrue(text.contains("deprecated=true"));
        if (options.contains("json_name")) assertTrue(text.contains("json_name=\"renamed\""));
        assertNoErrors();
        return null;
      });
    }
  }

  @Test public void should_not_offer_value_only_repairs_for_forbidden_edition_defaults() throws Exception {
    onUi(() -> {
      for (String field : new String[] {"bool enabled=1 [default=\"bad\"]",
          "string text=1 [default=true]"}) {
        open("edition=\"2023\"; option features.field_presence=IMPLICIT; message M { " + field + "; }");
        boolean forbidden = false;
        for (Issue issue : issues()) {
          forbidden |= EDITION_DEFAULT.equals(issue.getCode());
          for (IssueResolution resolution : fixes.getResolutions(issue)) {
            assertFalse(resolution.getLabel(), resolution.getLabel().equals("true"));
            assertFalse(resolution.getLabel(), resolution.getLabel().equals("false"));
            if (!EDITION_DEFAULT.equals(issue.getCode())) {
              assertTrue(issue.toString(), fixes.getResolutions(issue).isEmpty());
            }
          }
        }
        assertTrue("Missing forbidden-default diagnostic", forbidden);
        applyFix(EDITION_DEFAULT, "Remove explicit default");
        assertNoErrors();
      }
      return null;
    });
  }

  @Test public void should_apply_edition_feature_roots_paths_aggregates_and_values() throws Exception {
    onUi(() -> {
      Map<String, ICompletionProposal> choices = complete("edition=\"2023\"; option |={field_presence:EXPLICIT};");
      present(choices, "features");
      absent(choices, "map_entry");
      apply(choices, "features");
      assertNoErrors();
      choices = complete("edition=\"2023\"; option features.|=EXPLICIT;");
      present(choices, "field_presence", "enum_type", "repeated_field_encoding");
      absent(choices, "enforce_naming_style", "default_symbol_visibility", "enforce_proto_limits");
      apply(choices, "field_presence");
      assertNoErrors();
      for (String[] aggregate : new String[][] {{"{", "}"}, {"<", ">"}}) {
        choices = complete("edition=\"2023\"; option features=" + aggregate[0]
            + " |:IMPLICIT " + aggregate[1] + ";");
        apply(choices, "field_presence");
        assertNoErrors();
        choices = complete("edition=\"2023\"; option features=" + aggregate[0]
            + " field_presence:| " + aggregate[1] + ";");
        present(choices, "EXPLICIT", "IMPLICIT");
        absent(choices, "FIELD_PRESENCE_UNKNOWN", "LEGACY_REQUIRED");
        apply(choices, "EXPLICIT");
        assertNoErrors();
      }
      choices = complete("edition=\"2023\"; message M { int32 value=1 [features.field_presence=|]; }");
      present(choices, "EXPLICIT", "IMPLICIT", "LEGACY_REQUIRED");
      absent(choices, "FIELD_PRESENCE_UNKNOWN");
      apply(choices, "LEGACY_REQUIRED");
      assertNoErrors();
      return null;
    });
  }

  @Test public void should_filter_feature_targets_field_kinds_and_assigned_spellings() throws Exception {
    onUi(() -> {
      Map<String, ICompletionProposal> choices = complete("edition=\"2023\"; message M { int32 value=1 [features.|=EXPLICIT]; }");
      present(choices, "field_presence");
      absent(choices, "enum_type", "repeated_field_encoding", "utf8_validation", "message_encoding");
      choices = complete("edition=\"2023\"; message M { repeated int32 values=1 [|]; }");
      present(choices, "features");
      absent(choices, "packed", "default");
      choices = complete("edition=\"2023\"; message M { repeated int32 values=1 [features.|=PACKED]; }");
      present(choices, "repeated_field_encoding");
      absent(choices, "field_presence", "utf8_validation", "message_encoding");
      apply(choices, "repeated_field_encoding");
      assertNoErrors();
      choices = complete("edition=\"2023\"; option features.field_presence=IMPLICIT; "
          + "option features={ |:OPEN };");
      absent(choices, "field_presence");
      apply(choices, "enum_type");
      assertNoErrors();
      choices = complete("edition=\"2023\"; option features={field_presence:IMPLICIT}; "
          + "option features.|=OPEN;");
      absent(choices, "field_presence");
      apply(choices, "enum_type");
      assertNoErrors();
      choices = complete("edition=\"2023\"; option features.field_presence=IMPLICIT; "
          + "message M { int32 value=1 [features.|=EXPLICIT, default=7]; }");
      apply(choices, "field_presence");
      assertNoErrors();
      return null;
    });
  }

  @Test public void should_use_effective_presence_for_default_shorthand_and_typed_values() throws Exception {
    onUi(() -> {
      for (String presence : new String[] {"EXPLICIT", "LEGACY_REQUIRED"}) {
        String prefix = "edition=\"2023\"; message M { bool enabled=1 [features.field_presence=" + presence;
        Map<String, ICompletionProposal> choices = complete(prefix + ", default=|]; }");
        present(choices, "true", "false");
        apply(choices, "true");
        assertNoErrors();
      }
      Map<String, ICompletionProposal> choices = complete("edition=\"2023\"; message M { bool enabled=1 |; }");
      apply(choices, "[default = ]");
      assertTrue(editor.getDocument().get().contains("[default = ]"));
      choices = complete("edition=\"2023\"; message M { bool enabled=1 [default=|]; }");
      apply(choices, "false");
      assertNoErrors();
      choices = complete("edition=\"2023\"; message M { string text=1 [default=|]; }");
      apply(choices, "\"\"");
      assertNoErrors();
      choices = complete("edition=\"2023\"; message M { int32 value=1 [default=|]; }");
      apply(choices, "0");
      assertNoErrors();
      assertTrue(editor.getDocument().get().contains("default=0"));
      choices = complete("edition=\"2023\"; enum E { ZERO=0; FIRST=1; } message M { E value=1 [default=|]; }");
      present(choices, "ZERO", "FIRST");
      apply(choices, "FIRST");
      assertNoErrors();
      for (String body : new String[] {"bool enabled=1 [features.field_presence=IMPLICIT, default=|];",
          "repeated bool enabled=1 [default=|];", "M child=1 [default=|];",
          "oneof choice { bool enabled=1 [default=|]; }"}) {
        choices = complete("edition=\"2023\"; message M { " + body + " }");
        absent(choices, "true", "false", "\"\"");
      }
      for (String body : new String[] {"repeated bool enabled=1 |;", "M child=1 |;",
          "oneof choice { bool enabled=1 |; }"}) {
        choices = complete("edition=\"2023\"; message M { " + body + " }");
        absent(choices, "[default = ]");
      }
      choices = complete("edition=\"2023\"; option features.field_presence=IMPLICIT; message M { bool enabled=1 |; }");
      absent(choices, "[default = ]");
      return null;
    });
  }

  @Test public void should_apply_keyword_identifiers_without_reintroducing_edition_modifiers() throws Exception {
    onUi(() -> {
      for (String year : new String[] {"2023", "2024"}) {
        String prefix = "edition=\"" + year + "\"; ";
        Map<String, ICompletionProposal> choices = complete(prefix + "message M { | }");
        absent(choices, "optional", "required", "group");
        present(choices, "repeated", "map", "extensions", "extend");
        choices = complete(prefix + "service S { | }");
        present(choices, "rpc");
        absent(choices, "stream");
        choices = complete(prefix + "message M { oneof choice { | } }");
        absent(choices, "optional", "required", "repeated", "map", "group");
        for (String name : new String[] {"edition", "export", "local", "optional", "required"}) {
          choices = complete(prefix + "message M { string | = 1; }");
          apply(choices, name);
          assertTrue(editor.getDocument().get().contains("string " + name + " = 1"));
          assertNoErrors();
          choices = complete(prefix + "message M { string " + name.substring(0, 2) + "| = 1; }");
          apply(choices, name);
          assertNoErrors();
        }
      }
      return null;
    });
  }

  @Test public void should_offer_2024_visibility_and_option_imports_only_at_legal_positions() throws Exception {
    onUi(() -> {
      Map<String, ICompletionProposal> choices = complete("edition=\"2023\"; | message M {}");
      absent(choices, "export", "local");
      choices = complete("edition=\"2024\"; | message M {}");
      present(choices, "export", "local");
      apply(choices, "export");
      assertNoErrors();
      choices = complete("edition=\"2024\"; | enum E { ZERO=0; }");
      apply(choices, "local");
      assertNoErrors();
      choices = complete("edition=\"2024\"; message M { | int32 value=1; }");
      absent(choices, "export", "local");
      choices = complete("edition=\"2023\"; import | \"legacy.proto\";");
      present(choices, "weak");
      absent(choices, "option");
      choices = complete("edition=\"2024\"; import | \"google/protobuf/java_features.proto\";");
      present(choices, "option");
      absent(choices, "weak");
      apply(choices, "option");
      assertNoErrors();
      String strict = "edition=\"2024\"; option features.default_symbol_visibility=STRICT; ";
      choices = complete(strict + "message M { | message Nested {} }");
      present(choices, "local");
      absent(choices, "export");
      apply(choices, "local");
      assertNoErrors();
      choices = complete(strict + "message M { | enum E { ZERO=0; } }");
      absent(choices, "export");
      choices = complete(strict + "local message Namespace { reserved 1 to max; | enum E { ZERO=0; } }");
      apply(choices, "export");
      assertNoErrors();
      return null;
    });
  }

  @Test public void should_use_actual_import_modes_and_visibility_in_applied_completion() throws Exception {
    onUi(() -> {
      write("visibility.proto", "edition=\"2024\"; package visible; "
          + "message Public { message Hidden {} export message Exposed {} } local message Private {}");
      for (String dialect : new String[] {"syntax=\"proto2\"", "syntax=\"proto3\"", "edition=\"2024\""}) {
        String modifier = dialect.contains("proto2") ? "optional " : "";
        Map<String, ICompletionProposal> choices = complete(dialect
            + "; import \"visibility.proto\"; message M { " + modifier + "| value=1; }");
        present(choices, "visible.Public", "visible.Public.Exposed");
        absent(choices, "visible.Public.Hidden", "visible.Private");
        apply(choices, "visible.Public.Exposed");
        assertNoErrors();
      }
      write("option-only.proto", "syntax=\"proto2\"; " + DESCRIPTOR_IMPORT
          + "package options; message Payload { optional bool enabled=1; extensions 100 to max; } "
          + "extend google.protobuf.FileOptions { optional Payload settings=50001; }");
      String prefix = "edition=\"2024\"; import option \"option-only.proto\"; ";
      Map<String, ICompletionProposal> choices = complete(prefix + "message M { | value=1; }");
      absent(choices, "options.Payload");
      choices = complete(prefix + "service S { rpc Get(|) returns (options.Payload); }");
      absent(choices, "options.Payload");
      choices = complete(prefix + "extend | { int32 extra=100; }");
      absent(choices, "options.Payload");
      choices = complete(prefix + "option (|)={enabled:true};");
      apply(choices, "options.settings");
      assertNoErrors();
      choices = complete(prefix + "option (options.settings)={ |:true };");
      apply(choices, "enabled");
      assertNoErrors();
      choices = complete("edition=\"2024\"; import \"option-only.proto\"; message M { | value=1; }");
      apply(choices, "options.Payload");
      assertNoErrors();
      choices = complete("edition=\"2024\"; import \"option-only.proto\"; "
          + "service S { rpc Get(|) returns (options.Payload); }");
      apply(choices, "options.Payload");
      assertNoErrors();
      choices = complete("edition=\"2024\"; import \"option-only.proto\"; extend | { int32 extra=100; }");
      apply(choices, "options.Payload");
      assertNoErrors();
      return null;
    });
  }

  @Test public void should_exclude_closed_edition_enums_from_proto3_type_completion() throws Exception {
    onUi(() -> {
      write("edition-enums.proto", "edition=\"2023\"; package editions; "
          + "enum Open { ZERO=0; } enum Closed { option features.enum_type=CLOSED; FIRST=1; }");
      Map<String, ICompletionProposal> choices = complete("syntax=\"proto3\"; import \"edition-enums.proto\"; "
          + "message M { | value=1; }");
      present(choices, "editions.Open");
      absent(choices, "editions.Closed");
      apply(choices, "editions.Open");
      assertNoErrors();
      choices = complete("edition=\"2023\"; import \"edition-enums.proto\"; message M { | value=1; }");
      apply(choices, "editions.Closed");
      assertNoErrors();
      return null;
    });
  }

  @Test public void should_apply_custom_feature_extensions_and_filter_metadata_windows() throws Exception {
    onUi(() -> {
      write("features.proto", featureDefinitions());
      String prefix = "edition=\"2023\"; import \"features.proto\"; ";
      Map<String, ICompletionProposal> choices = complete(prefix + "option features.(|).enabled=true;");
      apply(choices, "feat.custom");
      assertNoErrors();
      choices = complete(prefix + "option features.(feat.custom).|=true;");
      present(choices, "enabled", "removed", "deprecated", "mode");
      absent(choices, "late");
      apply(choices, "enabled");
      assertNoErrors();
      prefix = "edition=\"2024\"; import option \"features.proto\"; ";
      choices = complete(prefix + "option features.(feat.custom).|=true;");
      present(choices, "enabled", "late", "deprecated", "mode");
      absent(choices, "removed");
      apply(choices, "late");
      assertNoErrors();
      choices = complete(prefix + "option features={ [|]:{enabled:true} };");
      apply(choices, "feat.custom");
      assertNoErrors();
      choices = complete(prefix + "option features={ [feat.custom]:{ |:true } };");
      present(choices, "enabled", "late", "deprecated");
      absent(choices, "removed");
      apply(choices, "deprecated");
      assertNoErrors();
      choices = complete(prefix + "option features.(feat.custom).mode=|;");
      present(choices, "MODE_ZERO");
      absent(choices, "MODE_RETIRED");
      apply(choices, "MODE_ZERO");
      assertNoErrors();
      choices = complete(prefix + "message M { int32 value=1 [features.(feat.custom).|=true]; }");
      present(choices, "enabled");
      absent(choices, "late", "removed", "deprecated", "mode");
      apply(choices, "enabled");
      assertNoErrors();
      return null;
    });
  }

  @Test public void should_apply_java_feature_values_with_targets_and_support_windows() throws Exception {
    onUi(() -> {
      String prefix = "edition=\"2023\"; import \"google/protobuf/java_features.proto\"; ";
      Map<String, ICompletionProposal> choices = complete(prefix + "option features.(pb.java).|=true;");
      absent(choices, "large_enum", "nest_in_file_class");
      choices = complete(prefix + "option | = true;");
      present(choices, "java_multiple_files");
      apply(choices, "java_multiple_files");
      assertNoErrors();
      prefix = "edition=\"2024\"; import option \"google/protobuf/java_features.proto\"; ";
      choices = complete(prefix + "option | = true;");
      absent(choices, "java_multiple_files");
      choices = complete(prefix + "option features.(pb.java).|=true;");
      present(choices, "large_enum");
      absent(choices, "use_old_outer_classname_default");
      apply(choices, "large_enum");
      assertNoErrors();
      choices = complete(prefix + "message M { option features.(pb.java).nest_in_file_class=|; }");
      present(choices, "YES", "NO");
      absent(choices, "LEGACY", "NEST_IN_FILE_CLASS_UNKNOWN", "1");
      apply(choices, "YES");
      assertNoErrors();
      choices = complete(prefix + "message M { message Nested { option features.(pb.java).|=YES; } }");
      absent(choices, "nest_in_file_class");
      choices = complete(prefix + "option features.(pb.java).utf8_validation=|;");
      present(choices, "VERIFY");
      apply(choices, "VERIFY");
      assertNoErrors();
      return null;
    });
  }

  private static String featureDefinitions() {
    return "syntax=\"proto2\"; " + DESCRIPTOR_IMPORT + "package feat; "
        + "extend google.protobuf.FeatureSet { optional Payload custom=9995; } message Payload { "
        + "optional bool enabled=1 [targets=TARGET_TYPE_FILE, targets=TARGET_TYPE_FIELD, "
        + "feature_support={edition_introduced:EDITION_2023}, "
        + "edition_defaults={edition:EDITION_LEGACY value:\"true\"}]; "
        + "optional bool late=2 [targets=TARGET_TYPE_FILE, feature_support={edition_introduced:EDITION_2024}, "
        + "edition_defaults={edition:EDITION_LEGACY value:\"false\"}]; "
        + "optional bool removed=3 [targets=TARGET_TYPE_FILE, feature_support={edition_introduced:EDITION_2023 "
        + "edition_removed:EDITION_2024 removal_error:\"Use enabled\"}, "
        + "edition_defaults={edition:EDITION_LEGACY value:\"true\"}, "
        + "edition_defaults={edition:EDITION_2024 value:\"false\"}]; "
        + "optional bool deprecated=4 [targets=TARGET_TYPE_FILE, feature_support={edition_introduced:EDITION_2023 "
        + "edition_deprecated:EDITION_2024 deprecation_warning:\"Prefer enabled\"}, "
        + "edition_defaults={edition:EDITION_LEGACY value:\"false\"}]; "
        + "enum Mode { MODE_ZERO=0; MODE_RETIRED=1 [feature_support={edition_introduced:EDITION_2023 "
        + "edition_removed:EDITION_2024 removal_error:\"Use MODE_ZERO\"}]; } "
        + "optional Mode mode=5 [targets=TARGET_TYPE_FILE, feature_support={edition_introduced:EDITION_2023}, "
        + "edition_defaults={edition:EDITION_LEGACY value:\"MODE_ZERO\"}]; }";
  }

  private Map<String, ICompletionProposal> complete(String marked) throws Exception {
    offset = marked.indexOf('|');
    assertTrue("Missing completion marker", offset >= 0);
    open(marked.substring(0, offset) + marked.substring(offset + 1));
    editor.getInternalSourceViewer().setSelectedRange(offset, 0);
    Map<String, ICompletionProposal> result = new LinkedHashMap<>();
    editor.getDocument().readOnly(resource -> {
      for (ContentAssistContext context : contexts.create(editor.getInternalSourceViewer(), offset, resource)) {
        proposals.createProposals(context, new ICompletionProposalAcceptor() {
          @Override public void accept(ICompletionProposal proposal) {
            if (proposal instanceof ConfigurableCompletionProposal) {
              result.put(((ConfigurableCompletionProposal) proposal).getReplacementString(), proposal);
            }
          }
          @Override public boolean canAcceptMoreProposals() { return true; }
        });
      }
      return null;
    });
    return result;
  }

  private void open(String text) throws Exception {
    if (editor == null) {
      IFile file = write("assistance.proto", text);
      editor = (XtextEditor) IDE.openEditor(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(), file);
    } else {
      editor.getDocument().set(text);
    }
  }

  private void apply(Map<String, ICompletionProposal> choices, String text) {
    present(choices, text);
    choices.get(text).apply(editor.getDocument());
  }

  private void applyFix(String code, String labelPart) {
    for (Issue issue : issues()) {
      if (code.equals(issue.getCode())) {
        for (IssueResolution resolution : fixes.getResolutions(issue)) {
          if (resolution.getLabel().contains(labelPart)) {
            resolution.apply();
            return;
          }
        }
      }
    }
    fail("Missing quickfix " + code + " / " + labelPart + ": " + issues());
  }

  private List<Issue> issues() {
    return editor.getDocument().readOnly(resource -> validator.validate(resource, CheckMode.ALL, CancelIndicator.NullImpl));
  }

  private void assertNoErrors() {
    for (Issue issue : issues()) {
      assertFalse(issue.toString(), issue.getSeverity() == Severity.ERROR);
    }
  }

  private IFile write(String name, String text) throws Exception {
    IFile file = project.getFile(name);
    file.create(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), true, null);
    return file;
  }

  private static void present(Map<String, ICompletionProposal> choices, String... expected) {
    for (String text : expected) {
      assertTrue("Missing " + text + " in " + choices.keySet(), choices.containsKey(text));
    }
  }

  private static void absent(Map<String, ICompletionProposal> choices, String... unexpected) {
    for (String text : unexpected) {
      assertFalse("Unexpected " + text + " in " + choices.keySet(), choices.containsKey(text));
    }
  }

  private static <T> T onUi(Callable<T> action) throws Exception {
    if (Display.getCurrent() != null) {
      return action.call();
    }
    FutureTask<T> task = new FutureTask<>(action);
    Display.getDefault().syncExec(task);
    return task.get();
  }
}
