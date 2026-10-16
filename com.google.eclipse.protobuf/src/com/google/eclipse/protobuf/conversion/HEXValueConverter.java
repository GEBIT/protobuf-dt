/*
 * Copyright (c) 2011 Google Inc.
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.conversion;

import java.math.BigInteger;
import java.util.regex.Pattern;

import org.eclipse.xtext.conversion.ValueConverterException;
import org.eclipse.xtext.conversion.impl.AbstractValueConverter;
import org.eclipse.xtext.nodemodel.INode;

/** Exact protobuf hexadecimal integer constants. */
public class HEXValueConverter extends AbstractValueConverter<BigInteger> {
  private static final Pattern INTEGER = Pattern.compile("-?0[xX][0-9a-fA-F]+");

  @Override public BigInteger toValue(String string, INode node) throws ValueConverterException {
    if (string == null || string.isEmpty()) {
      throw new ValueConverterException("Couldn't convert empty string to long.", node, null);
    }
    try {
      String text = NumericTokens.text(string, node);
      if (!INTEGER.matcher(text).matches()) {
        throw new NumberFormatException("Invalid hexadecimal spelling.");
      }
      boolean negative = text.charAt(0) == '-';
      BigInteger magnitude = new BigInteger(text.substring(negative ? 3 : 2), 16);
      return negative ? magnitude.negate() : magnitude;
    } catch (NumberFormatException e) {
      throw new ValueConverterException("Couldn't convert '" + string + "' to long.", node, e);
    }
  }

  @Override public String toString(BigInteger value) {
    return (value.signum() < 0 ? "-0x" : "0x") + value.abs().toString(16);
  }
}
