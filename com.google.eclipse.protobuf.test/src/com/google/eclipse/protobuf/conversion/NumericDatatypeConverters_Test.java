package com.google.eclipse.protobuf.conversion;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static com.google.eclipse.protobuf.junit.core.UnitTestModule.unitTestModule;
import static com.google.eclipse.protobuf.junit.core.XtextRule.overrideRuntimeModuleWith;

import java.math.BigInteger;
import java.io.StringReader;

import org.eclipse.xtext.conversion.ValueConverterException;
import org.eclipse.xtext.parser.IParser;
import org.eclipse.xtext.parser.IParseResult;
import org.eclipse.xtext.EcoreUtil2;
import com.google.eclipse.protobuf.protobuf.LongLink;
import com.google.eclipse.protobuf.protobuf.HexNumberLink;
import com.google.eclipse.protobuf.protobuf.DoubleLink;
import org.junit.Rule;
import org.junit.Test;

import com.google.eclipse.protobuf.junit.core.XtextRule;

public class NumericDatatypeConverters_Test {
  @Rule public XtextRule xtext = overrideRuntimeModuleWith(unitTestModule());

  @Test public void should_decode_index_radices_and_signed_long_boundaries() {
    IndexNumberValueConverter converter = xtext.injector().getInstance(IndexNumberValueConverter.class);
    assertEquals(Long.valueOf(8), converter.toValue("010", null));
    assertEquals(Long.valueOf(Long.MAX_VALUE), converter.toValue("0x7fffffffffffffff", null));
    assertEquals(Long.valueOf(Long.MIN_VALUE), converter.toValue("-9223372036854775808", null));
  }

  @Test public void should_reject_index_overflow_without_wrapping() {
    IndexNumberValueConverter converter = xtext.injector().getInstance(IndexNumberValueConverter.class);
    for (String input : asList("9223372036854775808", "-9223372036854775809", "0xffffffffffffffff")) {
      try {
        converter.toValue(input, null);
        org.junit.Assert.fail("Accepted overflowing index " + input);
      } catch (ValueConverterException expected) {
      }
    }
  }

  @Test public void should_omit_only_hidden_tokens_between_minus_and_magnitude() {
    IParseResult result = parse("option (custom) = - /* comment */ 010;");
    assertTrue(!result.hasSyntaxErrors());
    assertEquals(BigInteger.valueOf(-8), EcoreUtil2.getAllContentsOfType(
        result.getRootASTElement(), LongLink.class).get(0).getTarget());
  }

  @Test public void should_normalize_hexadecimal_sign_and_preserve_uint64_magnitude() {
    IParseResult result = parse("option (custom) = -/* gap */0xffffffffffffffff;");
    assertTrue(!result.hasSyntaxErrors());
    assertEquals(new BigInteger("-18446744073709551615"), EcoreUtil2.getAllContentsOfType(
        result.getRootASTElement(), HexNumberLink.class).get(0).getTarget());
  }

  @Test(expected = ValueConverterException.class)
  public void should_not_reinterpret_invalid_octal_index() {
    xtext.injector().getInstance(IndexNumberValueConverter.class).toValue("09", null);
  }

  @Test public void should_normalize_floating_sign_without_stripping_numeric_token() {
    IParseResult result = parse("option (custom) = { weight: -// comment\n1.5f };");
    assertTrue(!result.hasSyntaxErrors());
    assertEquals(-1.5, EcoreUtil2.getAllContentsOfType(
        result.getRootASTElement(), DoubleLink.class).get(0).getTarget(), 0);
  }

  @Test public void should_reject_separate_magnitudes_and_split_float_tokens() {
    assertTrue(parse("option (custom) = 1 2;").hasSyntaxErrors());
    assertTrue(parse("option (custom) = 1 . 5;").hasSyntaxErrors());
  }

  @Test public void should_reject_malformed_or_overflowing_range_endpoints() {
    for (String endpoint : asList("09", "9223372036854775808", "0xffffffffffffffff")) {
      assertTrue(parse("message M { reserved 1 to " + endpoint + "; }").hasSyntaxErrors());
    }
  }

  @Test public void should_retain_valid_range_radices_and_separated_sign() {
    IParseResult result = parse("enum E { reserved -2 to - /* gap */ 01, 010 to 0x10, 100 to max; }");
    assertTrue(!result.hasSyntaxErrors());
    java.util.List<com.google.eclipse.protobuf.protobuf.IndexRange> ranges = EcoreUtil2.getAllContentsOfType(
        result.getRootASTElement(), com.google.eclipse.protobuf.protobuf.IndexRange.class);
    assertEquals("-01", ranges.get(0).getTo());
    assertEquals("0x10", ranges.get(1).getTo());
    assertEquals("max", ranges.get(2).getTo());
  }

  @Test(expected = ValueConverterException.class)
  public void should_not_accept_leading_plus() {
    xtext.injector().getInstance(LONGValueConverter.class).toValue("+1", null);
  }

  private IParseResult parse(String source) {
    return xtext.injector().getInstance(IParser.class).parse(new StringReader(source));
  }
}
