package com.google.eclipse.protobuf.validation;

import static java.lang.String.format;

import java.math.BigInteger;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.validation.AbstractDeclarativeValidator;
import org.eclipse.xtext.validation.Check;
import org.eclipse.xtext.validation.EValidatorRegistrar;

import com.google.common.collect.Range;
import com.google.eclipse.protobuf.model.util.FeatureSets;
import com.google.eclipse.protobuf.model.util.FeatureSets.Assignment;
import com.google.eclipse.protobuf.model.util.IndexRanges;
import com.google.eclipse.protobuf.model.util.IndexRanges.BackwardsRangeException;
import com.google.eclipse.protobuf.model.util.MessageFields;
import com.google.eclipse.protobuf.model.util.OptionValues;
import com.google.eclipse.protobuf.model.util.Options;
import com.google.eclipse.protobuf.model.util.Syntaxes;
import com.google.eclipse.protobuf.naming.NameResolver;
import com.google.eclipse.protobuf.protobuf.AbstractOption;
import com.google.eclipse.protobuf.protobuf.Edition;
import com.google.eclipse.protobuf.protobuf.Group;
import com.google.eclipse.protobuf.protobuf.HexNumberLink;
import com.google.eclipse.protobuf.protobuf.IndexRange;
import com.google.eclipse.protobuf.protobuf.IndexedElement;
import com.google.eclipse.protobuf.protobuf.Literal;
import com.google.eclipse.protobuf.protobuf.LiteralLink;
import com.google.eclipse.protobuf.protobuf.LongLink;
import com.google.eclipse.protobuf.protobuf.MapType;
import com.google.eclipse.protobuf.protobuf.MapTypeLink;
import com.google.eclipse.protobuf.protobuf.Message;
import com.google.eclipse.protobuf.protobuf.MessageField;
import com.google.eclipse.protobuf.protobuf.ModifierEnum;
import com.google.eclipse.protobuf.protobuf.OneOf;
import com.google.eclipse.protobuf.protobuf.OptionField;
import com.google.eclipse.protobuf.protobuf.Package;
import com.google.eclipse.protobuf.protobuf.Protobuf;
import com.google.eclipse.protobuf.protobuf.Reserved;
import com.google.eclipse.protobuf.protobuf.ReservedEnum;
import com.google.eclipse.protobuf.protobuf.ReservedName;
import com.google.eclipse.protobuf.protobuf.Rpc;
import com.google.eclipse.protobuf.protobuf.ScalarType;
import com.google.eclipse.protobuf.protobuf.ScalarTypeLink;
import com.google.eclipse.protobuf.protobuf.Service;
import com.google.eclipse.protobuf.protobuf.Stream;
import com.google.eclipse.protobuf.protobuf.StringLiteral;
import com.google.eclipse.protobuf.protobuf.SymbolVisibilityEnum;
import com.google.eclipse.protobuf.protobuf.ValueField;
import com.google.inject.Inject;

/** Source-language rules for the two supported Editions; legacy rules retain their existing owners. */
public class EditionValidator extends AbstractDeclarativeValidator {
  public static final String UNSUPPORTED_EDITION = "unsupportedEdition";
  public static final String EDITION_FIELD_MODIFIER = "editionFieldModifier";
  public static final String EDITION_LEGACY_SYNTAX = "editionLegacySyntax";
  public static final String EDITION_RESERVED_NAME = "editionReservedName";
  public static final String EDITION_DEFAULT = "editionDefault";
  public static final String EDITION_FEATURE = "editionFeature";
  public static final String FEATURE_SUPPORT = "featureSupport";
  public static final String FEATURE_DEPRECATED = "featureDeprecated";
  public static final String EDITION_IMPORT = "editionImport";
  public static final String EDITION_VISIBILITY = "editionVisibility";
  public static final String EDITION_NAMING_STYLE = "editionNamingStyle";
  public static final String INVALID_FEATURE_DEFINITION = "invalidFeatureDefinition";
  public static final String EDITIONS_FEATURES_UNAVAILABLE = "editionsFeaturesUnavailable";

  @Inject private Syntaxes syntaxes;
  @Inject private FeatureSets features;
  @Inject private OptionValues values;
  @Inject private Options options;
  @Inject private MessageFields fields;
  @Inject private IndexRanges ranges;
  @Inject private NameResolver names;

  @Override public void register(EValidatorRegistrar registrar) {}

  @Check public void checkEdition(Edition edition) {
    String name = syntaxes.getName(edition);
    if (name == null || hasSyntaxError(edition)) return;
    if (name.isEmpty()) {
      error(Messages.expectedEditionIdentifier, edition, attribute(edition, "name"), "invalidStringLiteral");
      return;
    }
    if (!Syntaxes.EDITION_2023.equals(name) && !Syntaxes.EDITION_2024.equals(name)) {
      error(format(Messages.unsupportedEdition, name), edition, attribute(edition, "name"), UNSUPPORTED_EDITION);
    }
  }

  @Check public void checkFeatureMetadata(Protobuf root) {
    if (syntaxes.isEditions(root) && !features.hasRequiredMetadata(root)) {
      error(Messages.editionsFeaturesUnavailable, root.getEdition(), attribute(root.getEdition(), "name"),
          EDITIONS_FEATURES_UNAVAILABLE);
    }
  }

  @Check public void checkFieldLabel(MessageField field) {
    if (!syntaxes.isEditions(field) || field.getType() instanceof MapTypeLink
        || field.eContainer() instanceof OneOf) return;
    if (field.getModifier() == ModifierEnum.OPTIONAL || field.getModifier() == ModifierEnum.REQUIRED) {
      error(format(Messages.editionFieldModifier, field.getModifier().getLiteral()), field,
          attribute(field, "modifier"), EDITION_FIELD_MODIFIER);
    }
  }

  @Check public void checkGroup(Group group) {
    if (syntaxes.isEditions(group)) error(Messages.editionGroup, group, attribute(group, "name"), EDITION_LEGACY_SYNTAX);
  }

  @Check public void checkStream(Stream stream) {
    if (syntaxes.isEditions(stream)) error(Messages.editionStream, stream, attribute(stream, "name"), EDITION_LEGACY_SYNTAX);
  }

  @Check public void checkReservedName(ReservedName name) {
    if (syntaxes.isProto2(name) || syntaxes.isProto3(name)) {
      error(Messages.editionReservedIdentifier, name, attribute(name, "name"), EDITION_RESERVED_NAME);
    }
  }

  @Check public void checkReservedString(StringLiteral literal) {
    if (syntaxes.isEditions(literal)
        && (literal.eContainer() instanceof Reserved || literal.eContainer() instanceof ReservedEnum)) {
      error(Messages.editionReservedString, literal, attribute(literal, "chunks"), EDITION_RESERVED_NAME);
    }
  }

  @Check public void checkFeatureDefinition(IndexedElement declaration) {
    String problem = features.invalidDefinition(declaration);
    if (problem != null) {
      error(format(Messages.invalidFeatureDefinition, problem), declaration, attribute(declaration, "name"),
          INVALID_FEATURE_DEFINITION);
    }
  }

  @Check public void checkFeatureAssignments(EObject owner) {
    if (!(owner instanceof Protobuf || owner instanceof Message || owner instanceof MessageField
        || owner instanceof com.google.eclipse.protobuf.protobuf.Enum || owner instanceof Literal
        || owner instanceof OneOf || owner instanceof Service || owner instanceof Rpc
        || owner instanceof Stream || owner instanceof Group
        || owner instanceof com.google.eclipse.protobuf.protobuf.Extensions)) return;
    if (!syntaxes.isEditions(owner) || !features.hasRequiredMetadata(owner)) return;
    for (Assignment assignment : features.assignmentsIn(owner)) checkAssignment(assignment);
  }

  private void checkAssignment(Assignment assignment) {
    List<IndexedElement> path = assignment.path;
    if (path.isEmpty()) return;
    LiteralLink link = assignment.value instanceof LiteralLink ? (LiteralLink) assignment.value : null;
    Literal literal = link == null ? null : link.getTarget();
    IndexedElement leaf = path.get(path.size() - 1);
    EObject type = values.typeOf(leaf);
    BigInteger number = assignment.value instanceof LongLink ? ((LongLink) assignment.value).getTarget()
        : assignment.value instanceof HexNumberLink ? ((HexNumberLink) assignment.value).getTarget() : null;
    boolean numericBoolean = assignment.source instanceof ValueField && type instanceof ScalarTypeLink
        && ((ScalarTypeLink) type).getTarget() == ScalarType.BOOL
        && (BigInteger.ZERO.equals(number) || BigInteger.ONE.equals(number));
    // Option typing/linking and support windows have their own diagnostics.
    if (!numericBoolean && (literal == null || literal.eIsProxy() || link.isIsNegative()) || type == null
        || type instanceof Message || type instanceof MapType
        || assignment.owner instanceof MessageField && values.typeOf((MessageField) assignment.owner) == null
        || features.invalidDefinition(leaf) != null) return;
    if (type instanceof com.google.eclipse.protobuf.protobuf.Enum && literal.eContainer() != type) return;
    if (features.supportError(leaf, literal, assignment.owner) != null) return;
    if (features.canAssign(assignment.owner, path, literal)) return;
    String name = names.nameOf(leaf);
    boolean core = path.equals(features.corePath(assignment.owner, name));
    String problem;
    if (!features.allowsTarget(leaf, assignment.owner, assignment.source)) {
      problem = format(Messages.editionFeatureTarget, name);
    } else if (literal != null && literal.getIndex() == 0 && type instanceof com.google.eclipse.protobuf.protobuf.Enum
        && (core || "JavaFeatures".equals(names.nameOf(leaf.eContainer())))) {
      problem = format(Messages.editionFeatureUnknown, literal.getName());
    } else if (core && "field_presence".equals(name)) {
      if (assignment.owner instanceof Protobuf) problem = Messages.editionFileRequired;
      else if ("IMPLICIT".equals(literal.getName()) && assignment.owner instanceof MessageField
          && !values.isRepeated((MessageField) assignment.owner)
          && !(assignment.owner.eContainer() instanceof OneOf)
          && !(assignment.owner.eContainer() instanceof com.google.eclipse.protobuf.protobuf.TypeExtension)
          && values.typeOf((MessageField) assignment.owner) instanceof com.google.eclipse.protobuf.protobuf.Enum) {
        Boolean open = features.isOpenEnum((com.google.eclipse.protobuf.protobuf.Enum)
            values.typeOf((MessageField) assignment.owner));
        if (open == null) return;
        if (Boolean.TRUE.equals(open)) return; // The forbidden default is owned by DataTypeValidator.
        problem = Messages.editionImplicitEnum;
      } else if ("IMPLICIT".equals(literal.getName()) && assignment.owner instanceof MessageField
          && values.typeOf((MessageField) assignment.owner) instanceof Message) problem = Messages.editionImplicitMessage;
      else if ("IMPLICIT".equals(literal.getName()) && assignment.owner instanceof MessageField
          && !values.isRepeated((MessageField) assignment.owner)
          && !(assignment.owner.eContainer() instanceof OneOf)
          && !(assignment.owner.eContainer() instanceof com.google.eclipse.protobuf.protobuf.TypeExtension)) return;
      else problem = Messages.editionPresenceField;
    } else if (core && "repeated_field_encoding".equals(name)) {
      problem = assignment.owner instanceof MessageField && values.isRepeated((MessageField) assignment.owner)
          && "PACKED".equals(literal.getName()) ? Messages.editionPackedType : Messages.editionRepeatedEncoding;
    } else if (core && "utf8_validation".equals(name)) problem = Messages.editionUtf8Field;
    else if (core && "message_encoding".equals(name)) problem = Messages.editionMessageEncoding;
    else if ("legacy_closed_enum".equals(name)) problem = Messages.editionJavaEnum;
    else if ("nest_in_file_class".equals(name)) problem = Messages.editionJavaTopLevel;
    else problem = format(Messages.editionFeature, name);
    EObject source = assignment.source;
    if (source instanceof ValueField) source = ((ValueField) source).getName();
    else if (source instanceof AbstractOption) {
      List<OptionField> optionFields = options.fieldsOf((AbstractOption) source);
      if (!optionFields.isEmpty()) source = optionFields.get(optionFields.size() - 1);
    }
    if (source == null) source = assignment.source;
    EStructuralFeature feature = attribute(source, "target");
    error(problem, source, feature, EDITION_FEATURE);
  }

  @Check public void checkImplicitEnumField(MessageField field) {
    if (!syntaxes.isEditions(field) || !features.hasRequiredMetadata(field)
        || values.isRepeated(field) || !"IMPLICIT".equals(features.coreValue(field, "field_presence"))) return;
    EObject type = values.typeOf(field);
    if (type instanceof com.google.eclipse.protobuf.protobuf.Enum
        && Boolean.FALSE.equals(features.isOpenEnum((com.google.eclipse.protobuf.protobuf.Enum) type))) {
      error(Messages.editionImplicitEnum, field, attribute(field, "type"), EDITION_FEATURE);
    }
  }

  @Check public void checkVisibility(EObject declaration) {
    SymbolVisibilityEnum visibility = visibility(declaration);
    if (visibility == null || visibility == SymbolVisibilityEnum.UNSPECIFIED) return;
    if (!syntaxes.isEdition2024(declaration)) {
      error(Messages.editionVisibilityYear, declaration, attribute(declaration, "visibility"), EDITION_VISIBILITY);
    } else if (visibility == SymbolVisibilityEnum.EXPORT && features.hasRequiredMetadata(declaration)
        && !allowsExport(declaration)) {
      error(Messages.editionStrictExport, declaration, attribute(declaration, "visibility"), EDITION_VISIBILITY);
    }
  }

  /** STRICT permits a nested exported enum only in a top-level, local enum namespace. */
  public boolean allowsExport(EObject declaration) {
    if (!(declaration instanceof Message || declaration instanceof com.google.eclipse.protobuf.protobuf.Enum)) return false;
    return allowsExport(declaration.eContainer(), declaration instanceof com.google.eclipse.protobuf.protobuf.Enum);
  }

  /** Checks a prospective declaration inside an existing source owner without creating model objects. */
  public boolean allowsExport(EObject owner, boolean enumeration) {
    if (!syntaxes.isEdition2024(owner) || !features.hasRequiredMetadata(owner)) return false;
    if (owner instanceof Protobuf) return true;
    if (!(owner instanceof Message)) return false;
    if (!"STRICT".equals(features.coreValue(owner, "default_symbol_visibility"))) return true;
    if (!enumeration) return false;
    Message parent = (Message) owner;
    if (!(parent.eContainer() instanceof Protobuf)) return false;
    SymbolVisibilityEnum visibility = parent.getVisibility();
    if (visibility == SymbolVisibilityEnum.EXPORT) return false;
    String defaultVisibility = features.coreValue(parent, "default_symbol_visibility");
    if (visibility != SymbolVisibilityEnum.LOCAL && !"LOCAL_ALL".equals(defaultVisibility)
        && !"STRICT".equals(defaultVisibility)) return false;
    IndexRange namespace = null;
    for (EObject child : parent.eContents()) if (child instanceof Reserved) {
      for (EObject reservation : ((Reserved) child).getReservations()) if (reservation instanceof IndexRange) {
        if (namespace != null) return false;
        namespace = (IndexRange) reservation;
      }
    }
    if (namespace == null) return false;
    try {
      Range<Long> range = ranges.toLongRange(namespace);
      return range.lowerEndpoint() == 1L && range.upperEndpoint() == IndexRanges.MAX_FIELD_NUMBER;
    } catch (BackwardsRangeException invalid) {
      return false; // Existing range validation owns this error.
    }
  }

  private SymbolVisibilityEnum visibility(EObject declaration) {
    if (declaration instanceof Message) return ((Message) declaration).getVisibility();
    if (declaration instanceof com.google.eclipse.protobuf.protobuf.Enum)
      return ((com.google.eclipse.protobuf.protobuf.Enum) declaration).getVisibility();
    return null;
  }

  @Check public void checkNamingStyle(EObject declaration) {
    boolean title = declaration instanceof Message || declaration instanceof com.google.eclipse.protobuf.protobuf.Enum
        || declaration instanceof Service || declaration instanceof Rpc;
    boolean lower = declaration instanceof MessageField || declaration instanceof OneOf || declaration instanceof Package;
    boolean upper = declaration instanceof Literal && declaration.eContainer() instanceof com.google.eclipse.protobuf.protobuf.Enum;
    if (!title && !lower && !upper || declaration.eContainer() instanceof MapType) return;
    if (!syntaxes.isEdition2024(declaration) || !features.hasRequiredMetadata(declaration)) return;
    String name = names.nameOf(declaration);
    if (name == null || name.isEmpty() || hasSyntaxError(declaration)) return;
    String style = features.coreValue(declaration, "enforce_naming_style");
    if (!"STYLE2024".equals(style) && !"STYLE2026".equals(style)) return;
    String expected = title ? Messages.editionTitleCase : lower ? Messages.editionLowerSnake : Messages.editionUpperSnake;
    String problem = validName(name, title, upper, declaration instanceof Package) ? null : expected;
    if (problem == null && "STYLE2026".equals(style)
        && (declaration instanceof MessageField || declaration instanceof OneOf)
        && generatedNameCollision(declaration, name)) problem = Messages.editionGeneratedName;
    if (problem != null) {
      error(format(Messages.editionNamingStyle, name, problem), declaration,
          attribute(declaration, declaration instanceof Package ? "importedNamespace" : "name"), EDITION_NAMING_STYLE);
    }
  }

  // Pinned v36.2 naming_style.cc: ASCII classification and underscore-followed-by-letter rule.
  private boolean validName(String name, boolean title, boolean upper, boolean dots) {
    char first = name.charAt(0);
    if (title || upper ? !isUpper(first) : !isLower(first)) return false;
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      boolean letter = title ? isUpper(c) || isLower(c) : upper ? isUpper(c) : isLower(c);
      if (letter || c >= '0' && c <= '9') continue;
      if (title) return false;
      if (c == '.' && dots) continue;
      if (c != '_' || i + 1 == name.length()
          || !(isUpper(name.charAt(i + 1)) || isLower(name.charAt(i + 1)))) return false;
    }
    return true;
  }

  private boolean generatedNameCollision(EObject declaration, String name) {
    if ("descriptor".equals(name)) return true;
    EObject owner = declaration.eContainer();
    if (owner instanceof OneOf) owner = owner.eContainer();
    if (!(owner instanceof Message)) return false;
    Message message = (Message) owner;
    if (name.startsWith("has_") && hasMember(message, name, 4, name.length() - 4)
        || name.startsWith("get_") && hasMember(message, name, 4, name.length() - 4)
        || name.startsWith("set_") && hasMember(message, name, 4, name.length() - 4)
        || name.startsWith("clear_") && hasMember(message, name, 6, name.length() - 6)) return true;
    return name.endsWith("_value") && hasMember(message, name, 0, name.length() - 6);
  }

  private boolean hasMember(Message message, String name, int start, int length) {
    for (MessageField field : fields.fieldsOf(message))
      if (matchesName(field.getName(), name, start, length)) return true;
    for (EObject member : message.eContents()) if (member instanceof OneOf
        && matchesName(((OneOf) member).getName(), name, start, length)) return true;
    return false;
  }

  private boolean matchesName(String member, String name, int start, int length) {
    return member != null && member.length() == length && name.regionMatches(start, member, 0, length);
  }

  private boolean isUpper(char c) { return c >= 'A' && c <= 'Z'; }
  private boolean isLower(char c) { return c >= 'a' && c <= 'z'; }

  private EStructuralFeature attribute(EObject object, String name) {
    return object == null ? null : object.eClass().getEStructuralFeature(name);
  }

  private boolean hasSyntaxError(EObject object) {
    EStructuralFeature feature = attribute(object, object instanceof Package ? "importedNamespace" : "name");
    if (feature == null) return false;
    for (INode node : NodeModelUtils.findNodesForFeature(object, feature))
      for (INode descendant : node.getAsTreeIterable())
        if (descendant.getSyntaxErrorMessage() != null) return true;
    return false;
  }
}
