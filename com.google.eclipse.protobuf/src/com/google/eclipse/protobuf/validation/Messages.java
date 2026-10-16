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
  public static String unsupportedEdition;
  public static String editionFieldModifier;
  public static String editionGroup;
  public static String editionStream;
  public static String editionReservedIdentifier;
  public static String editionReservedString;
  public static String editionDefault;
  public static String editionPacked;
  public static String editionJavaUtf8;
  public static String editionsFeaturesUnavailable;
  public static String editionImplicitEnum;
  public static String editionNamingStyle;
  public static String editionFeature;
  public static String editionFeatureUnknown;
  public static String editionFeatureTarget;
  public static String editionFileRequired;
  public static String editionImplicitDefault;
  public static String editionImplicitMessage;
  public static String editionPresenceField;
  public static String editionPackedType;
  public static String editionRepeatedEncoding;
  public static String editionUtf8Field;
  public static String editionMessageEncoding;
  public static String editionJavaEnum;
  public static String editionJavaTopLevel;
  public static String editionVisibilityYear;
  public static String editionStrictExport;
  public static String editionTitleCase;
  public static String editionLowerSnake;
  public static String editionUpperSnake;
  public static String editionGeneratedName;
  public static String invalidFeatureDefinition;
  public static String featureSupportIntroduced;
  public static String featureSupportRemoved;
  public static String featureSupportDeprecated;
  public static String invalidFeatureSupportEditions;
  public static String invalidFeatureSupportText;
  public static String invalidFeatureTargets;
  public static String invalidFeatureSupportDuplicate;
  public static String invalidFeatureDefaultsRecord;
  public static String invalidFeatureExtension;
  public static String invalidFeatureNestedExtensions;
  public static String invalidFeatureKind;
  public static String invalidFeatureTargetsRequired;
  public static String invalidFeatureIntroduction;
  public static String invalidFeatureDuplicateDefault;
  public static String invalidFeatureDefaultValue;
  public static String invalidFeatureDefaultWindow;
  public static String invalidFeatureLegacyDefault;
  public static String invalidFeatureSupportWindow;
  public static String invalidFeatureDeprecationText;
  public static String invalidFeatureRemovalText;
  public static String invalidFeatureValueWindow;
  public static String expectedEditionIdentifier;
  public static String misplacedHeader;
  public static String editionOptionImportYear;
  public static String editionWeakImport;
  public static String editionImportOrder;
  public static String editionDuplicateImport;
  public static String expectedOptionValue;
  public static String expectedFieldTerminator;

  static {
    Class<Messages> targetType = Messages.class;
    NLS.initializeMessages(targetType.getName(), targetType);
  }

  private Messages() {}
}
