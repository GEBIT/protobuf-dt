package com.google.eclipse.protobuf.ui.editor.syntaxcoloring;

import static com.google.eclipse.protobuf.junit.core.XtextRule.createWith;
import static org.eclipse.xtext.ui.editor.syntaxcoloring.DefaultHighlightingConfiguration.KEYWORD_ID;
import static org.eclipse.xtext.ui.editor.syntaxcoloring.DefaultHighlightingConfiguration.NUMBER_ID;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.junit.Rule;
import org.junit.Test;

import com.google.eclipse.protobuf.junit.core.XtextRule;
import com.google.eclipse.protobuf.ui.plugin.ProtobufEditorPlugIn;
import com.google.inject.Inject;

public class ProtobufSemanticHighlightingCalculator_Test {
  @Rule public XtextRule xtext = createWith(ProtobufEditorPlugIn.injector());
  @Inject private ProtobufSemanticHighlightingCalculator calculator;
  @Inject private IResourceValidator validator;

  @Test public void should_highlight_only_real_type_and_scalar_reference_spans_recursively() {
    String source = "syntax = \"proto2\"; package highlight; "
        + "import \"google/protobuf/descriptor.proto\"; import \"google/protobuf/any.proto\"; "
        + "message Payload { optional int32 id = 1; } enum Mode { true = 0; } "
        + "message Settings { optional Mode mode = 1; optional bool enabled = 2; "
        + "optional double weight = 3; map<string, google.protobuf.Any> entries = 4; } "
        + "extend google.protobuf.FileOptions { optional Settings settings = 50001; } "
        + "extend google.protobuf.OneofOptions { optional bool note = 50002; } "
        + "option (settings) = { mode: true enabled: t weight: Infinity entries: { "
        + "key: \"payload\" value: { [example.test/a.b/highlight.Payload] { id: 7 } } } }; "
        + "message Envelope { map<string, Payload> payloads = 1; "
        + "oneof content { option (note) = true; Payload selected = 2; } "
        + "optional Mode state = 3 [default = true, deprecated = true]; }";
    List<Position> positions = highlight(source);
    assertSpan(positions, source, "map<string, Payload>", "Payload", HighlightingConfiguration.MESSAGE_ID);
    assertSpan(positions, source, "Payload selected", "Payload", HighlightingConfiguration.MESSAGE_ID);
    assertSpan(positions, source, "selected = 2", "2", HighlightingConfiguration.MESSAGE_FIELD_INDEX_ID);
    assertSpan(positions, source, "mode: true", "true", HighlightingConfiguration.ENUM_LITERAL_ID);
    assertSpan(positions, source, "enabled: t", "t", KEYWORD_ID);
    assertSpan(positions, source, "weight: Infinity", "Infinity", NUMBER_ID);
    assertSpan(positions, source, "(note) = true", "true", KEYWORD_ID);
    assertSpan(positions, source, "default = true", "true", HighlightingConfiguration.ENUM_LITERAL_ID);
    assertSpan(positions, source, "deprecated = true", "true", KEYWORD_ID);
    assertSpan(positions, source, "example.test/a.b/highlight.Payload", "highlight.Payload",
        HighlightingConfiguration.MESSAGE_ID);
    int prefix = source.indexOf("example.test/a.b/");
    for (Position position : positions) {
      assertFalse("URL prefix must not be styled as a message reference",
          position.styles.contains(HighlightingConfiguration.MESSAGE_ID)
              && position.offset < prefix + "example.test/a.b/".length()
              && position.offset + position.length > prefix);
    }
  }

  @Test public void should_highlight_range_and_enum_value_metadata_without_skipping_later_options() {
    String source = "syntax = \"proto2\"; import \"google/protobuf/descriptor.proto\"; "
        + "message Ext { extensions 100 to 200 [verification = DECLARATION, "
        + "declaration = { number: 100 full_name: \".highlight.ext\" type: \"int32\" }]; } "
        + "enum Mode { ZERO = 0 [feature_support = { edition_introduced: EDITION_PROTO2 }, deprecated = true]; } "
        + "message Owner { optional int32 value = 1 [retention = RETENTION_RUNTIME, deprecated = true]; }";
    List<Position> positions = highlight(source);
    assertSpan(positions, source, "verification = DECLARATION", "DECLARATION", HighlightingConfiguration.ENUM_LITERAL_ID);
    assertSpan(positions, source, "edition_introduced: EDITION_PROTO2", "EDITION_PROTO2", HighlightingConfiguration.ENUM_LITERAL_ID);
    assertSpan(positions, source, "retention = RETENTION_RUNTIME", "RETENTION_RUNTIME", HighlightingConfiguration.ENUM_LITERAL_ID);
    int cursor = 0;
    while ((cursor = source.indexOf("deprecated = true", cursor)) >= 0) {
      assertPosition(positions, cursor + "deprecated = ".length(), 4, KEYWORD_ID);
      cursor++;
    }
  }

  private List<Position> highlight(String source) {
    xtext.parseText(source);
    java.util.List<org.eclipse.xtext.validation.Issue> issues =
        validator.validate(xtext.resource(), CheckMode.ALL, CancelIndicator.NullImpl);
    assertTrue(issues.toString(), issues.isEmpty());
    List<Position> positions = new ArrayList<>();
    calculator.provideHighlightingFor(xtext.resource(),
        (offset, length, styles) -> positions.add(new Position(offset, length, styles)), CancelIndicator.NullImpl);
    return positions;
  }

  private void assertSpan(List<Position> positions, String source, String context, String token, String style) {
    int start = source.indexOf(context);
    assertTrue(context, start >= 0);
    assertPosition(positions, start + context.lastIndexOf(token), token.length(), style);
  }

  private void assertPosition(List<Position> positions, int offset, int length, String style) {
    for (Position position : positions) {
      if (position.offset == offset && position.length == length && position.styles.contains(style)) return;
    }
    fail("Missing " + style + " at " + offset + ":" + length);
  }

  private static class Position {
    final int offset;
    final int length;
    final List<String> styles;
    Position(int offset, int length, String[] styles) {
      this.offset = offset;
      this.length = length;
      this.styles = Arrays.asList(styles);
    }
  }
}
