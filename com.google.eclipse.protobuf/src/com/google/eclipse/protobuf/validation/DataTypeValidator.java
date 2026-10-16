/*
 * Copyright (c) 2011 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.validation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.naming.IQualifiedNameProvider;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.validation.AbstractDeclarativeValidator;
import org.eclipse.xtext.validation.Check;
import org.eclipse.xtext.validation.EValidatorRegistrar;

import com.google.eclipse.protobuf.model.util.IndexRanges;
import com.google.eclipse.protobuf.model.util.FeatureSets;
import com.google.eclipse.protobuf.model.util.MessageFields;
import com.google.eclipse.protobuf.model.util.OptionValues;
import com.google.eclipse.protobuf.model.util.Options;
import com.google.eclipse.protobuf.model.util.StringLiterals;
import com.google.eclipse.protobuf.model.util.Syntaxes;
import com.google.eclipse.protobuf.protobuf.*;
import com.google.eclipse.protobuf.protobuf.Enum;
import com.google.inject.Inject;

/** Type and assignment rules shared by descriptor options and TextFormat aggregates. */
public class DataTypeValidator extends AbstractDeclarativeValidator {
  public static final String EXPECTED_BOOL_ERROR = "expectedBool";
  public static final String EXPECTED_STRING_ERROR = "expectedString";
  public static final String OPTION_TYPE_ERROR = "optionType";
  public static final String OPTION_ASSIGNMENT_ERROR = "optionAlreadySet";
  public static final String OPTION_CONTEXT_ERROR = "optionContext";
  public static final String EXTENSION_DECLARATION_ERROR = "extensionDeclaration";
  public static final String JSON_NAME_ERROR = "jsonName";

  private static final BigInteger INT32_MIN = BigInteger.valueOf(Integer.MIN_VALUE);
  private static final BigInteger INT32_MAX = BigInteger.valueOf(Integer.MAX_VALUE);
  private static final BigInteger INT64_MIN = BigInteger.valueOf(Long.MIN_VALUE);
  private static final BigInteger INT64_MAX = BigInteger.valueOf(Long.MAX_VALUE);
  private static final BigInteger UINT32_MAX = BigInteger.ONE.shiftLeft(32).subtract(BigInteger.ONE);
  private static final BigInteger UINT64_MAX = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
  private static final Pattern QUALIFIED_SYMBOL = Pattern.compile("\\.[A-Za-z_][A-Za-z_0-9]*(\\.[A-Za-z_][A-Za-z_0-9]*)*");

  @Inject private IQualifiedNameProvider names;
  @Inject private MessageFields messageFields;
  @Inject private OptionValues values;
  @Inject private Options options;
  @Inject private StringLiterals strings;
  @Inject private Syntaxes syntaxes;
  @Inject private IndexRanges ranges;
  @Inject private FeatureSets features;

  @Override public void register(EValidatorRegistrar registrar) {}

  @Check public void checkStringLiteral(StringLiteral literal) {
    EObject expected = values.expectedType(literal);
    boolean bytes = expected instanceof ScalarTypeLink && ((ScalarTypeLink) expected).getTarget() == ScalarType.BYTES;
    String problem = strings.errorOf(literal, !bytes);
    if (problem != null) {
      error(problem, literal, ProtobufPackage.Literals.STRING_LITERAL__CHUNKS, "invalidStringLiteral");
    }
  }

  @Check public void checkOptionValue(AbstractOption option) {
    if (option instanceof JsonNameFieldOption) {
      checkJsonName((JsonNameFieldOption) option);
      return;
    }
    if (option instanceof DefaultValueFieldOption) {
      EObject owner = option.eContainer();
      if (!(owner instanceof MessageField)) {
        problem("The default option is only allowed on fields.", option, OPTION_CONTEXT_ERROR);
        return;
      }
      if (syntaxes.isProto2(owner) && !messageFields.supportsExplicitDefault((MessageField) owner)) {
        problem("Explicit default values are only allowed on singular scalar or enum fields outside oneofs.", option,
            OPTION_CONTEXT_ERROR);
      }
      if (syntaxes.isEditions(owner) && features.hasRequiredMetadata(owner)
          && !messageFields.supportsExplicitDefault((MessageField) owner)) {
        problem(Messages.editionDefault, option, EditionValidator.EDITION_DEFAULT);
      }
    } else {
      checkOptionContext(option);
    }
    EObject expected = values.expectedType(option);
    if (expected != null && option.getValue() != null) {
      validateValue(option.getValue(), expected, false);
    }
  }

  @Check public void checkAggregateField(ValueField field) {
    EObject expected = values.expectedType(field);
    IndexedElement declaration = values.declarationOf(field);
    boolean list = isList(field);
    if (list && (declaration != null || field.getName() instanceof AnyFieldName) && !values.isRepeated(declaration)) {
      problem("List syntax is only allowed for repeated fields.", field, OPTION_TYPE_ERROR);
    }
    if (expected == null) {
      return;
    }
    if (!isMessage(expected) && !hasColon(field)) {
      problem("Expected ':' after the scalar field name.", field, OPTION_TYPE_ERROR);
    }
    // Empty lists are represented by SimpleValueField, including repeated messages.
    for (Value value : fieldValues(field)) {
      validateValue(value, expected, true);
    }
    if (field.getName() instanceof AnyFieldName) {
      EObject ownerType = values.expectedType(field.eContainer());
      if (ownerType != null && !"google.protobuf.Any".equals(qualifiedName(ownerType))) {
        problem("Expanded Any syntax is only allowed in google.protobuf.Any.", field.getName(), OPTION_CONTEXT_ERROR);
      }
    } else if (declaration != null) {
      boolean extension = declaration.eContainer() instanceof TypeExtension;
      if (extension != (field.getName() instanceof ExtensionFieldName)) {
        problem(extension ? "Extension fields require bracket notation." : "This field is not an extension.",
            field.getName(), OPTION_CONTEXT_ERROR);
      }
      if (!isEditionFeatureOption(field)) {
        checkTargets(declaration, enclosingOptionOwner(field), field);
      }
      if (!isEditionFeatureOption(field) || !isMessage(expected)) {
        for (Value value : fieldValues(field)) {
          checkSupport(declaration, value, field);
        }
      }
      for (Value value : fieldValues(field)) {
        checkLegacyEditionOption(declaration, value, field, true);
      }
      checkReservedNativeDeclaration(declaration, field);
    }
  }

  private void validateValue(Value value, EObject expected, boolean aggregate) {
    if (isMessage(expected)) {
      if (!(value instanceof ComplexValue)) {
        problem("Expected a message aggregate.", value, OPTION_TYPE_ERROR);
      }
      return;
    }
    if (value instanceof ComplexValue) {
      problem("Expected a scalar or enum value, not a message aggregate.", value, OPTION_TYPE_ERROR);
      return;
    }
    if (expected instanceof Enum) {
      Literal literal = value instanceof LiteralLink ? ((LiteralLink) value).getTarget() : null;
      if (!(value instanceof LiteralLink) || ((LiteralLink) value).isIsNegative() || literal instanceof BuiltinLiteral
          || literal != null && !literal.eIsProxy() && literal.eContainer() != expected) {
        problem("Expected a value of enum " + qualifiedName(expected) + ".", value, OPTION_TYPE_ERROR);
      }
      return;
    }
    if (!(expected instanceof ScalarTypeLink)) {
      return;
    }
    ScalarType type = ((ScalarTypeLink) expected).getTarget();
    if (type == null) {
      return;
    }
    switch (type) {
      case BOOL:
        if (!isBoolean(value, aggregate)) {
          problem(Messages.expectedTrueOrFalse, value, EXPECTED_BOOL_ERROR);
        }
        return;
      case STRING:
      case BYTES:
        if (!(value instanceof StringLink)) {
          problem(Messages.expectedString, value, EXPECTED_STRING_ERROR);
        }
        return;
      case FLOAT:
      case DOUBLE:
        if (!isFloating(value, aggregate)) {
          problem(Messages.expectedNumber, value, OPTION_TYPE_ERROR);
        } else if (!(value instanceof LiteralLink)) {
          double number = values.floatingValue(value);
          if (Double.isInfinite(number) || type == ScalarType.FLOAT && Float.isInfinite((float) number)) {
            problem("Floating-point value is out of range.", value, OPTION_TYPE_ERROR);
          }
        }
        return;
      default:
        BigInteger number = integer(value);
        if (number == null) {
          problem(Messages.expectedInteger, value, OPTION_TYPE_ERROR);
          return;
        }
        // Unsigned types must be selected before the generic signed integer cases.
        BigInteger min = INT32_MIN;
        BigInteger max = INT32_MAX;
        switch (type) {
          case UINT32: case FIXED32: min = BigInteger.ZERO; max = UINT32_MAX; break;
          case UINT64: case FIXED64: min = BigInteger.ZERO; max = UINT64_MAX; break;
          case INT64: case SINT64: case SFIXED64: min = INT64_MIN; max = INT64_MAX; break;
          default: break;
        }
        if (number.compareTo(min) < 0 || number.compareTo(max) > 0) {
          problem("Integer value is out of range for " + type.getLiteral() + ".", value, OPTION_TYPE_ERROR);
        }
    }
  }

  private boolean isBoolean(Value value, boolean aggregate) {
    BigInteger number = integer(value);
    if (number != null) {
      return aggregate && (BigInteger.ZERO.equals(number) || BigInteger.ONE.equals(number));
    }
    if (!(value instanceof LiteralLink)) {
      return false;
    }
    LiteralLink link = (LiteralLink) value;
    Literal target = link.getTarget();
    if (target == null || target.eIsProxy()) {
      return true; // The linker reports unavailable spellings; do not turn recovery into another error.
    }
    if (!(target instanceof BuiltinLiteral) || link.isIsNegative()
        || !("true".equals(target.getName()) || "false".equals(target.getName()))) {
      return false;
    }
    String spelling = literalSpelling(link);
    return "true".equals(spelling) || "false".equals(spelling)
        || aggregate && ("True".equals(spelling) || "False".equals(spelling)
            || "t".equals(spelling) || "f".equals(spelling));
  }

  private boolean isFloating(Value value, boolean aggregate) {
    if (integer(value) != null) {
      return true;
    }
    if (value instanceof DoubleLink) {
      String text = sourceText(value);
      return aggregate || !(text.endsWith("f") || text.endsWith("F"));
    }
    if (!(value instanceof LiteralLink)) {
      return false;
    }
    LiteralLink link = (LiteralLink) value;
    Literal target = link.getTarget();
    if (target == null || target.eIsProxy()) {
      return true;
    }
    if (!(target instanceof BuiltinLiteral)
        || !("inf".equals(target.getName()) || "infinity".equals(target.getName()) || "nan".equals(target.getName()))) {
      return false;
    }
    String spelling = literalSpelling(link);
    return aggregate ? "inf".equalsIgnoreCase(spelling) || "infinity".equalsIgnoreCase(spelling)
        || "nan".equalsIgnoreCase(spelling) : "inf".equals(spelling) || "nan".equals(spelling);
  }

  private String literalSpelling(LiteralLink link) {
    List<INode> nodes = NodeModelUtils.findNodesForFeature(link, ProtobufPackage.Literals.LITERAL_LINK__TARGET);
    return nodes.isEmpty() ? link.getTarget().getName() : NodeModelUtils.getTokenText(nodes.get(0));
  }

  private BigInteger integer(Value value) {
    return value instanceof LongLink ? ((LongLink) value).getTarget()
        : value instanceof HexNumberLink ? ((HexNumberLink) value).getTarget() : null;
  }

  private boolean isMessage(EObject type) {
    return type instanceof Message || type instanceof Group || type instanceof MapType;
  }

  private boolean isList(ValueField field) {
    return field instanceof SimpleValueField ? ((SimpleValueField) field).isIsList()
        : field instanceof ComplexValueField && ((ComplexValueField) field).isIsList();
  }

  private boolean hasColon(ValueField field) {
    return field instanceof SimpleValueField ? ((SimpleValueField) field).isHasColon()
        : field instanceof ComplexValueField && ((ComplexValueField) field).isHasColon();
  }

  private List<? extends Value> fieldValues(ValueField field) {
    return field instanceof SimpleValueField ? ((SimpleValueField) field).getValues()
        : ((ComplexValueField) field).getValues();
  }

  /** Each source option owner is one assignment context, not each individual option statement. */
  @Check public void checkAssignmentOwner(EObject owner) {
    if (!(owner instanceof Protobuf || owner instanceof Message || owner instanceof Group || owner instanceof OneOf
        || owner instanceof Enum || owner instanceof Literal || owner instanceof Extensions || owner instanceof Rpc
        || owner instanceof Stream || owner instanceof Service || owner instanceof MessageField)) {
      return;
    }
    Assignment assignments = new Assignment();
    Set<Class<?>> pseudoOptions = new HashSet<Class<?>>();
    for (AbstractOption option : optionsOf(owner)) {
      if (option instanceof DefaultValueFieldOption || option instanceof JsonNameFieldOption) {
        if (!pseudoOptions.add(option.getClass())) {
          duplicate(option);
        }
        continue;
      }
      List<IndexedElement> path = optionPath(option);
      if (path.isEmpty()) {
        continue;
      }
      Assignment current = assignments;
      for (int i = 0; i < path.size(); i++) {
        IndexedElement declaration = path.get(i);
        if (values.isRepeated(declaration) && i + 1 < path.size()) {
          problem("Repeated message options must be initialized using an aggregate.", option, OPTION_CONTEXT_ERROR);
          current = null;
          break;
        }
        current = assign(current, declaration, option, i + 1 == path.size());
      }
      if (current != null && option.getValue() instanceof ComplexValue) {
        collectAssignments((ComplexValue) option.getValue(), current);
      }
    }
  }

  private void collectAssignments(ComplexValue aggregate, Assignment context) {
    for (ValueField field : aggregate.getFields()) {
      if (field.getName() instanceof AnyFieldName) {
        EObject ownerType = values.expectedType(aggregate);
        if (ownerType instanceof Message && "google.protobuf.Any".equals(qualifiedName(ownerType))) {
          // Expanded Any assigns these two real fields implicitly, including across dotted option paths.
          for (MessageField implicit : messageFields.fieldsOf((Message) ownerType)) {
            if ("type_url".equals(implicit.getName()) || "value".equals(implicit.getName())) {
              assign(context, implicit, field, true);
            }
          }
        }
      }
      IndexedElement declaration = values.declarationOf(field);
      if (declaration == null && !(field.getName() instanceof AnyFieldName)) {
        continue;
      }
      Assignment entry = declaration == null ? new Assignment() : assign(context, declaration, field, true);
      // Record nested paths so a later dotted option cannot silently reassign a singular member.
      for (Value value : fieldValues(field)) {
        if (value instanceof ComplexValue) {
          collectAssignments((ComplexValue) value,
              values.isRepeated(declaration) ? new Assignment() : entry);
        }
      }
    }
  }

  private Assignment assign(Assignment parent, IndexedElement declaration, EObject source, boolean terminal) {
    if (declaration.eContainer() instanceof OneOf) {
      OneOf oneof = (OneOf) declaration.eContainer();
      IndexedElement previous = parent.oneofs.put(oneof, declaration);
      if (previous != null && previous != declaration) {
        problem("Fields in the same oneof cannot both be assigned.", source, OPTION_ASSIGNMENT_ERROR);
      }
    }
    if (values.isRepeated(declaration)) {
      return new Assignment(); // Every repeated aggregate (including a map entry) is a separate element.
    }
    Assignment entry = parent.fields.get(declaration);
    if (entry == null) {
      entry = new Assignment();
      parent.fields.put(declaration, entry);
    }
    if (terminal) {
      if (entry.assigned) {
        duplicate(source);
      }
      entry.assigned = true;
    }
    return entry;
  }

  private static final class Assignment {
    boolean assigned;
    final Map<IndexedElement, Assignment> fields = new HashMap<IndexedElement, Assignment>();
    final Map<OneOf, IndexedElement> oneofs = new HashMap<OneOf, IndexedElement>();
  }

  private void duplicate(EObject source) {
    problem("Option or singular field was already set.", source, OPTION_ASSIGNMENT_ERROR);
  }

  private List<IndexedElement> optionPath(AbstractOption option) {
    List<IndexedElement> path = new ArrayList<IndexedElement>();
    IndexedElement root = options.rootSourceOf(option);
    if (!resolved(root)) {
      return path;
    }
    path.add(root);
    for (OptionField field : options.fieldsOf(option)) {
      IndexedElement target = field instanceof MessageOptionField ? ((MessageOptionField) field).getTarget()
          : ((ExtensionOptionField) field).getTarget();
      if (!resolved(target)) {
        path.clear();
        return path;
      }
      if ((field instanceof ExtensionOptionField) != (target.eContainer() instanceof TypeExtension)) {
        problem("Extension option fields require parenthesized notation.", field, OPTION_CONTEXT_ERROR);
      }
      path.add(target);
    }
    return path;
  }

  private List<AbstractOption> optionsOf(EObject owner) {
    List<AbstractOption> result = new ArrayList<AbstractOption>();
    for (EObject child : owner.eContents()) {
      if (child instanceof AbstractOption) {
        result.add((AbstractOption) child);
      }
    }
    return result;
  }

  private void checkOptionContext(AbstractOption option) {
    IndexedElement root = options.rootSourceOf(option);
    if (!resolved(root)) {
      return;
    }
    boolean extension = root.eContainer() instanceof TypeExtension;
    if (extension == options.isNative(option)) {
      problem(extension ? "Custom options require parenthesized notation." : "This option is not an extension.",
          option, OPTION_CONTEXT_ERROR);
      return;
    }
    EObject extendee = extension ? extendee((TypeExtension) root.eContainer()) : root.eContainer();
    String expectedOwner = optionMessageName(option);
    if (expectedOwner != null && !isDescriptorType(extendee, expectedOwner)) {
      problem("Option does not extend the applicable " + expectedOwner + ".", option, OPTION_CONTEXT_ERROR);
    }
    boolean feature = isEditionFeatureOption(option);
    List<IndexedElement> path = optionPath(option);
    for (int i = 0; i < path.size(); i++) {
      IndexedElement declaration = path.get(i);
      boolean terminal = i + 1 == path.size();
      if (!feature) {
        checkTargets(declaration, option.eContainer(), option);
      }
      if (!feature || terminal && !isMessage(values.typeOf(declaration))) {
        checkSupport(declaration, terminal ? option.getValue() : null, option);
      }
      if (terminal) {
        checkLegacyEditionOption(declaration, option.getValue(), option, false);
      }
      checkReservedNativeDeclaration(declaration, option);
    }
    if (!extension) {
      String name = root instanceof MessageField ? ((MessageField) root).getName() : "";
      if ((option.eContainer() instanceof MessageField || option.eContainer() instanceof Group)
          && isDescriptorType(extendee, "FieldOptions")) {
        checkFieldOption((IndexedElement) option.eContainer(), name, option);
      }
    }
  }

  private void checkReservedNativeDeclaration(IndexedElement declaration, EObject source) {
    if (!(declaration instanceof MessageField)) return;
    String name = ((MessageField) declaration).getName();
    EObject owner = declaration.eContainer();
    if ("features".equals(name) && (isDescriptorType(owner, "FileOptions")
        || isDescriptorType(owner, "MessageOptions") || isDescriptorType(owner, "FieldOptions")
        || isDescriptorType(owner, "EnumOptions") || isDescriptorType(owner, "EnumValueOptions")
        || isDescriptorType(owner, "ServiceOptions") || isDescriptorType(owner, "MethodOptions")
        || isDescriptorType(owner, "OneofOptions") || isDescriptorType(owner, "ExtensionRangeOptions"))
        && !(syntaxes.isEditions(source) && features.isFeaturesRoot(declaration, source))) {
      problem("Features are only valid under Editions, not proto2 or proto3.", source, OPTION_CONTEXT_ERROR);
    }
    if ("map_entry".equals(name) && isDescriptorType(declaration.eContainer(), "MessageOptions")) {
      problem("map_entry should not be set explicitly. Use map<KeyType, ValueType> instead.", source, OPTION_CONTEXT_ERROR);
    }
  }

  private boolean isEditionFeatureOption(EObject source) {
    AbstractOption option = enclosingOption(source);
    return syntaxes.isEditions(source) && option != null && options.isNative(option)
        && features.isFeaturesRoot(options.rootSourceOf(option), source);
  }

  private void checkSupport(IndexedElement declaration, Value value, EObject source) {
    if (!syntaxes.isEditions(source) || !resolved(declaration)
        || isEditionFeatureOption(source) && !features.hasRequiredMetadata(source)) return;
    Literal literal = value instanceof LiteralLink ? ((LiteralLink) value).getTarget() : null;
    EObject owner = enclosingOptionOwner(source);
    String error = features.supportError(declaration, literal, owner);
    if (error != null) {
      problem(error, source, EditionValidator.FEATURE_SUPPORT);
    } else {
      String message = features.deprecationWarning(declaration, literal, owner);
      if (message != null) warning(message, source, null, EditionValidator.FEATURE_DEPRECATED);
    }
  }

  private void checkLegacyEditionOption(IndexedElement declaration, Value value, EObject source, boolean aggregate) {
    if (!syntaxes.isEditions(source) || !(declaration instanceof MessageField)) return;
    String name = ((MessageField) declaration).getName();
    if ("packed".equals(name) && isDescriptorType(declaration.eContainer(), "FieldOptions")) {
      problem(Messages.editionPacked, source, EditionValidator.EDITION_FEATURE);
    }
    if ("java_string_check_utf8".equals(name) && isDescriptorType(declaration.eContainer(), "FileOptions")
        && isBoolean(value, aggregate) && values.booleanValue(value)) {
      problem(Messages.editionJavaUtf8, source, EditionValidator.EDITION_FEATURE);
    }
  }

  private void checkFieldOption(IndexedElement field, String name, AbstractOption option) {
    Value value = option.getValue();
    boolean enabled = isBoolean(value, false) && values.booleanValue(value);
    if ("packed".equals(name) && !syntaxes.isEditions(option) && enabled
        && (!(field instanceof MessageField) || !messageFields.isPackable((MessageField) field))) {
      problem("[packed = true] can only be specified for repeated primitive fields.", option, OPTION_CONTEXT_ERROR);
    }
    if (("lazy".equals(name) || "unverified_lazy".equals(name)) && enabled) {
      if (!(values.typeOf(field) instanceof Message)) {
        problem("[lazy = true] can only be specified for submessage fields.", option, OPTION_CONTEXT_ERROR);
      }
      if ("unverified_lazy".equals(name) && field.eContainer() instanceof TypeExtension) {
        problem("Extensions cannot be marked as unverified_lazy.", option, OPTION_CONTEXT_ERROR);
      }
    }
    if ("jstype".equals(name) && enumName(value) != null && !"JS_NORMAL".equals(enumName(value))) {
      EObject type = values.typeOf(field);
      ScalarType scalar = type instanceof ScalarTypeLink ? ((ScalarTypeLink) type).getTarget() : null;
      if (!(scalar == ScalarType.INT64 || scalar == ScalarType.UINT64 || scalar == ScalarType.SINT64
          || scalar == ScalarType.FIXED64 || scalar == ScalarType.SFIXED64)) {
        problem("jstype is only allowed on int64, uint64, sint64, fixed64 or sfixed64 fields.", option, OPTION_CONTEXT_ERROR);
      }
    }
  }

  private String optionMessageName(AbstractOption option) {
    EObject owner = option.eContainer();
    if (owner instanceof Group && option instanceof Option) return "MessageOptions";
    if (owner instanceof Protobuf) return "FileOptions";
    if (owner instanceof Message) return "MessageOptions";
    if (owner instanceof OneOf) return "OneofOptions";
    if (owner instanceof MessageField || owner instanceof Group) return "FieldOptions";
    if (owner instanceof Enum) return "EnumOptions";
    if (owner instanceof Literal) return "EnumValueOptions";
    if (owner instanceof Extensions) return "ExtensionRangeOptions";
    if (owner instanceof Rpc) return "MethodOptions";
    if (owner instanceof Stream) return "StreamOptions";
    if (owner instanceof Service) return "ServiceOptions";
    return null;
  }

  private String targetName(EObject owner, EObject source) {
    if (owner instanceof Group && enclosingOption(source) instanceof Option) return "TARGET_TYPE_MESSAGE";
    if (owner instanceof Protobuf) return "TARGET_TYPE_FILE";
    if (owner instanceof Extensions) return "TARGET_TYPE_EXTENSION_RANGE";
    if (owner instanceof Message) return "TARGET_TYPE_MESSAGE";
    if (owner instanceof MessageField || owner instanceof Group) return "TARGET_TYPE_FIELD";
    if (owner instanceof OneOf) return "TARGET_TYPE_ONEOF";
    if (owner instanceof Enum) return "TARGET_TYPE_ENUM";
    if (owner instanceof Literal) return "TARGET_TYPE_ENUM_ENTRY";
    if (owner instanceof Service) return "TARGET_TYPE_SERVICE";
    if (owner instanceof Rpc || owner instanceof Stream) return "TARGET_TYPE_METHOD";
    return null;
  }

  private void checkTargets(IndexedElement declaration, EObject owner, EObject source) {
    String target = targetName(owner, source);
    if (target == null) return;
    if (!features.allowsTarget(declaration, owner, source)) {
      problem("Option field is not allowed on this declaration kind (" + target + ").", source, OPTION_CONTEXT_ERROR);
    }
  }

  private EObject enclosingOptionOwner(EObject source) {
    for (EObject current = source; current != null; current = current.eContainer()) {
      if (current instanceof AbstractOption) return current.eContainer();
    }
    return null;
  }

  private AbstractOption enclosingOption(EObject source) {
    for (EObject current = source; current != null; current = current.eContainer()) {
      if (current instanceof AbstractOption) return (AbstractOption) current;
    }
    return null;
  }

  private boolean nativeOption(AbstractOption option, String owner, String name) {
    IndexedElement declaration = options.rootSourceOf(option);
    return options.isNative(option) && declaration instanceof MessageField && !declaration.eIsProxy()
        && isDescriptorType(declaration.eContainer(), owner) && name.equals(((MessageField) declaration).getName());
  }

  private boolean isDescriptorType(EObject type, String name) {
    String qualified = qualifiedName(type);
    return ("google.protobuf." + name).equals(qualified) || ("proto2." + name).equals(qualified);
  }

  private String qualifiedName(EObject object) {
    if (object == null || object.eIsProxy()) return null;
    QualifiedName name = names.getFullyQualifiedName(object);
    return name == null ? null : name.toString();
  }

  private boolean resolved(EObject object) {
    return object != null && !object.eIsProxy();
  }

  private EObject extendee(TypeExtension extension) {
    return extension.getType() == null ? null : extension.getType().getTarget();
  }

  private String enumName(Value value) {
    Literal literal = value instanceof LiteralLink ? ((LiteralLink) value).getTarget() : null;
    return resolved(literal) && !(literal instanceof BuiltinLiteral) ? literal.getName() : null;
  }

  private String sourceText(EObject object) {
    INode node = NodeModelUtils.getNode(object);
    return node == null ? "" : NodeModelUtils.getTokenText(node).trim();
  }

  private void problem(String message, EObject object, String code) {
    if (object instanceof Value && object.eContainer() instanceof AbstractOption) {
      error(message, object.eContainer(), ProtobufPackage.Literals.ABSTRACT_OPTION__VALUE, code);
    } else {
      error(message, object, null, code);
    }
  }

  private void checkJsonName(JsonNameFieldOption option) {
    EObject owner = option.eContainer();
    if (!(owner instanceof MessageField || owner instanceof Group)) {
      problem("The json_name option is only allowed on fields.", option, OPTION_CONTEXT_ERROR);
    } else if (owner.eContainer() instanceof TypeExtension) {
      problem("option json_name is not allowed on extension fields.", option, JSON_NAME_ERROR);
    }
    if (!(option.getValue() instanceof StringLink)) {
      problem(Messages.expectedString, option, EXPECTED_STRING_ERROR);
      return;
    }
    String name = stringValue(option.getValue());
    if (name != null && name.indexOf('\0') >= 0) {
      problem("json_name cannot have embedded null characters.", option, JSON_NAME_ERROR);
    }
    if (name != null && name.startsWith("[") && name.endsWith("]")) {
      problem("JSON names may not start with '[' and end with ']'.", option, JSON_NAME_ERROR);
    }
  }

  @Check public void checkJsonNames(Message message) {
    List<IndexedElement> fields = new ArrayList<IndexedElement>(messageFields.fieldsOf(message));
    for (MessageElement element : message.getElements()) {
      if (element instanceof Group) fields.add((Group) element);
      if (element instanceof OneOf) {
        for (MessageElement member : ((OneOf) element).getElements()) {
          if (member instanceof Group) fields.add((Group) member);
        }
      }
    }
    checkJsonNamesIn(message, fields);
  }

  @Check public void checkJsonNames(Group group) {
    List<IndexedElement> fields = new ArrayList<IndexedElement>();
    for (EObject element : group.eContents()) {
      if (element instanceof MessageField || element instanceof Group) fields.add((IndexedElement) element);
    }
    checkJsonNamesIn(group, fields);
  }

  private void checkJsonNamesIn(EObject owner, List<IndexedElement> fields) {
    for (AbstractOption option : optionsOf(owner)) {
      if (nativeOption(option, "MessageOptions", "deprecated_legacy_json_field_conflicts")
          && isBoolean(option.getValue(), false) && values.booleanValue(option.getValue())) return;
    }
    String jsonFormat = syntaxes.isEditions(owner) ? features.coreValue(owner, "json_format") : null;
    if (syntaxes.isEditions(owner) && jsonFormat == null) return;
    boolean legacy = syntaxes.isProto2(owner) || "LEGACY_BEST_EFFORT".equals(jsonFormat);
    checkJsonNames(owner, fields, false, legacy);
    checkJsonNames(owner, fields, true, legacy);
  }

  private void checkJsonNames(EObject owner, List<IndexedElement> fields, boolean overrides, boolean legacy) {
    Map<String, IndexedElement> seen = new HashMap<String, IndexedElement>();
    for (IndexedElement field : fields) {
      String fieldName = jsonFieldName(field);
      if (fieldName == null) continue;
      String defaultName = defaultJsonName(fieldName);
      String name = overrides ? jsonName(field, defaultName) : defaultName;
      if (name == null) continue;
      IndexedElement previous = seen.put(name, field);
      if (previous != null && (!overrides || !name.equals(defaultName)
          || !name.equals(defaultJsonName(jsonFieldName(previous))))) {
        // Legacy proto2 reports a warning if either colliding spelling is a default JSON name.
        boolean involvesDefault = !overrides || name.equals(defaultName)
            || name.equals(defaultJsonName(jsonFieldName(previous)));
        if (syntaxes.isEditions(owner) && hasCustomJsonName(field) && hasCustomJsonName(previous)) {
          String customName = jsonName(field, defaultName);
          if (customName != null
              && customName.equals(jsonName(previous, defaultJsonName(jsonFieldName(previous))))) {
            involvesDefault = false;
          }
        }
        if (involvesDefault && legacy) {
          warning("The JSON camel-case name conflicts with another field.", field, null, JSON_NAME_ERROR);
        } else {
          problem("The JSON name conflicts with another field.", field, JSON_NAME_ERROR);
        }
      }
    }
  }

  private String defaultJsonName(String name) {
    StringBuilder result = new StringBuilder(name.length());
    boolean upper = false;
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (c == '_') upper = true;
      else {
        result.append(upper && c >= 'a' && c <= 'z' ? (char) (c - 'a' + 'A') : c);
        upper = false;
      }
    }
    return result.toString();
  }

  private String jsonName(IndexedElement field, String defaultName) {
    for (AbstractOption option : optionsOf(field)) {
      if (option instanceof JsonNameFieldOption) return stringValue(option.getValue());
    }
    return defaultName;
  }

  private boolean hasCustomJsonName(IndexedElement field) {
    for (EObject child : field.eContents()) {
      if (child instanceof JsonNameFieldOption) return true;
    }
    return false;
  }

  private String jsonFieldName(IndexedElement field) {
    if (field instanceof MessageField) return ((MessageField) field).getName();
    String name = ((Group) field).getName();
    return name == null ? null : name.toLowerCase(java.util.Locale.ROOT);
  }

  private String stringValue(Value value) {
    return value instanceof StringLink ? strings.getCombinedString(((StringLink) value).getTarget()) : null;
  }

  @Check public void checkExtensionDeclarations(Message message) {
    checkExtensionDeclarationsIn(message);
  }

  @Check public void checkExtensionDeclarations(Group group) {
    checkExtensionDeclarationsIn(group);
  }

  private void checkExtensionDeclarationsIn(EObject owner) {
    Map<BigInteger, ComplexValue> numbers = new HashMap<BigInteger, ComplexValue>();
    Set<String> fullNames = new HashSet<String>();
    for (EObject element : owner.eContents()) {
      if (!(element instanceof Extensions)) continue;
      Extensions extension = (Extensions) element;
      List<ComplexValue> declarations = declarations(extension);
      if (!declarations.isEmpty()) {
        for (AbstractOption option : optionsOf(extension)) {
          if (nativeOption(option, "ExtensionRangeOptions", "verification")
              && "UNVERIFIED".equals(enumName(option.getValue()))) {
            problem("Cannot mark the extension range as UNVERIFIED when it has extensions declared.", option,
                EXTENSION_DECLARATION_ERROR);
          }
        }
      }
      for (ComplexValue declaration : declarations) {
        Map<String, Value> members = declarationMembers(declaration);
        BigInteger number = integer(members.get("number"));
        if (number == null) number = BigInteger.ZERO;
        for (IndexRange range : extension.getRanges()) {
          try {
            com.google.common.collect.Range<Long> bounds = ranges.toLongRange(range);
            if (number.compareTo(INT64_MIN) < 0 || number.compareTo(INT64_MAX) > 0
                || !bounds.contains(number.longValue())) {
              problem("Extension declaration number is not in the extension range.", declaration, EXTENSION_DECLARATION_ERROR);
            }
          } catch (IndexRanges.BackwardsRangeException e) {
            // The range validator reports the invalid range itself.
          }
        }
        if (numbers.put(number, declaration) != null) {
          problem("Extension declaration number is declared multiple times.", declaration, EXTENSION_DECLARATION_ERROR);
        }
        String fullName = stringValue(members.get("full_name"));
        String type = stringValue(members.get("type"));
        boolean reserved = isBoolean(members.get("reserved"), true) && values.booleanValue(members.get("reserved"));
        if ((fullName == null) != (type == null) || fullName == null && !reserved) {
          problem("Extension declaration should have both full_name and type set.", declaration, EXTENSION_DECLARATION_ERROR);
        }
        if (fullName != null) {
          if (!QUALIFIED_SYMBOL.matcher(fullName).matches()) {
            problem("Extension declaration full_name must be a fully qualified name with a leading dot.", declaration,
                EXTENSION_DECLARATION_ERROR);
          }
          if (!fullNames.add(fullName)) {
            problem("Extension field name is declared multiple times.", declaration, EXTENSION_DECLARATION_ERROR);
          }
        }
        if (type != null && !scalarName(type) && !QUALIFIED_SYMBOL.matcher(type).matches()) {
          problem("Extension declaration type must be a scalar type or a fully qualified name with a leading dot.",
              declaration, EXTENSION_DECLARATION_ERROR);
        }
      }
    }
  }

  private List<ComplexValue> declarations(Extensions extension) {
    List<ComplexValue> result = new ArrayList<ComplexValue>();
    for (AbstractOption option : optionsOf(extension)) {
      if (nativeOption(option, "ExtensionRangeOptions", "declaration") && option.getValue() instanceof ComplexValue) {
        result.add((ComplexValue) option.getValue());
      }
    }
    return result;
  }

  private Map<String, Value> declarationMembers(ComplexValue declaration) {
    Map<String, Value> result = new LinkedHashMap<String, Value>();
    for (ValueField field : declaration.getFields()) {
      IndexedElement target = values.declarationOf(field);
      if (target instanceof MessageField && !fieldValues(field).isEmpty()) {
        result.put(((MessageField) target).getName(), fieldValues(field).get(0));
      }
    }
    return result;
  }

  private boolean scalarName(String name) {
    return ScalarType.get(name) != null;
  }

  @Check public void checkAvailableExtensionDeclaration(MessageField field) {
    if (!(field.eContainer() instanceof TypeExtension)) return;
    EObject target = extendee((TypeExtension) field.eContainer());
    if (!(target instanceof Message || target instanceof Group) || target.eIsProxy()) return;
    for (EObject element : target.eContents()) {
      if (!(element instanceof Extensions)) continue;
      Extensions extension = (Extensions) element;
      boolean contains = false;
      for (IndexRange range : extension.getRanges()) {
        try {
          contains |= ranges.toLongRange(range).contains(field.getIndex());
        } catch (IndexRanges.BackwardsRangeException e) {
          // Reported by the range validator.
        }
      }
      if (!contains) continue;
      List<ComplexValue> declarations = declarations(extension);
      boolean required = !declarations.isEmpty();
      for (AbstractOption option : optionsOf(extension)) {
        if (nativeOption(option, "ExtensionRangeOptions", "verification")) {
          required |= "DECLARATION".equals(enumName(option.getValue()));
        }
      }
      for (ComplexValue declaration : declarations) {
        Map<String, Value> members = declarationMembers(declaration);
        if (!BigInteger.valueOf(field.getIndex()).equals(integer(members.get("number")))) continue;
        if (isBoolean(members.get("reserved"), true) && values.booleanValue(members.get("reserved"))) {
          problem("This extension number is reserved in the extension declarations.", field, EXTENSION_DECLARATION_ERROR);
          return;
        }
        String declaredName = stringValue(members.get("full_name"));
        String actualName = qualifiedName(field);
        if (declaredName != null && actualName != null && !declaredName.equals("." + actualName)) {
          problem("Extension name does not match its declaration.", field, EXTENSION_DECLARATION_ERROR);
        }
        String declaredType = stringValue(members.get("type"));
        EObject type = values.typeOf(field);
        String actualType = type instanceof ScalarTypeLink ? ((ScalarTypeLink) type).getTarget().getLiteral()
            : type == null ? null : "." + qualifiedName(type);
        if (declaredType != null && actualType != null && !declaredType.equals(actualType)) {
          problem("Extension type does not match its declaration.", field, EXTENSION_DECLARATION_ERROR);
        }
        boolean repeated = isBoolean(members.get("repeated"), true) && values.booleanValue(members.get("repeated"));
        if (repeated != values.isRepeated(field)) {
          problem("Extension cardinality does not match its declaration.", field, EXTENSION_DECLARATION_ERROR);
        }
        return;
      }
      if (required) {
        problem("Missing extension declaration for this field number.", field, EXTENSION_DECLARATION_ERROR);
      }
      return;
    }
  }
}
