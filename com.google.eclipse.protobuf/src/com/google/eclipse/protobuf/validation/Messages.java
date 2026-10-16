/*
 * Copyright (c) 2011, 2015 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.validation;

import org.eclipse.osgi.util.NLS;

/**
 * @author alruiz@google.com (Alex Ruiz)
 */
public class Messages extends NLS {
  public static String duplicateNameConflict;
  public static String conflictingExtensions;
  public static String conflictingField;
  public static String conflictingValue;
  public static String conflictingGroup;
  public static String conflictingDuplicateName;
  public static String conflictingReservedName;
  public static String conflictingReservedNumber;
  public static String expectedFieldName;
  public static String expectedFieldNumber;
  public static String expectedIdentifier;
  public static String expectedInteger;
  public static String expectedNumber;
  public static String expectedPositiveNumber;
  public static String expectedString;
  public static String expectedSyntaxIdentifier;
  public static String expectedTrueOrFalse;
  public static String fieldNumbersMustBePositive;
  public static String importingUnsupportedSyntax;
  public static String importNotFound;
  public static String indexRangeEndLessThanStart;
  public static String indexRangeNonPositive;
  public static String invalidMapKeyType;
  public static String invalidMapValueType;
  public static String literalNotInEnum;
  public static String mapWithModifier;
  public static String mapWithinTypeExtension;
  public static String missingFieldNumber;
  public static String missingModifier;
  public static String multiplePackages;
  public static String nameConflict;
  public static String oneofFieldWithModifier;
  public static String requiredInProto3;
  public static String reservedIndexAndName;
  public static String scopingError;
  public static String tagNumberRangeConflict;
  public static String tagNumberConflict;
  public static String unknownSyntax;
  public static String unrecognizedSyntaxIdentifier;
  public static String proto3Default;
  public static String fieldNumberTooLarge;
  public static String implementationReservedNumber;
  public static String rangeNumberTooLarge;
  public static String enumNumberOutOfRange;
  public static String proto3Group;
  public static String proto3ExtensionRange;
  public static String proto3Stream;
  public static String proto3MessageSet;
  public static String proto3Extension;
  public static String extensionNumberOutsideRange;
  public static String duplicateExtensionNumber;
  public static String repeatedOneof;
  public static String invalidOneofMember;
  public static String emptyOneof;
  public static String mapInOneof;
  public static String emptyEnum;
  public static String proto3EnumZero;
  public static String mapEnumZero;
  public static String proto3ClosedEnum;
  public static String aliasFalseHasNoEffect;
  public static String aliasWithoutDuplicate;
  public static String enumNameConflict;
  public static String mapEntryNameConflict;

  static {
    Class<Messages> targetType = Messages.class;
    NLS.initializeMessages(targetType.getName(), targetType);
  }

  private Messages() {}
}
