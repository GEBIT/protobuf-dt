package com.google.eclipse.protobuf.conversion;

import java.math.BigInteger;

import org.eclipse.xtext.conversion.ValueConverterException;
import org.eclipse.xtext.conversion.impl.AbstractValueConverter;
import org.eclipse.xtext.nodemodel.INode;

import com.google.inject.Inject;

/** Long-valued indices, distinct from arbitrary-precision option constants. */
public class IndexNumberValueConverter extends AbstractValueConverter<Long> {
  private static final BigInteger MIN_VALUE = BigInteger.valueOf(Long.MIN_VALUE);
  private static final BigInteger MAX_VALUE = BigInteger.valueOf(Long.MAX_VALUE);

  @Inject private LONGValueConverter decimal;
  @Inject private HEXValueConverter hexadecimal;

  @Override public Long toValue(String string, INode node) throws ValueConverterException {
    String text;
    try {
      text = NumericTokens.text(string, node);
    } catch (NumberFormatException e) {
      throw new ValueConverterException("Invalid integer.", node, e);
    }
    int start = text != null && text.startsWith("-") ? 1 : 0;
    boolean hex = text != null && (text.startsWith("0x", start) || text.startsWith("0X", start));
    BigInteger value = hex ? hexadecimal.toValue(string, node) : decimal.toValue(string, node);
    if (value.compareTo(MIN_VALUE) < 0 || value.compareTo(MAX_VALUE) > 0) {
      throw new ValueConverterException("Integer out of range.", node,
          new ArithmeticException("Integer does not fit in a signed long."));
    }
    return value.longValue();
  }

  @Override public String toString(Long value) {
    return value.toString();
  }
}
