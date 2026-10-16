/*
 * Copyright (c) 2026.
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.model.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.eclipse.protobuf.protobuf.ProtobufFactory;
import com.google.eclipse.protobuf.protobuf.StringLiteral;

import org.junit.Test;

public class StringLiterals_Test {
  private final StringLiterals strings = new StringLiterals();

  @Test public void should_decode_c_escapes_and_concatenation_without_changing_chunks() {
    String first = "\"\\a\\b\\f\\n\\r\\t\\v\\\\\\?\\'\\\"\"";
    StringLiteral value = literal(first, "'\\x4a\\X4\\101\\12\\1Z'");
    assertArrayEquals(new byte[] {7, 8, 12, 10, 13, 9, 11, 92, 63, 39, 34,
        74, 4, 65, 10, 1, 90}, strings.getCombinedBytes(value));
    assertEquals(first, value.getChunks().get(0));
  }

  @Test public void should_decode_unicode_scalars_and_surrogate_escape_pairs() {
    StringLiteral value = literal("\"\\u00e9\\U0001f600\\uD83D\\uDE00\"", "'é😀'");
    assertEquals("é😀😀é😀", strings.getCombinedString(value));
    assertNull(strings.errorOf(value, true));
  }

  @Test public void should_validate_utf8_after_combining_all_chunks() {
    StringLiteral value = literal("'\\xc3'", "'\\xa9'");
    assertEquals("é", strings.getCombinedString(value));
  }

  @Test public void should_allow_non_utf8_bytes_but_not_text() {
    StringLiteral value = literal("'\\xff\\000'");
    assertArrayEquals(new byte[] {(byte) 255, 0}, strings.getCombinedBytes(value));
    assertNull(strings.errorOf(value, false));
    assertNotNull(strings.errorOf(value, true));
    assertNull(strings.getCombinedString(value));
    for (String raw : new String[] {"'\\xc0\\x80'", "'\\xed\\xa0\\x80'", "'\\xf4\\x90\\x80\\x80'"}) {
      assertNotNull(strings.getCombinedBytes(literal(raw)));
      assertNull(strings.getCombinedString(literal(raw)));
    }
  }

  @Test public void should_reject_invalid_escapes_scalars_and_raw_characters_without_throwing() {
    for (String raw : new String[] {"'\\q'", "'\\x'", "'\\u123'", "'\\uD800'", "'\\uDC00'",
        "'\\U00110000'", "'\\Uffffffff'", "'a\nb'", "'a\rb'", "'a\0b'", "'a'b'", "'\\'", "'unterminated"}) {
      StringLiteral value = literal(raw);
      assertNull(strings.getCombinedBytes(value));
      assertNull(strings.getCombinedString(value));
      assertNotNull(strings.errorOf(value, false));
      assertNotNull(strings.errorOf(value, true));
    }
  }

  @Test public void should_distinguish_empty_quoted_text_from_incomplete_literals() {
    assertEquals("", strings.getCombinedString(literal("''", "\"\"")));
    assertNull(strings.getCombinedString(null));
    assertNull(strings.getCombinedBytes(literal()));
    assertNotNull(strings.errorOf(literal(), false));
  }

  @Test public void should_consume_at_most_two_hex_or_three_octal_digits() {
    assertArrayEquals(new byte[] {18, 51, (byte) 255, 55, (byte) 255},
        strings.getCombinedBytes(literal("'\\x123\\3777\\777'")));
  }

  private StringLiteral literal(String... chunks) {
    StringLiteral value = ProtobufFactory.eINSTANCE.createStringLiteral();
    for (String chunk : chunks) value.getChunks().add(chunk);
    return value;
  }
}
