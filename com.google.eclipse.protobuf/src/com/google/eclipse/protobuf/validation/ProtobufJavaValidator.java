/*
 * Copyright (c) 2014, 2015 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.validation;

import static com.google.eclipse.protobuf.protobuf.ProtobufPackage.Literals.MAP_TYPE__KEY_TYPE;
import static com.google.eclipse.protobuf.protobuf.ProtobufPackage.Literals.MAP_TYPE__VALUE_TYPE;
import static com.google.eclipse.protobuf.protobuf.ProtobufPackage.Literals.MESSAGE_FIELD__MODIFIER;
import static com.google.eclipse.protobuf.protobuf.ProtobufPackage.Literals.PACKAGE__IMPORTED_NAMESPACE;
import static com.google.eclipse.protobuf.protobuf.ProtobufPackage.Literals.SYNTAX__NAME;
import static com.google.eclipse.protobuf.validation.Messages.duplicateNameConflict;
import static com.google.eclipse.protobuf.validation.Messages.conflictingDuplicateName;
import static com.google.eclipse.protobuf.validation.Messages.expectedFieldNumber;
import static com.google.eclipse.protobuf.validation.Messages.expectedSyntaxIdentifier;
import static com.google.eclipse.protobuf.validation.Messages.fieldNumbersMustBePositive;
import static com.google.eclipse.protobuf.validation.Messages.indexRangeEndLessThanStart;
import static com.google.eclipse.protobuf.validation.Messages.indexRangeNonPositive;
import static com.google.eclipse.protobuf.validation.Messages.invalidMapKeyType;
import static com.google.eclipse.protobuf.validation.Messages.invalidMapValueType;
import static com.google.eclipse.protobuf.validation.Messages.mapWithModifier;
import static com.google.eclipse.protobuf.validation.Messages.mapWithinTypeExtension;
import static com.google.eclipse.protobuf.validation.Messages.missingModifier;
import static com.google.eclipse.protobuf.validation.Messages.multiplePackages;
import static com.google.eclipse.protobuf.validation.Messages.nameConflict;
import static com.google.eclipse.protobuf.validation.Messages.oneofFieldWithModifier;
import static com.google.eclipse.protobuf.validation.Messages.requiredInProto3;
import static com.google.eclipse.protobuf.validation.Messages.reservedIndexAndName;
import static com.google.eclipse.protobuf.validation.Messages.tagNumberRangeConflict;
import static com.google.eclipse.protobuf.validation.Messages.tagNumberConflict;
import static com.google.eclipse.protobuf.validation.Messages.conflictingExtensions;
import static com.google.eclipse.protobuf.validation.Messages.conflictingField;
import static com.google.eclipse.protobuf.validation.Messages.conflictingValue;
import static com.google.eclipse.protobuf.validation.Messages.conflictingGroup;
import static com.google.eclipse.protobuf.validation.Messages.conflictingReservedName;
import static com.google.eclipse.protobuf.validation.Messages.conflictingReservedNumber;
import static com.google.eclipse.protobuf.validation.Messages.unknownSyntax;
import static com.google.eclipse.protobuf.validation.Messages.unrecognizedSyntaxIdentifier;
import static java.lang.String.format;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Range;
import com.google.eclipse.protobuf.model.util.IndexRanges;
import com.google.eclipse.protobuf.model.util.IndexRanges.BackwardsRangeException;
import com.google.eclipse.protobuf.model.util.IndexedElements;
import com.google.eclipse.protobuf.model.util.Literals;
import com.google.eclipse.protobuf.model.util.Protobufs;
import com.google.eclipse.protobuf.model.util.StringLiterals;
import com.google.eclipse.protobuf.model.util.Syntaxes;
import com.google.eclipse.protobuf.model.util.Imports;
import com.google.eclipse.protobuf.model.util.Options;
import com.google.eclipse.protobuf.protobuf.ComplexType;
import com.google.eclipse.protobuf.protobuf.ComplexTypeLink;
import com.google.eclipse.protobuf.protobuf.DefaultValueFieldOption;
import com.google.eclipse.protobuf.protobuf.Import;
import com.google.eclipse.protobuf.protobuf.NativeOption;
import com.google.eclipse.protobuf.protobuf.PublicImport;
import com.google.eclipse.protobuf.protobuf.Service;
import com.google.eclipse.protobuf.protobuf.Rpc;
import com.google.eclipse.protobuf.protobuf.Stream;
import com.google.eclipse.protobuf.protobuf.AbstractOption;
import com.google.eclipse.protobuf.naming.NameResolver;
import com.google.eclipse.protobuf.protobuf.BuiltinLiteral;
import com.google.eclipse.protobuf.protobuf.LiteralLink;
import com.google.eclipse.protobuf.protobuf.Extensions;
import com.google.eclipse.protobuf.protobuf.Group;
import com.google.eclipse.protobuf.protobuf.IndexRange;
import com.google.eclipse.protobuf.protobuf.IndexedElement;
import com.google.eclipse.protobuf.protobuf.Literal;
import com.google.eclipse.protobuf.protobuf.MapType;
import com.google.eclipse.protobuf.protobuf.MapTypeLink;
import com.google.eclipse.protobuf.protobuf.Message;
import com.google.eclipse.protobuf.protobuf.MessageField;
import com.google.eclipse.protobuf.protobuf.ModifierEnum;
import com.google.eclipse.protobuf.protobuf.OneOf;
import com.google.eclipse.protobuf.protobuf.Option;
import com.google.eclipse.protobuf.protobuf.Package;
import com.google.eclipse.protobuf.protobuf.Protobuf;
import com.google.eclipse.protobuf.protobuf.ProtobufElement;
import com.google.eclipse.protobuf.protobuf.ProtobufPackage;
import com.google.eclipse.protobuf.protobuf.Reservation;
import com.google.eclipse.protobuf.protobuf.Reserved;
import com.google.eclipse.protobuf.protobuf.ReservedEnum;
import com.google.eclipse.protobuf.protobuf.ScalarType;
import com.google.eclipse.protobuf.protobuf.ScalarTypeLink;
import com.google.eclipse.protobuf.protobuf.StringLiteral;
import com.google.eclipse.protobuf.protobuf.Syntax;
import com.google.eclipse.protobuf.protobuf.TypeExtension;
import com.google.eclipse.protobuf.protobuf.TypeLink;
import com.google.inject.Inject;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.naming.IQualifiedNameProvider;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.util.SimpleAttributeResolver;
import org.eclipse.xtext.validation.Check;
import org.eclipse.xtext.validation.ComposedChecks;

/**
 * @author alruiz@google.com (Alex Ruiz)
 */
@ComposedChecks(validators = { DataTypeValidator.class, ImportValidator.class })
public class ProtobufJavaValidator extends AbstractProtobufJavaValidator {
  public static final String SYNTAX_IS_NOT_KNOWN_ERROR = "syntaxIsNotProto2";
  public static final String INVALID_FIELD_TAG_NUMBER_ERROR = "invalidFieldTagNumber";
  public static final String MORE_THAN_ONE_PACKAGE_ERROR = "moreThanOnePackage";
  public static final String MISSING_MODIFIER_ERROR = "noModifier";
  public static final String MAP_WITH_MODIFIER_ERROR = "mapWithModifier";
  public static final String REQUIRED_IN_PROTO3_ERROR = "requiredInProto3";
  public static final String INVALID_MAP_KEY_TYPE_ERROR = "invalidMapKeyType";
  public static final String MAP_WITH_MAP_VALUE_TYPE_ERROR = "mapWithMapValueType";
  public static final String ONEOF_FIELD_WITH_MODIFIER_ERROR = "oneofFieldWithModifier";
  public static final String PROTO3_DEFAULT_ERROR = "proto3Default";

  @Inject private IndexedElements indexedElements;
  @Inject private IndexRanges indexRanges;
  @Inject private NameResolver nameResolver;
  @Inject private StringLiterals stringLiterals;
  @Inject private Literals literals;
  @Inject private Protobufs protobufs;
  @Inject private Syntaxes syntaxes;
  @Inject private Imports imports;
  @Inject private Options options;
  @Inject private IQualifiedNameProvider qualifiedNames;

  @Check public void checkIsKnownSyntax(Protobuf protobuf) {
    if (!protobufs.hasKnownSyntax(protobuf) && protobuf.getSyntax() == null) {
      warning(unknownSyntax, null);
    }
  }

  @Check public void checkSyntaxIsKnown(Syntax syntax) {
    if (syntaxes.isSpecifyingProto2Syntax(syntax) || syntaxes.isSpecifyingProto3Syntax(syntax)) {
      return;
    }
    String name = syntaxes.getName(syntax);
    String msg = (name == null) ? expectedSyntaxIdentifier : format(unrecognizedSyntaxIdentifier, name);
    error(msg, syntax, SYNTAX__NAME, SYNTAX_IS_NOT_KNOWN_ERROR);
  }

  @Check public void checkIndexRangeBounds(IndexRange indexRange) {
    Range<Long> range;
    try {
      range = indexRanges.toLongRange(indexRange);
    } catch (BackwardsRangeException e) {
      error(indexRangeEndLessThanStart, indexRange, null);
      return;
    }

    long minimum = indexRange.eContainer() instanceof ReservedEnum ? Integer.MIN_VALUE : 1L;
    long maximum = indexRanges.maximumFor(indexRange);
    if (range.lowerEndpoint() < minimum) {
      error(minimum == 1L ? indexRangeNonPositive : Messages.enumNumberOutOfRange,
          indexRange, ProtobufPackage.Literals.INDEX_RANGE__FROM);
    }
    if (range.lowerEndpoint() > maximum || range.upperEndpoint() > maximum) {
      error(format(Messages.rangeNumberTooLarge, maximum), indexRange,
          range.lowerEndpoint() > maximum ? ProtobufPackage.Literals.INDEX_RANGE__FROM
              : ProtobufPackage.Literals.INDEX_RANGE__TO);
    }
  }

  @Check public void checkForIndexConflicts(Message message) {
    checkOwnedIndexConflicts(message);
  }

  @Check public void checkForIndexConflicts(Group group) {
    checkOwnedIndexConflicts(group);
  }

  private void checkOwnedIndexConflicts(EObject message) {
    Multimap<EObject, Range<Long>> rangeUsages = LinkedHashMultimap.create();

    for (Reserved reserved : getOwnedElements(message, Reserved.class)) {
      for (IndexRange indexRange : Iterables.filter(reserved.getReservations(), IndexRange.class)) {
        try {
          Range<Long> range = indexRanges.toLongRange(indexRange);
          errorOnConflicts(range, rangeUsages, indexRange, null);
          rangeUsages.put(reserved, range);
        } catch (BackwardsRangeException e) {
          // Do not try to find conflicts with invalid ranges.
        }
      }
    }

    for (Extensions extensions : getOwnedElements(message, Extensions.class)) {
      for (IndexRange indexRange : extensions.getRanges()) {
        try {
          Range<Long> range = indexRanges.toLongRange(indexRange);
          errorOnConflicts(range, rangeUsages, indexRange, null);
          rangeUsages.put(extensions,  range);
        } catch (BackwardsRangeException e) {
          // Do not try to find conflicts with invalid ranges.
        }
      }
    }

    for (IndexedElement element : getOwnedElements(message, IndexedElement.class)) {
      if (hasIncompleteName(element)) {
        continue;
      }
      long index = indexedElements.indexOf(element);
      Range<Long> range = Range.singleton(index);
      EStructuralFeature feature = indexedElements.indexFeatureOf(element);
      errorOnConflicts(range, rangeUsages, element, feature);
      rangeUsages.put(element, range);
    }
  }
  
  @Check public void checkForIndexConflicts(com.google.eclipse.protobuf.protobuf.Enum enumeration) {
    boolean allowAliases = Boolean.TRUE.equals(nativeBoolean(enumeration, "EnumOptions", "allow_alias"));
    Multimap<EObject, Range<Long>> rangeUsages = LinkedHashMultimap.create();
    for (ReservedEnum reserved : getOwnedElements(enumeration, ReservedEnum.class)) {
      for (IndexRange indexRange : Iterables.filter(reserved.getReservations(), IndexRange.class)) {
        try {
          Range<Long> range = indexRanges.toLongRange(indexRange);
          errorOnConflicts(range, rangeUsages, indexRange, null);
          rangeUsages.put(reserved, range);
        } catch (BackwardsRangeException e) {
          // An invalid range has its own diagnostic.
        }
      }
    }
    Set<Long> numbers = new HashSet<>();
    boolean aliasesUsed = false;
    for (Literal literal : getOwnedElements(enumeration, Literal.class)) {
      if (hasIncompleteName(literal)) {
        continue;
      }
      boolean duplicate = !numbers.add(literal.getIndex());
      aliasesUsed |= duplicate;
      errorOnConflicts(Range.singleton(literal.getIndex()), rangeUsages, literal, literals.indexFeatureOf(literal));
      if (!allowAliases) {
        rangeUsages.put(literal, Range.singleton(literal.getIndex()));
      }
    }
    NativeOption alias = nativeOption(enumeration, "EnumOptions", "allow_alias");
    Boolean value = alias == null ? null : booleanConstant(alias);
    if (Boolean.FALSE.equals(value)) {
      error(Messages.aliasFalseHasNoEffect, alias, ProtobufPackage.Literals.ABSTRACT_OPTION__VALUE);
    } else if (Boolean.TRUE.equals(value) && !aliasesUsed) {
      error(Messages.aliasWithoutDuplicate, alias, ProtobufPackage.Literals.ABSTRACT_OPTION__VALUE);
    }
  }

  private void errorOnConflicts(
      Range<Long> range,
      Multimap<EObject, Range<Long>> rangeUsages,
      EObject errorSource,
      EStructuralFeature errorFeature) {
    for (Map.Entry<EObject, Range<Long>> rangeUsage : rangeUsages.entries()) {
      Range<Long> usedRange = rangeUsage.getValue();
      if (range.isConnected(usedRange)) {
        EObject rangeUser = rangeUsage.getKey();

        boolean rangeIsSingular =
            range.hasUpperBound() && range.upperEndpoint().equals(range.lowerEndpoint());
        String template = rangeIsSingular ? tagNumberConflict : tagNumberRangeConflict;

        String rangeUserString;
        String usedRangeString = rangeToString(usedRange, rangeUser);
        if (rangeUser instanceof MessageField) {
          rangeUserString =
              String.format(conflictingField, nameResolver.nameOf(rangeUser), usedRangeString);
        } else if (rangeUser instanceof Literal) {
          rangeUserString =
              String.format(conflictingValue, nameResolver.nameOf(rangeUser), usedRangeString);
        } else if (rangeUser instanceof Group) {
          rangeUserString =
              String.format(conflictingGroup, nameResolver.nameOf(rangeUser), usedRangeString);
        } else if (rangeUser instanceof Reserved) {
          rangeUserString = String.format(conflictingReservedNumber, usedRangeString);
        } else if (rangeUser instanceof ReservedEnum) {
          rangeUserString = String.format(conflictingReservedNumber, usedRangeString);
        } else {
          rangeUserString = String.format(conflictingExtensions, usedRangeString);
        }

        String message = String.format(template, rangeToString(range, errorSource), rangeUserString);
        error(message, errorSource, errorFeature);

        // Don't report more than one error per element.
        return;
      }
    }
  }

  private String rangeToString(Range<Long> range, EObject context) {
    if (range.hasLowerBound() && range.hasUpperBound()
        && range.lowerEndpoint().equals(range.upperEndpoint())) {
      return String.valueOf(range.lowerEndpoint());
    }

    long maximum = context instanceof IndexRange ? indexRanges.maximumFor((IndexRange) context)
        : context instanceof ReservedEnum ? Integer.MAX_VALUE
        : context instanceof Extensions && indexRanges.isMessageSet(context.eContainer())
            ? IndexRanges.MAX_MESSAGE_SET_EXTENSION_NUMBER : IndexRanges.MAX_FIELD_NUMBER;
    String upper = range.upperEndpoint() == maximum ? indexRanges.getMaxKeyword()
        : String.valueOf(range.upperEndpoint());
    return String.format("%d to %s", range.lowerEndpoint(), upper);
  }


  @Check public void checkForReservedNameConflicts(Message message) {
    checkOwnedReservedNameConflicts(message);
  }

  @Check public void checkForReservedNameConflicts(Group group) {
    checkOwnedReservedNameConflicts(group);
  }

  private void checkOwnedReservedNameConflicts(EObject message) {
    Set<String> reservedNames = new HashSet<>();
    for (Reserved reserved : getOwnedElements(message, Reserved.class)) {
      for (StringLiteral stringLiteral :
          Iterables.filter(reserved.getReservations(), StringLiteral.class)) {
        String name = stringLiterals.getCombinedString(stringLiteral);
        if (name == null) {
          continue;
        }
        reportReservedNameConflicts(name, reservedNames, stringLiteral, null);
        reservedNames.add(name);
      }
    }

    for (IndexedElement element : getOwnedElements(message, IndexedElement.class)) {
      String name = nameResolver.nameOf(element);
      if (element instanceof Group && name != null) {
        name = asciiLower(name);
      }
      if (name != null && !name.isEmpty()) {
        EAttribute nameAttribute = SimpleAttributeResolver.NAME_RESOLVER.getAttribute(element);
        reportReservedNameConflicts(name, reservedNames, element, nameAttribute);
      }
    }
  }
  
  @Check public void checkForReservedNameConflicts(com.google.eclipse.protobuf.protobuf.Enum anEnum) {
    Set<String> reservedNames = new HashSet<>();
    for (ReservedEnum reserved : getOwnedElements(anEnum, ReservedEnum.class)) {
      for (StringLiteral stringLiteral :
          Iterables.filter(reserved.getReservations(), StringLiteral.class)) {
        String name = stringLiterals.getCombinedString(stringLiteral);
        if (name == null) {
          continue;
        }
        reportReservedNameConflicts(name, reservedNames, stringLiteral, null);
        reservedNames.add(name);
      }
    }

    for (Literal literal : getOwnedElements(anEnum, Literal.class)) {
      String name = nameResolver.nameOf(literal);
      if (name != null && !name.isEmpty()) {
        EAttribute nameAttribute = SimpleAttributeResolver.NAME_RESOLVER.getAttribute(literal);
        reportReservedNameConflicts(name, reservedNames, literal, nameAttribute);
      }
    }
  }
  
  @Check public void checkForDuplicateFieldNames(Message message) {
    checkNamespace(message);
  }

  @Check public void checkForDuplicateFieldNames(Group group) {
    checkNamespace(group);
  }

  @Check public void checkForDuplicateMethodNames(Service service) {
    checkNamespace(service);
  }

  @Check public void checkForDuplicateValues(com.google.eclipse.protobuf.protobuf.Enum enumeration) {
    // Literal names live in the enum's enclosing scope, not in a second enum namespace.
    checkEnumNames(enumeration);
  }

  @Check public void checkForReservedIndexAndName(Reserved reserved) {
    checkMixedReservation(reserved, reserved.getReservations());
  }

  @Check public void checkForReservedIndexAndName(ReservedEnum reserved) {
    checkMixedReservation(reserved, reserved.getReservations());
  }

  private void checkMixedReservation(EObject reserved, List<Reservation> reservations) {
    boolean hasIndexReservation = false;
    boolean hasNameReservation = false;
    for (Reservation reservation : reservations) {
      if (reservation instanceof IndexRange) {
        hasIndexReservation = true;
      } else if (reservation instanceof StringLiteral) {
        hasNameReservation = true;
      }
    }

    if (hasIndexReservation && hasNameReservation) {
      error(reservedIndexAndName, reserved, null);
    }
  }

  private void reportReservedNameConflicts(
      String name, Set<String> reservedNames, EObject errorSource, EAttribute errorFeature) {
    if (reservedNames.contains(name)) {
      String nameUser = String.format(conflictingReservedName, name);
      String message = String.format(nameConflict, name, nameUser);
      error(message, errorSource, errorFeature);
    }
  }
  
  private void reportDuplicateNameConflicts(String name, EObject errorSource, EAttribute errorFeature) {
      String nameUser = String.format(conflictingDuplicateName, name);
      String message = String.format(duplicateNameConflict, name, nameUser);
      error(message, errorSource, errorFeature);
  }

  private <E extends EObject> List<E> getOwnedElements(EObject owner, Class<E> type) {
    List<E> result = new ArrayList<>();
    for (EObject element : indexedElements.ownedElements(owner)) {
      if (type.isInstance(element)) {
        result.add(type.cast(element));
      }
    }
    return result;
  }

  @Check public void checkFieldModifiers(MessageField field) {
    if (field.eContainer() instanceof MapType) {
      return;
    }
    if (field.eContainer() instanceof OneOf) {
      checkOneOfField(field);
      if (field.getType() instanceof MapTypeLink) {
        error(Messages.mapInOneof, field, ProtobufPackage.Literals.MESSAGE_FIELD__TYPE);
      }
    }
    if (field.getType() instanceof MapTypeLink) {
      checkMapField(field);
      return;
    }
    if (field.eContainer() instanceof OneOf) {
      return;
    }
    if (field.getModifier() == ModifierEnum.UNSPECIFIED && syntaxes.isProto2(field)) {
      error(missingModifier, field, MESSAGE_FIELD__MODIFIER, MISSING_MODIFIER_ERROR);
    } else if (field.getModifier() == ModifierEnum.REQUIRED && syntaxes.isProto3(field)) {
      error(requiredInProto3, field, MESSAGE_FIELD__MODIFIER, REQUIRED_IN_PROTO3_ERROR);
    }
  }

  private void checkMapField(MessageField field) {
    if (field.getModifier() != ModifierEnum.UNSPECIFIED) {
      error(mapWithModifier, field, MESSAGE_FIELD__MODIFIER, MAP_WITH_MODIFIER_ERROR);
    }
  }

  private void checkOneOfField(MessageField field) {
    if (field.getModifier() != ModifierEnum.UNSPECIFIED) {
      error(oneofFieldWithModifier, field, MESSAGE_FIELD__MODIFIER,
          ONEOF_FIELD_WITH_MODIFIER_ERROR);
    }
  }


  @Check public void checkTagNumberIsGreaterThanZero(IndexedElement e) {
    if (e.eContainer() instanceof MapType || hasIncompleteName(e)) {
      return;
    }
    long index = indexedElements.indexOf(e);
    if (index <= 0L) {
      invalidTagNumberError(index == 0L ? fieldNumbersMustBePositive : expectedFieldNumber, e);
    } else if (!(e.eContainer() instanceof TypeExtension) && index > IndexRanges.MAX_FIELD_NUMBER) {
      invalidTagNumberError(format(Messages.fieldNumberTooLarge, IndexRanges.MAX_FIELD_NUMBER), e);
    } else if (index >= IndexRanges.FIRST_RESERVED_NUMBER && index <= IndexRanges.LAST_RESERVED_NUMBER) {
      invalidTagNumberError(Messages.implementationReservedNumber, e);
    } else if (e.eContainer() instanceof TypeExtension) {
      checkExtensionNumber(e, (TypeExtension) e.eContainer());
    }
  }

  private void invalidTagNumberError(String message, IndexedElement e) {
    error(message, e, indexedElements.indexFeatureOf(e), INVALID_FIELD_TAG_NUMBER_ERROR);
  }

  @Check public void checkOnlyOnePackageDefinition(Package aPackage) {
    boolean firstFound = false;
    Protobuf root = (Protobuf) aPackage.eContainer();
    for (ProtobufElement e : root.getElements()) {
      if (e == aPackage) {
        if (firstFound) {
          error(multiplePackages, aPackage, PACKAGE__IMPORTED_NAMESPACE, MORE_THAN_ONE_PACKAGE_ERROR);
        }
        return;
      }
      if (e instanceof Package && !firstFound) {
        firstFound = true;
      }
    }
  }

  private boolean hasIncompleteName(EObject object) {
    String name = nameResolver.nameOf(object);
    return name == null || name.isEmpty();
  }

  @Check public void checkMapTypeHasValidKeyType(MapType map) {
    TypeLink keyType = map.getKeyType();
    if (!(keyType instanceof ScalarTypeLink)) {
      error(invalidMapKeyType, map, MAP_TYPE__KEY_TYPE, INVALID_MAP_KEY_TYPE_ERROR);
      return;
    }
    ScalarType scalarKeyType = ((ScalarTypeLink) keyType).getTarget();
    if (scalarKeyType == ScalarType.BYTES || scalarKeyType == ScalarType.DOUBLE
        || scalarKeyType == ScalarType.FLOAT) {
      error(invalidMapKeyType, map, MAP_TYPE__KEY_TYPE, INVALID_MAP_KEY_TYPE_ERROR);
    }
  }

  @Check public void checkMapTypeHasValidValueType(MapType map) {
    TypeLink keyType = map.getValueType();
    if (keyType instanceof MapTypeLink) {
      error(invalidMapValueType, map, MAP_TYPE__VALUE_TYPE, MAP_WITH_MAP_VALUE_TYPE_ERROR);
      return;
    }
  }

  @Check public void checkMapIsNotWithinExtension(MapType map) {
    EObject link = map.eContainer();
    EObject field = link == null ? null : link.eContainer();
    if (field instanceof MessageField && field.eContainer() instanceof TypeExtension) {
      error(mapWithinTypeExtension, map, null);
    }
  }

  @Check public void checkProto3Default(DefaultValueFieldOption option) {
    if (syntaxes.isProto3(option) && option.eContainer() instanceof IndexedElement) {
      error(Messages.proto3Default, option, ProtobufPackage.Literals.ABSTRACT_OPTION__VALUE,
          PROTO3_DEFAULT_ERROR);
    }
  }

  @Check public void checkGroup(Group group) {
    if (syntaxes.isProto3(group)) {
      error(Messages.proto3Group, group, ProtobufPackage.Literals.COMPLEX_TYPE__NAME);
    }
    if (group.eContainer() instanceof OneOf) {
      if (group.getModifier() != ModifierEnum.UNSPECIFIED) {
        error(oneofFieldWithModifier, group, ProtobufPackage.Literals.GROUP__MODIFIER,
            ONEOF_FIELD_WITH_MODIFIER_ERROR);
      }
    } else if (group.getModifier() == ModifierEnum.UNSPECIFIED && syntaxes.isProto2(group)) {
      error(missingModifier, group, ProtobufPackage.Literals.GROUP__MODIFIER, MISSING_MODIFIER_ERROR);
    }
  }

  @Check public void checkExtensions(Extensions extensions) {
    if (syntaxes.isProto3(extensions)) {
      error(Messages.proto3ExtensionRange, extensions, ProtobufPackage.Literals.EXTENSIONS__RANGES);
    }
  }

  @Check public void checkLegacyStream(Stream stream) {
    if (syntaxes.isProto3(stream)) {
      error(Messages.proto3Stream, stream, ProtobufPackage.Literals.STREAM__NAME);
    }
  }

  @Check public void checkMessageSet(Message message) {
    if (syntaxes.isProto3(message) && indexRanges.isMessageSet(message)) {
      NativeOption option = nativeOption(message, "MessageOptions", "message_set_wire_format");
      error(Messages.proto3MessageSet, option, ProtobufPackage.Literals.ABSTRACT_OPTION__VALUE);
    }
  }

  @Check public void checkOneOf(OneOf oneof) {
    if (oneof.isIsRepeated()) {
      error(Messages.repeatedOneof, oneof, ProtobufPackage.Literals.ONE_OF__IS_REPEATED);
    }
    boolean hasField = false;
    for (EObject member : oneof.getElements()) {
      if (member instanceof IndexedElement) {
        hasField = true;
      } else if (!(member instanceof Option)) {
        error(Messages.invalidOneofMember, member, null);
      }
    }
    if (!hasField) {
      error(Messages.emptyOneof, oneof, ProtobufPackage.Literals.ONE_OF__NAME);
    }
  }

  @Check public void checkEnum(com.google.eclipse.protobuf.protobuf.Enum enumeration) {
    List<Literal> values = getOwnedElements(enumeration, Literal.class);
    if (values.isEmpty()) {
      error(Messages.emptyEnum, enumeration, ProtobufPackage.Literals.COMPLEX_TYPE__NAME);
    } else if (!hasIncompleteName(values.get(0)) && syntaxes.isProto3(enumeration)
        && values.get(0).getIndex() != 0L) {
      error(Messages.proto3EnumZero, values.get(0), literals.indexFeatureOf(values.get(0)));
    }
  }

  @Check public void checkEnumNumber(Literal literal) {
    if (!(literal instanceof BuiltinLiteral) && !hasIncompleteName(literal)
        && (literal.getIndex() < Integer.MIN_VALUE || literal.getIndex() > Integer.MAX_VALUE)) {
      error(Messages.enumNumberOutOfRange, literal, literals.indexFeatureOf(literal));
    }
  }

  @Check public void checkEnumTypeUse(ComplexTypeLink link) {
    ComplexType target = link.getTarget();
    if (!(target instanceof com.google.eclipse.protobuf.protobuf.Enum) || target.eIsProxy()) {
      return;
    }
    com.google.eclipse.protobuf.protobuf.Enum enumeration = (com.google.eclipse.protobuf.protobuf.Enum) target;
    EObject owner = link.eContainer();
    boolean mapValue = owner instanceof MapType && ((MapType) owner).getValueType() == link;
    MessageField field = null;
    if (owner instanceof MessageField) {
      field = (MessageField) owner;
    } else if (mapValue && owner.eContainer() != null
        && owner.eContainer().eContainer() instanceof MessageField) {
      field = (MessageField) owner.eContainer().eContainer();
    }
    if (field == null || field.eContainer() instanceof MapType) {
      return;
    }
    if (syntaxes.isProto3(field) && syntaxes.isProto2(enumeration)) {
      error(Messages.proto3ClosedEnum, link, ProtobufPackage.Literals.COMPLEX_TYPE_LINK__TARGET);
    }
    if (mapValue) {
      for (EObject element : enumeration.getElements()) {
        if (element instanceof Literal) {
          if (((Literal) element).getIndex() != 0L) {
            error(Messages.mapEnumZero, link, ProtobufPackage.Literals.COMPLEX_TYPE_LINK__TARGET);
          }
          break;
        }
      }
    }
  }

  @Check public void checkProto3Extension(TypeExtension extension) {
    if (!syntaxes.isProto3(extension) || extension.getType() == null) {
      return;
    }
    EObject target = extension.getType().getTarget();
    if (target == null || target.eIsProxy()) {
      return;
    }
    String name = qualifiedName(target);
    String prefix = name.startsWith("google.protobuf.") ? "google.protobuf."
        : name.startsWith("proto2.") ? "proto2." : "";
    String optionName = name.substring(prefix.length());
    boolean valid = target instanceof Message && !prefix.isEmpty()
        && ("FileOptions".equals(optionName) || "MessageOptions".equals(optionName)
            || "FieldOptions".equals(optionName) || "EnumOptions".equals(optionName)
            || "EnumValueOptions".equals(optionName) || "ServiceOptions".equals(optionName)
            || "MethodOptions".equals(optionName) || "OneofOptions".equals(optionName)
            || "ExtensionRangeOptions".equals(optionName));
    if (!valid) {
      error(Messages.proto3Extension, extension, ProtobufPackage.Literals.TYPE_EXTENSION__TYPE);
    }
  }

  private void checkExtensionNumber(IndexedElement field, TypeExtension extension) {
    if (extension.getType() == null) {
      return;
    }
    EObject target = extension.getType().getTarget();
    if (target == null || target.eIsProxy()) {
      return;
    }
    long number = indexedElements.indexOf(field);
    boolean inRange = false;
    for (Extensions ranges : getOwnedElements(target, Extensions.class)) {
      for (IndexRange source : ranges.getRanges()) {
        try {
          if (indexRanges.toLongRange(source).contains(number)) {
            inRange = true;
          }
        } catch (BackwardsRangeException e) {
          // Invalid extendee ranges have their own diagnostics.
        }
      }
    }
    long maximum = indexRanges.isMessageSet(target)
        ? IndexRanges.MAX_MESSAGE_SET_EXTENSION_NUMBER : IndexRanges.MAX_FIELD_NUMBER;
    if (!inRange || number > maximum) {
      invalidTagNumberError(Messages.extensionNumberOutsideRange, field);
    }
  }

  @Check public void checkVisibleSymbols(Protobuf root) {
    List<Protobuf> visible = visibleRoots(root);
    Map<String, EObject> names = new HashMap<>();
    for (Protobuf imported : visible) {
      if (imported != root) {
        for (EObject symbol : scopeSymbols(imported)) {
          addQualifiedSymbol(names, symbol, false);
        }
      }
    }
    for (Protobuf file : visible) {
      for (Package declaration : getOwnedElements(file, Package.class)) {
        String name = declaration.getImportedNamespace();
        if (name == null) {
          continue;
        }
        for (int end = name.indexOf('.'); ; end = name.indexOf('.', end + 1)) {
          String prefix = end < 0 ? name : name.substring(0, end);
          EObject previous = names.putIfAbsent(prefix, declaration);
          if (file == root && previous != null && !(previous instanceof Package)) {
            error(format(nameConflict, prefix, format(conflictingDuplicateName, prefix)), declaration,
                PACKAGE__IMPORTED_NAMESPACE);
          }
          if (end < 0) {
            break;
          }
        }
      }
    }
    for (EObject symbol : scopeSymbols(root)) {
      addQualifiedSymbol(names, symbol, true);
    }
    Map<EObject, Map<Long, IndexedElement>> numbers = new HashMap<>();
    // Imported definitions precede this file so each collision is diagnosed on this file's declaration.
    for (Protobuf file : visible) {
      for (TypeExtension extension : EcoreUtil2.getAllContentsOfType(file, TypeExtension.class)) {
        EObject target = extension.getType() == null ? null : extension.getType().getTarget();
        if (target == null || target.eIsProxy()) {
          continue;
        }
        Map<Long, IndexedElement> used = numbers.get(target);
        if (used == null) {
          used = new HashMap<>();
          numbers.put(target, used);
        }
        for (IndexedElement field : extension.getElements()) {
          long number = indexedElements.indexOf(field);
          IndexedElement previous = used.putIfAbsent(number, field);
          if (file == root && previous != null && previous != field) {
            invalidTagNumberError(format(Messages.duplicateExtensionNumber, number), field);
          }
        }
      }
    }
  }

  private void addQualifiedSymbol(Map<String, EObject> names, EObject symbol, boolean report) {
    String name = qualifiedName(symbol);
    if (name.isEmpty()) {
      return;
    }
    addQualifiedSymbolName(names, name, symbol, report);
    if (symbol instanceof Group) {
      int dot = name.lastIndexOf('.');
      String fieldName = name.substring(0, dot + 1) + asciiLower(name.substring(dot + 1));
      addQualifiedSymbolName(names, fieldName, symbol, report);
    }
  }

  private void addQualifiedSymbolName(Map<String, EObject> names, String name, EObject symbol, boolean report) {
    EObject previous = names.putIfAbsent(name, symbol);
    if (report && previous != null && previous != symbol) {
      reportDuplicateNameConflicts(nameResolver.nameOf(symbol), symbol,
          SimpleAttributeResolver.NAME_RESOLVER.getAttribute(symbol));
    }
  }

  private List<Protobuf> visibleRoots(Protobuf root) {
    List<Protobuf> result = new ArrayList<>();
    Set<Resource> seen = new HashSet<>();
    seen.add(root.eResource());
    collectImportedRoots(root, false, seen, result);
    result.add(root);
    return result;
  }

  private void collectImportedRoots(Protobuf root, boolean publicOnly, Set<Resource> seen, List<Protobuf> result) {
    for (ProtobufElement element : root.getElements()) {
      if (!(element instanceof Import) || publicOnly && !(element instanceof PublicImport)) {
        continue;
      }
      Resource resource = imports.importedResource((Import) element);
      if (resource != null && seen.add(resource) && !resource.getContents().isEmpty()
          && resource.getContents().get(0) instanceof Protobuf) {
        Protobuf imported = (Protobuf) resource.getContents().get(0);
        result.add(imported);
        collectImportedRoots(imported, true, seen, result);
      }
    }
  }

  private void checkNamespace(EObject owner) {
    Map<String, EObject> names = new LinkedHashMap<>();
    for (EObject symbol : scopeSymbols(owner)) {
      addName(names, nameResolver.nameOf(symbol), symbol);
      if (symbol instanceof Group && nameResolver.nameOf(symbol) != null) {
        addName(names, asciiLower(nameResolver.nameOf(symbol)), symbol);
      }
    }
    for (MessageField field : getOwnedElements(owner, MessageField.class)) {
      if (field.getType() instanceof MapTypeLink && !hasIncompleteName(field)) {
        String entry = mapEntryName(field.getName());
        EObject previous = names.putIfAbsent(entry, field);
        if (previous != null && previous != field) {
          error(format(Messages.mapEntryNameConflict, entry), field, ProtobufPackage.Literals.MESSAGE_FIELD__NAME);
        }
      }
    }
  }

  private List<EObject> scopeSymbols(EObject owner) {
    List<EObject> result = new ArrayList<>();
    for (EObject element : indexedElements.ownedElements(owner)) {
      if (element instanceof IndexedElement || element instanceof ComplexType || element instanceof OneOf
          || element instanceof Service || element instanceof Rpc || element instanceof Stream) {
        result.add(element);
      }
      if (element instanceof com.google.eclipse.protobuf.protobuf.Enum) {
        result.addAll(getOwnedElements(element, Literal.class));
      } else if (element instanceof TypeExtension) {
        result.addAll(((TypeExtension) element).getElements());
      }
    }
    return result;
  }

  private void addName(Map<String, EObject> names, String name, EObject symbol) {
    if (name == null || name.isEmpty()) {
      return;
    }
    EObject previous = names.putIfAbsent(name, symbol);
    if (previous != null && previous != symbol) {
      reportDuplicateNameConflicts(name, symbol, SimpleAttributeResolver.NAME_RESOLVER.getAttribute(symbol));
    }
  }

  private String mapEntryName(String name) {
    StringBuilder result = new StringBuilder(name.length() + 5);
    boolean capitalize = true;
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (c == '_') {
        capitalize = true;
      } else {
        result.append(capitalize ? asciiUpper(c) : c);
        capitalize = false;
      }
    }
    return result.append("Entry").toString();
  }

  private void checkEnumNames(com.google.eclipse.protobuf.protobuf.Enum enumeration) {
    Map<String, Literal> names = new HashMap<>();
    String enumName = enumeration.getName();
    if (enumName == null || enumName.isEmpty()) {
      return;
    }
    String prefix = asciiLower(enumName.replace("_", ""));
    for (Literal literal : getOwnedElements(enumeration, Literal.class)) {
      String name = literal.getName();
      if (name == null || name.isEmpty()) {
        continue;
      }
      String normalized = enumValueName(stripEnumPrefix(name, prefix));
      Literal previous = names.putIfAbsent(normalized, literal);
      if (previous != null && !name.equals(previous.getName()) && literal.getIndex() != previous.getIndex()) {
        String message = format(Messages.enumNameConflict, name, previous.getName());
        if (syntaxes.isProto2(enumeration)
            && Boolean.TRUE.equals(nativeBoolean(enumeration, "EnumOptions", "deprecated_legacy_json_field_conflicts"))) {
          warning(message, literal, ProtobufPackage.Literals.LITERAL__NAME);
        } else {
          error(message, literal, ProtobufPackage.Literals.LITERAL__NAME);
        }
      }
    }
  }

  private String stripEnumPrefix(String name, String prefix) {
    int i = 0;
    int j = 0;
    while (i < name.length() && j < prefix.length()) {
      char c = name.charAt(i++);
      if (c != '_' && asciiLower(c) != prefix.charAt(j++)) {
        return name;
      }
    }
    if (j < prefix.length()) {
      return name;
    }
    while (i < name.length() && name.charAt(i) == '_') {
      i++;
    }
    return i == name.length() ? name : name.substring(i);
  }

  private String enumValueName(String name) {
    StringBuilder result = new StringBuilder(name.length());
    boolean uppercase = true;
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (c == '_') {
        uppercase = true;
      } else {
        result.append(uppercase ? asciiUpper(c) : asciiLower(c));
        uppercase = false;
      }
    }
    return result.toString();
  }

  private char asciiUpper(char c) {
    return c >= 'a' && c <= 'z' ? (char) (c - 'a' + 'A') : c;
  }

  private char asciiLower(char c) {
    return c >= 'A' && c <= 'Z' ? (char) (c - 'A' + 'a') : c;
  }

  private String asciiLower(String text) {
    StringBuilder result = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      result.append(asciiLower(text.charAt(i)));
    }
    return result.toString();
  }

  private String qualifiedName(EObject object) {
    if (object == null || object.eIsProxy() || hasIncompleteName(object)) {
      return "";
    }
    QualifiedName name = qualifiedNames.getFullyQualifiedName(object);
    return name == null ? "" : name.toString();
  }

  private NativeOption nativeOption(EObject owner, String optionsType, String name) {
    for (NativeOption option : getOwnedElements(owner, NativeOption.class)) {
      IndexedElement target = options.rootSourceOf(option);
      if (!options.fieldsOf(option).isEmpty() || target == null || target.eIsProxy()) {
        continue;
      }
      String resolved = qualifiedName(target);
      if (resolved.equals("google.protobuf." + optionsType + "." + name)
          || resolved.equals("proto2." + optionsType + "." + name)) {
        return option;
      }
    }
    return null;
  }

  private Boolean nativeBoolean(EObject owner, String optionsType, String name) {
    NativeOption option = nativeOption(owner, optionsType, name);
    return option == null ? null : booleanConstant(option);
  }

  private Boolean booleanConstant(AbstractOption option) {
    if (!(option.getValue() instanceof LiteralLink)) {
      return null;
    }
    LiteralLink link = (LiteralLink) option.getValue();
    Literal target = link.getTarget();
    if (link.isIsNegative() || !(target instanceof BuiltinLiteral)
        || !("true".equals(target.getName()) || "false".equals(target.getName()))) {
      return null;
    }
    return ((BuiltinLiteral) target).isBooleanValue();
  }
}
