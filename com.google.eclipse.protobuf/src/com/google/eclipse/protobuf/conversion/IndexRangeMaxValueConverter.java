package com.google.eclipse.protobuf.conversion;

import org.eclipse.xtext.conversion.ValueConverterException;
import org.eclipse.xtext.conversion.impl.AbstractValueConverter;
import org.eclipse.xtext.nodemodel.INode;

import com.google.inject.Inject;

/** Range endpoints retain their spelling but must satisfy the same integer contract as indices. */
public class IndexRangeMaxValueConverter extends AbstractValueConverter<String> {
  @Inject private IndexNumberValueConverter indices;

  @Override public String toValue(String string, INode node) throws ValueConverterException {
    final String text;
    try {
      text = NumericTokens.text(string, node);
    } catch (NumberFormatException exception) {
      throw new ValueConverterException("Invalid integer.", node, exception);
    }
    if (!"max".equals(text)) {
      try {
        indices.toValue(text, null);
      } catch (ValueConverterException exception) {
        throw new ValueConverterException(exception.getMessage(), node, exception);
      }
    }
    return text;
  }

  @Override public String toString(String value) {
    return toValue(value, null);
  }
}
