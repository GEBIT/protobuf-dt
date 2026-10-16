/*
 * Copyright (c) 2015 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.model.util;

import java.math.BigInteger;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.naming.IQualifiedNameProvider;
import org.eclipse.xtext.naming.QualifiedName;

import com.google.eclipse.protobuf.protobuf.BuiltinLiteral;
import com.google.eclipse.protobuf.protobuf.LiteralLink;
import com.google.eclipse.protobuf.protobuf.NativeOption;
import com.google.eclipse.protobuf.protobuf.ReservedEnum;
import com.google.eclipse.protobuf.protobuf.Extensions;
import com.google.common.collect.Range;
import com.google.eclipse.protobuf.protobuf.IndexRange;
import com.google.eclipse.protobuf.services.ProtobufGrammarAccess;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Utility methods related to <code>{@link IndexRange}</code>.
 *
 * @author jogl@google.com (John Glassmyer)
 */
@Singleton public class IndexRanges {
  public static final long MAX_FIELD_NUMBER = 536870911L;
  public static final long MAX_MESSAGE_SET_EXTENSION_NUMBER = Integer.MAX_VALUE - 1L;
  public static final long FIRST_RESERVED_NUMBER = 19000L;
  public static final long LAST_RESERVED_NUMBER = 19999L;
  private static final BigInteger MIN_LONG = BigInteger.valueOf(Long.MIN_VALUE);
  private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);

  @Inject private IQualifiedNameProvider qualifiedNames;
  /**
   * Thrown to indicate that a range's end number is less than its start number.
   */
  public static class BackwardsRangeException extends Exception {
    private static final long serialVersionUID = 1L;
  }

  private ProtobufGrammarAccess protobufGrammarAccess;

  @Inject
  IndexRanges(ProtobufGrammarAccess protobufGrammarAccess) {
    this.protobufGrammarAccess = protobufGrammarAccess;
  }

  /**
   * @throws BackwardsRangeException if the end number is less than the start number
   */
  public Range<Long> toLongRange(IndexRange indexRange) throws BackwardsRangeException {
    long from = indexRange.getFrom();
    String text = indexRange.getTo();
    long to = from;
    if (getMaxKeyword().equals(text)) {
      to = maximumFor(indexRange);
    } else if (text != null) {
      try {
        int sign = text.startsWith("-") ? 1 : 0;
        String magnitude = text.substring(sign);
        int radix = magnitude.startsWith("0x") || magnitude.startsWith("0X") ? 16
            : magnitude.startsWith("0") ? 8 : 10;
        if (radix == 16) {
          magnitude = magnitude.substring(2);
        }
        BigInteger number = new BigInteger((sign == 1 ? "-" : "") + magnitude, radix);
        to = number.compareTo(MIN_LONG) < 0 ? Long.MIN_VALUE
            : number.compareTo(MAX_LONG) > 0 ? Long.MAX_VALUE : number.longValue();
      } catch (NumberFormatException e) {
        // The parser/converter owns incomplete or malformed integer diagnostics.
        return Range.singleton(from);
      }
    }
    if (to < from) {
      throw new BackwardsRangeException();
    }
    return Range.closed(from, to);
  }

  public long maximumFor(IndexRange range) {
    if (range.eContainer() instanceof ReservedEnum) {
      return Integer.MAX_VALUE;
    }
    EObject owner = range.eContainer();
    if (owner instanceof Extensions && isMessageSet(owner.eContainer())) {
      return MAX_MESSAGE_SET_EXTENSION_NUMBER;
    }
    return MAX_FIELD_NUMBER;
  }

  public boolean isMessageSet(EObject owner) {
    if (owner == null) {
      return false;
    }
    for (EObject element : owner.eContents()) {
      if (!(element instanceof NativeOption)) {
        continue;
      }
      NativeOption option = (NativeOption) element;
      if (option.getSource() == null || option.getSource().getTarget() == null
          || option.getSource().getTarget().eIsProxy() || !option.getFields().isEmpty()
          || !(option.getValue() instanceof LiteralLink)) {
        continue;
      }
      QualifiedName name = qualifiedNames.getFullyQualifiedName(option.getSource().getTarget());
      if (name == null || !("google.protobuf.MessageOptions.message_set_wire_format".equals(name.toString())
          || "proto2.MessageOptions.message_set_wire_format".equals(name.toString()))) {
        continue;
      }
      LiteralLink value = (LiteralLink) option.getValue();
      if (!value.isIsNegative() && value.getTarget() instanceof BuiltinLiteral
          && ((BuiltinLiteral) value.getTarget()).isBooleanValue()) {
        return true;
      }
    }
    return false;
  }

  public String getMaxKeyword() {
    return protobufGrammarAccess.getIndexRangeMaxAccess().getMaxKeyword_1().getValue();
  }
}
