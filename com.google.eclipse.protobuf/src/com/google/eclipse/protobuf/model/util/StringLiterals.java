/*
 * Copyright (c) 2014 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.model.util;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import com.google.eclipse.protobuf.protobuf.StringLiteral;
import com.google.inject.Singleton;

/** Helper methods for working with quoted protobuf string chunks. */
@Singleton public class StringLiterals {
  /** Returns decoded bytes, or null for an incomplete or malformed literal. */
  public byte[] getCombinedBytes(StringLiteral literal) {
    Decoded decoded = decode(literal);
    return decoded.error == null ? decoded.bytes : null;
  }

  /** Returns strict UTF-8 text, or null rather than replacing invalid data. */
  public String getCombinedString(StringLiteral literal) {
    byte[] bytes = getCombinedBytes(literal);
    return bytes == null ? null : utf8(bytes);
  }

  /** Returns a representation error, or null when valid for the requested context. */
  public String errorOf(StringLiteral literal, boolean requireUtf8) {
    Decoded decoded = decode(literal);
    if (decoded.error != null) {
      return decoded.error;
    }
    return requireUtf8 && utf8(decoded.bytes) == null ? "String literal is not valid UTF-8." : null;
  }

  private String utf8(byte[] bytes) {
    try {
      return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    } catch (CharacterCodingException e) {
      return null;
    }
  }

  private Decoded decode(StringLiteral literal) {
    if (literal == null || literal.getChunks().isEmpty()) {
      return new Decoded("Incomplete string literal.");
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (String chunk : literal.getChunks()) {
      if (chunk == null || chunk.length() < 2) {
        return new Decoded("Incomplete string literal.");
      }
      char quote = chunk.charAt(0);
      int end = chunk.length() - 1;
      if ((quote != '\'' && quote != '"') || chunk.charAt(end) != quote) {
        return new Decoded("Incomplete string literal.");
      }
      for (int i = 1; i < end; i++) {
        char c = chunk.charAt(i);
        if (c == 0 || c == '\n' || c == '\r' || c == quote) {
          return new Decoded("Invalid unescaped character in string literal.");
        }
        int point = c;
        if (c == '\\') {
          if (++i >= end) {
            return new Decoded("Incomplete escape sequence.");
          }
          c = chunk.charAt(i);
          switch (c) {
            case 'a': out.write(7); continue;
            case 'b': out.write(8); continue;
            case 'f': out.write(12); continue;
            case 'n': out.write(10); continue;
            case 'r': out.write(13); continue;
            case 't': out.write(9); continue;
            case 'v': out.write(11); continue;
            case '\\': case '\'': case '"': case '?': out.write(c); continue;
            default: break;
          }
          if (c >= '0' && c <= '7') {
            int value = c - '0';
            for (int digits = 1; digits < 3 && i + 1 < end; digits++) {
              char next = chunk.charAt(i + 1);
              if (next < '0' || next > '7') break;
              value = value * 8 + chunk.charAt(++i) - '0';
            }
            // The protobuf tokenizer writes octal escapes as a single byte.
            out.write(value);
            continue;
          }
          if (c == 'x' || c == 'X') {
            if (i + 1 >= end || hex(chunk.charAt(i + 1)) < 0) {
              return new Decoded("Hex escape requires a hexadecimal digit.");
            }
            int value = hex(chunk.charAt(++i));
            if (i + 1 < end && hex(chunk.charAt(i + 1)) >= 0) {
              value = value * 16 + hex(chunk.charAt(++i));
            }
            out.write(value);
            continue;
          }
          if (c != 'u' && c != 'U') {
            return new Decoded("Invalid escape sequence.");
          }
          int digits = c == 'u' ? 4 : 8;
          long value = unicode(chunk, i + 1, digits, end);
          if (value < 0 || value > Character.MAX_CODE_POINT) {
            return new Decoded("Invalid Unicode escape.");
          }
          i += digits;
          point = (int) value;
          // Like the tokenizer, accept an immediately adjacent UTF-16 escape pair.
          if (Character.isHighSurrogate((char) point) && point <= 0xffff
              && i + 6 < end && chunk.charAt(i + 1) == '\\' && chunk.charAt(i + 2) == 'u') {
            long trail = unicode(chunk, i + 3, 4, end);
            if (trail >= 0xdc00 && trail <= 0xdfff) {
              point = Character.toCodePoint((char) point, (char) trail);
              i += 6;
            }
          }
        } else if (Character.isHighSurrogate(c) && i + 1 < end
            && Character.isLowSurrogate(chunk.charAt(i + 1))) {
          point = Character.toCodePoint(c, chunk.charAt(++i));
        }
        if (point >= 0xd800 && point <= 0xdfff) {
          return new Decoded("String literal contains an invalid Unicode scalar.");
        }
        appendUtf8(out, point);
      }
    }
    return new Decoded(out.toByteArray());
  }

  private int hex(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
  }

  private long unicode(String chunk, int start, int digits, int end) {
    if (start + digits > end) return -1;
    long value = 0;
    for (int i = start; i < start + digits; i++) {
      int digit = hex(chunk.charAt(i));
      if (digit < 0) return -1;
      value = value * 16 + digit;
    }
    return value;
  }

  private void appendUtf8(ByteArrayOutputStream out, int point) {
    if (point < 0x80) {
      out.write(point);
    } else if (point < 0x800) {
      out.write(0xc0 | point >> 6);
      out.write(0x80 | point & 0x3f);
    } else if (point < 0x10000) {
      out.write(0xe0 | point >> 12);
      out.write(0x80 | point >> 6 & 0x3f);
      out.write(0x80 | point & 0x3f);
    } else {
      out.write(0xf0 | point >> 18);
      out.write(0x80 | point >> 12 & 0x3f);
      out.write(0x80 | point >> 6 & 0x3f);
      out.write(0x80 | point & 0x3f);
    }
  }

  private static final class Decoded {
    final byte[] bytes;
    final String error;

    Decoded(byte[] bytes) {
      this.bytes = bytes;
      this.error = null;
    }

    Decoded(String error) {
      this.bytes = null;
      this.error = error;
    }
  }
}
