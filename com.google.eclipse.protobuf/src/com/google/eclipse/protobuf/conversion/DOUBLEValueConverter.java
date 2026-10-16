/*
 * Copyright (c) 2011 Google Inc.
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.conversion;

import java.util.regex.Pattern;

import org.eclipse.xtext.conversion.ValueConverterException;
import org.eclipse.xtext.conversion.impl.AbstractValueConverter;
import org.eclipse.xtext.nodemodel.INode;

/** Numeric floating constants; named special values are literal references. */
public class DOUBLEValueConverter extends AbstractValueConverter<Double> {
  private static final Pattern NUMBER = Pattern.compile(
      "-?(?:(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?)[fF]?");

  @Override public Double toValue(String string, INode node) throws ValueConverterException {
    if (string == null || string.isEmpty()) {
      throw new ValueConverterException("Couldn't convert empty string to double.", node, null);
    }
    try {
      String text = NumericTokens.text(string, node);
      if (!NUMBER.matcher(text).matches()) {
        throw new NumberFormatException("Invalid numeric floating spelling.");
      }
      return Double.parseDouble(text);
    } catch (NumberFormatException e) {
      throw new ValueConverterException("Couldn't convert '" + string + "' to double.", node, e);
    }
  }

  @Override public String toString(Double value) {
    return value.toString();
  }
}
