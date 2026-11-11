package com.google.eclipse.protobuf.model.util;

import static java.lang.String.format;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.util.IResourceScopeCache;
import org.eclipse.xtext.util.Tuples;

import com.google.eclipse.protobuf.protobuf.AbstractOption;
import com.google.eclipse.protobuf.protobuf.BuiltinLiteral;
import com.google.eclipse.protobuf.protobuf.ComplexValue;
import com.google.eclipse.protobuf.protobuf.Extensions;
import com.google.eclipse.protobuf.protobuf.Group;
import com.google.eclipse.protobuf.protobuf.HexNumberLink;
import com.google.eclipse.protobuf.protobuf.IndexedElement;
import com.google.eclipse.protobuf.protobuf.Literal;
import com.google.eclipse.protobuf.protobuf.LongLink;
import com.google.eclipse.protobuf.protobuf.MapType;
import com.google.eclipse.protobuf.protobuf.MapTypeLink;
import com.google.eclipse.protobuf.protobuf.Message;
import com.google.eclipse.protobuf.protobuf.MessageField;
import com.google.eclipse.protobuf.protobuf.ModifierEnum;
import com.google.eclipse.protobuf.protobuf.OneOf;
import com.google.eclipse.protobuf.protobuf.Option;
import com.google.eclipse.protobuf.protobuf.OptionField;
import com.google.eclipse.protobuf.protobuf.Protobuf;
import com.google.eclipse.protobuf.protobuf.Rpc;
import com.google.eclipse.protobuf.protobuf.ScalarType;
import com.google.eclipse.protobuf.protobuf.ScalarTypeLink;
import com.google.eclipse.protobuf.protobuf.SimpleValueField;
import com.google.eclipse.protobuf.protobuf.Service;
import com.google.eclipse.protobuf.protobuf.Stream;
import com.google.eclipse.protobuf.protobuf.TypeExtension;
import com.google.eclipse.protobuf.protobuf.TypeLink;
import com.google.eclipse.protobuf.protobuf.Value;
import com.google.eclipse.protobuf.protobuf.ValueField;
import com.google.eclipse.protobuf.scoping.ProtoDescriptorProvider;
import com.google.eclipse.protobuf.scoping.ProtoDescriptorProvider.ProtoDescriptorInfo;
import com.google.inject.Inject;
import com.google.eclipse.protobuf.validation.Messages;
import com.google.inject.Singleton;

/** Typed Editions feature policy shared by validation, scopes and editor assistance. */
@Singleton
public class FeatureSets {
  @Inject private Options options;
  @Inject private OptionValues values;
  @Inject private Syntaxes syntaxes;
  @Inject private MessageFields fields;
  @Inject private FeatureMetadata metadata;
  @Inject private ProtoDescriptorProvider descriptors;
  @Inject private IResourceScopeCache cache;

  public static final class Assignment {
    public final EObject owner;
    public final List<IndexedElement> path;
    public final Value value;
    public final EObject source;

    public Assignment(EObject owner, List<IndexedElement> path, Value value, EObject source) {
      this.owner = owner;
      this.path = Collections.unmodifiableList(new ArrayList<IndexedElement>(path));
      this.value = value;
      this.source = source;
    }
  }

  private static final String[] REQUIRED = {"field_presence", "enum_type", "repeated_field_encoding",
      "utf8_validation", "message_encoding", "json_format"};
  private static final Set<String> OPTIONS_TYPES;
  static {
    Set<String> types = new HashSet<String>();
    Collections.addAll(types, "FileOptions", "MessageOptions", "FieldOptions", "EnumOptions",
        "EnumValueOptions", "OneofOptions", "ServiceOptions", "MethodOptions", "ExtensionRangeOptions");
    OPTIONS_TYPES = Collections.unmodifiableSet(types);
  }

  private static final class Descriptor {
    final Resource resource;
    final Message features;
    final com.google.eclipse.protobuf.protobuf.Enum editions;
    final Map<String, List<IndexedElement>> core;
    final List<IndexedElement> nativeRoots;
    Descriptor(Resource resource, Message features, com.google.eclipse.protobuf.protobuf.Enum editions,
        Map<String, List<IndexedElement>> core, List<IndexedElement> nativeRoots) {
      this.resource = resource;
      this.features = features;
      this.editions = editions;
      this.core = Collections.unmodifiableMap(core);
      this.nativeRoots = Collections.unmodifiableList(nativeRoots);
    }
  }

  private Protobuf root(EObject context) {
    if (context == null || context.eResource() == null) return null;
    for (EObject current = context; current != null; current = current.eContainer())
      if (current instanceof Protobuf) return (Protobuf) current;
    return null;
  }

  private Descriptor descriptor(EObject context) {
    if (context == null || context.eResource() == null || context.eResource().getResourceSet() == null) return null;
    Resource source = context.eResource();
    ProtoDescriptorInfo info = descriptors.primaryDescriptor(source);
    if (info == null || info.location == null) return null;
    final Resource selected;
    try {
      selected = source.getResourceSet().getResource(info.location, true);
    } catch (RuntimeException unavailable) {
      // An explicit unreadable descriptor remains authoritative. Linking owns its load diagnostic.
      return null;
    }
    if (selected == null || selected.getContents().isEmpty()) return null;
    return cache.get(Tuples.create(FeatureSets.class, "descriptor"), selected, () -> readDescriptor(selected));
  }

  private Descriptor readDescriptor(Resource resource) {
    Message featureSet = null;
    com.google.eclipse.protobuf.protobuf.Enum editions = null;
    TreeIterator<EObject> all = resource.getAllContents();
    while (all.hasNext()) {
      EObject object = all.next();
      if (object instanceof Message && metadata.descriptorType(object, "FeatureSet")) featureSet = (Message) object;
      if (object instanceof com.google.eclipse.protobuf.protobuf.Enum && metadata.descriptorType(object, "Edition"))
        editions = (com.google.eclipse.protobuf.protobuf.Enum) object;
    }
    Map<String, List<IndexedElement>> core = new LinkedHashMap<String, List<IndexedElement>>();
    if (featureSet != null) for (EObject child : featureSet.eContents()) {
      if (child instanceof MessageField) {
        String name = ((MessageField) child).getName();
        if (core.containsKey(name)) core.put(name, Collections.<IndexedElement>emptyList());
        else core.put(name, Collections.singletonList((IndexedElement) child));
      }
    }
    List<IndexedElement> nativeRoots = new ArrayList<IndexedElement>();
    all = resource.getAllContents();
    while (all.hasNext()) {
      EObject declaration = all.next();
      if (declaration instanceof MessageField && "features".equals(((MessageField) declaration).getName())) {
        for (String optionType : OPTIONS_TYPES) {
          if (metadata.descriptorType(declaration.eContainer(), optionType)) {
            nativeRoots.add((IndexedElement) declaration);
            break;
          }
        }
      }
    }
    return new Descriptor(resource, featureSet, editions, core, nativeRoots);
  }

  private Long edition(EObject context, Descriptor descriptor) {
    Protobuf root = root(context);
    if (root == null || !syntaxes.isEditions(root) || descriptor == null || descriptor.editions == null) return null;
    String name = "EDITION_" + syntaxes.getName(root.getEdition());
    Long result = null;
    for (EObject element : descriptor.editions.eContents()) {
      if (element instanceof Literal && name.equals(((Literal) element).getName())) {
        if (result != null) return null;
        result = ((Literal) element).getIndex();
      }
    }
    return result;
  }

  public boolean hasRequiredMetadata(EObject context) {
    Descriptor descriptor = descriptor(context);
    Long year = edition(context, descriptor);
    if (year == null || descriptor.features == null) return false;
    boolean nativeRoot = false;
    for (IndexedElement declaration : descriptor.nativeRoots)
      if (metadata.descriptorType(declaration.eContainer(), "FileOptions")
          && values.typeOf(declaration) == descriptor.features) { nativeRoot = true; break; }
    if (!nativeRoot) return false;
    for (String name : REQUIRED) if (!hasCoreDefault(descriptor, name, year)) return false;
    return !syntaxes.isEdition2024(context)
        || hasCoreDefault(descriptor, "enforce_naming_style", year)
            && hasCoreDefault(descriptor, "default_symbol_visibility", year);
  }

  private boolean hasCoreDefault(Descriptor descriptor, String name, long year) {
    List<IndexedElement> path = descriptor.core.get(name);
    if (path == null || path.size() != 1) return false;
    IndexedElement field = path.get(0);
    FeatureMetadata.Info info = metadata.of(field);
    String value = defaultValue(field, info, year);
    if (invalidLeaf(field, info) != null || value == null) return false;
    EObject type = values.typeOf(field);
    if (type instanceof com.google.eclipse.protobuf.protobuf.Enum) {
      for (EObject literal : type.eContents())
        if (literal instanceof Literal && value.equals(((Literal) literal).getName()))
          return !coreSentinel(field, (Literal) literal, descriptor);
    }
    return false;
  }

  /** Returns the real selected core declaration path, or an empty list when it is unavailable. */
  public List<IndexedElement> corePath(EObject context, String featureName) {
    Descriptor descriptor = descriptor(context);
    List<IndexedElement> path = descriptor == null ? null : descriptor.core.get(featureName);
    return path == null ? Collections.<IndexedElement>emptyList() : path;
  }

  /** Recognizes only a native options.features field in the selected descriptor. */
  public boolean isFeaturesRoot(IndexedElement declaration, EObject context) {
    Descriptor descriptor = descriptor(context);
    if (!(declaration instanceof MessageField) || descriptor == null || descriptor.features == null
        || declaration.eResource() != descriptor.resource || !"features".equals(FeatureMetadata.name(declaration))
        || values.typeOf(declaration) != descriptor.features) return false;
    for (String name : OPTIONS_TYPES) if (metadata.descriptorType(declaration.eContainer(), name)) return true;
    return false;
  }

  public List<Assignment> assignmentsIn(final EObject owner) {
    if (owner == null || owner.eResource() == null) return Collections.emptyList();
    final Descriptor descriptor = descriptor(owner);
    if (descriptor == null || descriptor.features == null) return Collections.emptyList();
    // Selection identity participates in the SOURCE cache key; foreign metadata never lives here.
    return cache.get(Tuples.create(FeatureSets.class, "assignments",
        Tuples.create(owner, descriptor.features, metadata.revisionsOf(owner))), owner.eResource(),
        () -> readAssignments(owner, descriptor));
  }

  private List<Assignment> readAssignments(EObject owner, Descriptor descriptor) {
    List<Assignment> result = new ArrayList<Assignment>();
    for (AbstractOption option : FeatureMetadata.optionsOf(owner)) {
      if (!options.isNative(option) || !isFeaturesRoot(options.rootSourceOf(option), owner)) continue;
      List<IndexedElement> path = new ArrayList<IndexedElement>();
      EObject expected = descriptor.features;
      boolean valid = true;
      for (OptionField field : options.fieldsOf(option)) {
        IndexedElement declaration = field.getTarget();
        if (!memberOf(declaration, expected)) { valid = false; break; }
        path.add(declaration);
        expected = values.typeOf(declaration);
      }
      if (valid) flatten(owner, path, expected, option.getValue(), option, result);
    }
    return Collections.unmodifiableList(result);
  }

  private void flatten(EObject owner, List<IndexedElement> path, EObject type, Value value, EObject source,
      List<Assignment> result) {
    if (value instanceof ComplexValue && type instanceof Message) {
      for (ValueField field : ((ComplexValue) value).getFields()) {
        IndexedElement declaration = values.declarationOf(field);
        if (!memberOf(declaration, type)) continue;
        List<IndexedElement> nested = new ArrayList<IndexedElement>(path);
        nested.add(declaration);
        List<? extends Value> assigned = FeatureMetadata.valuesOf(field);
        for (Value leaf : assigned) flatten(owner, nested, values.typeOf(declaration), leaf, field, result);
        if (assigned.isEmpty() && !(values.typeOf(declaration) instanceof Message))
          result.add(new Assignment(owner, nested, null, field));
      }
    } else if (!path.isEmpty()) {
      // Keep ill-typed leaves: their explicit presence must block inherited semantic guesses.
      result.add(new Assignment(owner, path, value, source));
    }
  }

  private boolean memberOf(IndexedElement declaration, EObject type) {
    if (declaration == null || declaration.eIsProxy() || declaration.eResource() == null
        || type == null || type.eIsProxy() || type.eResource() == null) return false;
    if (declaration.eContainer() == type) return true;
    if (declaration.eContainer() instanceof TypeExtension) {
      TypeExtension extension = (TypeExtension) declaration.eContainer();
      return extension.getType() != null && extension.getType().getTarget() == type;
    }
    return false;
  }

  private boolean validPath(List<IndexedElement> path, Descriptor descriptor) {
    if (descriptor == null || descriptor.features == null || path == null || path.isEmpty()) return false;
    EObject type = descriptor.features;
    for (IndexedElement declaration : path) {
      if (!memberOf(declaration, type)) return false;
      type = values.typeOf(declaration);
      if (type == null || type.eResource() == null) return false;
    }
    return true;
  }

  /** Typed path for a feature option or aggregate member; excludes Options.features. */
  public List<IndexedElement> pathOf(EObject source) {
    AbstractOption option = enclosingOption(source);
    if (option == null) return Collections.emptyList();
    List<IndexedElement> prefix = new ArrayList<IndexedElement>();
    List<ValueField> aggregates = new ArrayList<ValueField>();
    for (EObject current = source; current != null && current != option; current = current.eContainer())
      if (current instanceof ValueField) aggregates.add((ValueField) current);
    if (!options.isNative(option) || !isFeaturesRoot(options.rootSourceOf(option), option)) return Collections.emptyList();
    for (OptionField field : options.fieldsOf(option)) prefix.add(field.getTarget());
    Collections.reverse(aggregates);
    for (ValueField field : aggregates) prefix.add(values.declarationOf(field));
    return validPath(prefix, descriptor(option)) ? Collections.unmodifiableList(prefix) : Collections.<IndexedElement>emptyList();
  }

  public String explicitValue(EObject owner, List<IndexedElement> path) {
    Descriptor descriptor = descriptor(owner);
    if (edition(owner, descriptor) == null || !hasRequiredMetadata(owner) || !validPath(path, descriptor)) return null;
    return explicit(owner, path).value;
  }

  private static final class Explicit {
    final boolean present;
    final String value;
    Explicit(boolean present, String value) { this.present = present; this.value = value; }
  }

  private Explicit explicit(EObject owner, List<IndexedElement> path) {
    Assignment found = null;
    for (Assignment assignment : assignmentsIn(owner)) {
      if (prefix(assignment.path, path) || prefix(path, assignment.path)) {
        if (!assignment.path.equals(path) || found != null) return new Explicit(true, null);
        found = assignment;
      }
    }
    if (found == null) return new Explicit(false, null);
    IndexedElement leaf = path.get(path.size() - 1);
    String value = canonical(leaf, found.value);
    Literal literal = metadata.literal(found.value);
    return new Explicit(true, value != null && canAssign(owner, path, literal, found.source) ? value : null);
  }

  private boolean prefix(List<IndexedElement> first, List<IndexedElement> second) {
    if (first.size() > second.size()) return false;
    for (int i = 0; i < first.size(); i++) if (first.get(i) != second.get(i)) return false;
    return true;
  }

  public String effectiveValue(EObject owner, List<IndexedElement> path) {
    Descriptor descriptor = descriptor(owner);
    Long year = edition(owner, descriptor);
    if (year == null || !hasRequiredMetadata(owner) || !validPath(path, descriptor)) return null;
    IndexedElement leaf = path.get(path.size() - 1);
    if (values.typeOf(leaf) instanceof Message || invalidDefinition(leaf) != null) return null;
    for (EObject current = owner; current != null; current = current.eContainer()) {
      Explicit explicit = explicit(current, path);
      if (explicit.present) return inheritedPresence(owner, current, path, explicit.value, descriptor);
      if (current instanceof Protobuf) break;
    }
    return inheritedPresence(owner, null, path, defaultValue(leaf, metadata.of(leaf), year), descriptor);
  }

  private String inheritedPresence(EObject owner, EObject declaringOwner, List<IndexedElement> path,
      String value, Descriptor descriptor) {
    if (owner != declaringOwner && owner instanceof MessageField && path.size() == 1
        && path.get(0).eContainer() == descriptor.features
        && "field_presence".equals(FeatureMetadata.name(path.get(0))) && "IMPLICIT".equals(value)) {
      MessageField field = (MessageField) owner;
      if (values.typeOf(field) instanceof Message || field.eContainer() instanceof OneOf
          || field.eContainer() instanceof TypeExtension) return "EXPLICIT";
    }
    return value;
  }

  public String coreValue(EObject owner, String featureName) {
    return effectiveValue(owner, corePath(owner, featureName));
  }

  private String canonical(IndexedElement declaration, Value value) {
    Literal literal = metadata.literal(value);
    EObject type = values.typeOf(declaration);
    if (isBool(type) && value != null && value.eContainer() instanceof SimpleValueField) {
      BigInteger number = value instanceof LongLink ? ((LongLink) value).getTarget()
          : value instanceof HexNumberLink ? ((HexNumberLink) value).getTarget() : null;
      if (BigInteger.ZERO.equals(number)) return "false";
      if (BigInteger.ONE.equals(number)) return "true";
    }
    if (literal == null) return null;
    if (type instanceof com.google.eclipse.protobuf.protobuf.Enum)
      return !(literal instanceof BuiltinLiteral) && literal.eContainer() == type ? literal.getName() : null;
    if (isBool(type) && literal instanceof BuiltinLiteral
        && ("true".equals(literal.getName()) || "false".equals(literal.getName()))) return literal.getName();
    return null;
  }

  private boolean isBool(EObject type) {
    return type instanceof ScalarTypeLink && ((ScalarTypeLink) type).getTarget() == ScalarType.BOOL;
  }

  private String defaultText(final IndexedElement declaration, final String text) {
    if (text == null) return null;
    if (declaration.eResource() == null) return parseDefaultText(declaration, text);
    return cache.get(Tuples.create(FeatureSets.class, "defaultText",
        Tuples.create(declaration, text, metadata.revisionsOf(declaration))), declaration.eResource(),
        () -> parseDefaultText(declaration, text));
  }

  private String parseDefaultText(IndexedElement declaration, String text) {
    String trimmed = text.trim();
    EObject type = values.typeOf(declaration);
    if (isBool(type)) {
      if ("true".equals(trimmed) || "True".equals(trimmed) || "t".equals(trimmed)) return "true";
      if ("false".equals(trimmed) || "False".equals(trimmed) || "f".equals(trimmed)) return "false";
      Integer number = defaultInteger(trimmed);
      return number == null ? null : number == 0 ? "false" : number == 1 ? "true" : null;
    }
    if (type instanceof com.google.eclipse.protobuf.protobuf.Enum) {
      boolean found = false;
      for (EObject child : type.eContents()) {
        if (child instanceof Literal && trimmed.equals(((Literal) child).getName())) {
          if (found) return null;
          found = true;
        }
      }
      if (found) return trimmed;
      Integer number = defaultInteger(trimmed);
      if (number != null) {
        for (EObject child : type.eContents()) {
          if (child instanceof Literal && ((Literal) child).getIndex() == number) return ((Literal) child).getName();
        }
      }
    }
    return null;
  }

  private Integer defaultInteger(String text) {
    int start = text.startsWith("-") ? 1 : 0;
    if (start == text.length() || text.charAt(start) < '0' || text.charAt(start) > '9') return null;
    try {
      return Integer.decode(text);
    } catch (NumberFormatException invalid) {
      return null;
    }
  }

  private String defaultValue(IndexedElement declaration, FeatureMetadata.Info info, long year) {
    FeatureMetadata.Default best = null;
    for (FeatureMetadata.Default candidate : info.defaults) {
      if (candidate.edition <= year && (best == null || candidate.edition > best.edition)) best = candidate;
    }
    return best == null ? null : defaultText(declaration, best.text);
  }

  public boolean allowsTarget(IndexedElement declaration, EObject owner, EObject source) {
    if (declaration == null || declaration.eIsProxy() || owner == null) return false;
    String target = targetName(owner, source);
    Set<String> targets = metadata.of(declaration).targets;
    return target == null || targets.isEmpty() || targets.contains(target);
  }

  private String targetName(EObject owner, EObject source) {
    if (owner instanceof Group && enclosingOption(source) instanceof Option) return "TARGET_TYPE_MESSAGE";
    if (owner instanceof Protobuf) return "TARGET_TYPE_FILE";
    if (owner instanceof Extensions) return "TARGET_TYPE_EXTENSION_RANGE";
    if (owner instanceof Message) return "TARGET_TYPE_MESSAGE";
    if (owner instanceof MessageField || owner instanceof Group) return "TARGET_TYPE_FIELD";
    if (owner instanceof OneOf) return "TARGET_TYPE_ONEOF";
    if (owner instanceof com.google.eclipse.protobuf.protobuf.Enum) return "TARGET_TYPE_ENUM";
    if (owner instanceof Literal) return "TARGET_TYPE_ENUM_ENTRY";
    if (owner instanceof Service) return "TARGET_TYPE_SERVICE";
    if (owner instanceof Rpc || owner instanceof Stream) return "TARGET_TYPE_METHOD";
    return null;
  }

  private AbstractOption enclosingOption(EObject source) {
    for (EObject current = source; current != null; current = current.eContainer())
      if (current instanceof AbstractOption) return (AbstractOption) current;
    return null;
  }

  public boolean canAssign(EObject owner, List<IndexedElement> path, Literal enumValue) {
    return canAssign(owner, path, enumValue, owner);
  }

  private boolean canAssign(EObject owner, List<IndexedElement> path, Literal enumValue, EObject source) {
    Descriptor descriptor = descriptor(owner);
    if (edition(owner, descriptor) == null || !hasRequiredMetadata(owner) || !validPath(path, descriptor)
        || targetName(owner, source) == null
        || owner instanceof MessageField && values.typeOf((MessageField) owner) == null) return false;
    IndexedElement declaration = path.get(path.size() - 1);
    EObject type = values.typeOf(declaration);
    if (type instanceof Message) {
      if (enumValue != null || path.size() != 1 || !isFeatureExtension(declaration)
          || invalidDefinition(declaration) != null) return false;
      for (EObject member : type.eContents()) if (member instanceof MessageField) {
        List<IndexedElement> child = new ArrayList<IndexedElement>(path);
        child.add((IndexedElement) member);
        if (canAssign(owner, child, null, source)) return true;
      }
      return false;
    }
    if (invalidDefinition(declaration) != null || !allowsTarget(declaration, owner, source)
        || supportError(declaration, enumValue, owner) != null) return false;
    if (type instanceof com.google.eclipse.protobuf.protobuf.Enum) {
      if (enumValue == null) {
        for (EObject child : type.eContents()) if (child instanceof Literal
            && canAssign(owner, path, (Literal) child, source)) return true;
        return false;
      }
      if (enumValue.eIsProxy() || enumValue.eContainer() != type || coreSentinel(declaration, enumValue, descriptor)) return false;
    } else if (!isBool(type) || enumValue != null && (!(enumValue instanceof BuiltinLiteral)
        || !("true".equals(enumValue.getName()) || "false".equals(enumValue.getName())))) return false;
    return fieldKindAllows(owner, path, enumValue, descriptor);
  }

  private boolean coreSentinel(IndexedElement field, Literal value, Descriptor descriptor) {
    return value.getIndex() == 0 && (field.eContainer() == descriptor.features
        || "pb.JavaFeatures".equals(metadata.qualified(field.eContainer())));
  }

  private boolean fieldKindAllows(EObject owner, List<IndexedElement> path, Literal literal, Descriptor descriptor) {
    IndexedElement leaf = path.get(path.size() - 1);
    String feature = FeatureMetadata.name(leaf);
    String value = literal == null ? null : literal.getName();
    boolean core = path.size() == 1 && leaf.eContainer() == descriptor.features;
    if (core && "field_presence".equals(feature)) {
      if (owner instanceof Protobuf) return !"LEGACY_REQUIRED".equals(value);
      if (!(owner instanceof MessageField)) return false;
      MessageField field = (MessageField) owner;
      if (values.isRepeated(field) || field.eContainer() instanceof OneOf || field.eContainer() instanceof TypeExtension)
        return false;
      if ("IMPLICIT".equals(value)) {
        EObject type = values.typeOf(field);
        if (type instanceof Message) return false;
        if (type instanceof com.google.eclipse.protobuf.protobuf.Enum
            && !Boolean.TRUE.equals(isOpenEnum((com.google.eclipse.protobuf.protobuf.Enum) type))) return false;
        for (AbstractOption option : FeatureMetadata.optionsOf(field))
          if (option instanceof com.google.eclipse.protobuf.protobuf.DefaultValueFieldOption) return false;
      }
    }
    if (core && owner instanceof MessageField) {
      MessageField field = (MessageField) owner;
      if ("repeated_field_encoding".equals(feature))
        return values.isRepeated(field) && !(field.getType() instanceof MapTypeLink)
            && (!"PACKED".equals(value) || fields.isPackable(field));
      if ("utf8_validation".equals(feature)) return isStringField(field);
      if ("message_encoding".equals(feature)) return values.typeOf(field) instanceof Message;
    }
    if (path.size() > 1 && "pb.java".equals(metadata.qualified(path.get(0)))) {
      if ("legacy_closed_enum".equals(feature) && owner instanceof MessageField)
        return values.typeOf((MessageField) owner) instanceof com.google.eclipse.protobuf.protobuf.Enum;
      if ("nest_in_file_class".equals(feature))
        return (owner instanceof Message || owner instanceof com.google.eclipse.protobuf.protobuf.Enum || owner instanceof Service)
            && owner.eContainer() instanceof Protobuf;
    }
    return true;
  }

  private boolean isStringField(MessageField field) {
    if (field.getType() instanceof ScalarTypeLink) return ((ScalarTypeLink) field.getType()).getTarget() == ScalarType.STRING;
    if (field.getType() instanceof MapTypeLink) {
      MapType map = ((MapTypeLink) field.getType()).getTarget();
      return map != null && (isStringType(map.getKeyType()) || isStringType(map.getValueType()));
    }
    return false;
  }

  private boolean isStringType(TypeLink type) {
    return type instanceof ScalarTypeLink && ((ScalarTypeLink) type).getTarget() == ScalarType.STRING;
  }

  public String supportError(IndexedElement declaration, Literal enumValue, EObject context) {
    Descriptor descriptor = descriptor(context);
    Long year = edition(context, descriptor);
    if (year == null || declaration == null || declaration.eIsProxy() || values.typeOf(declaration) == null) return null;
    FeatureMetadata.Info parent = metadata.of(declaration);
    String error = supportProblem(declaration, parent, null, year, context, false);
    if (error != null || enumValue == null || enumValue.eIsProxy()) return error;
    if (enumValue.eContainer() != values.typeOf(declaration)) return null;
    return supportProblem(enumValue, metadata.of(enumValue), parent, year, context, false);
  }

  public String deprecationWarning(IndexedElement declaration, Literal enumValue, EObject context) {
    Descriptor descriptor = descriptor(context);
    Long year = edition(context, descriptor);
    if (year == null || declaration == null || declaration.eIsProxy() || values.typeOf(declaration) == null
        || supportError(declaration, enumValue, context) != null) return null;
    if (enumValue != null && !enumValue.eIsProxy() && enumValue.eContainer() == values.typeOf(declaration)) {
      String warning = supportProblem(enumValue, metadata.of(enumValue), metadata.of(declaration), year, context, true);
      if (warning != null) return warning;
    }
    return supportProblem(declaration, metadata.of(declaration), null, year, context, true);
  }

  private String supportProblem(EObject declaration, FeatureMetadata.Info info, FeatureMetadata.Info parent,
      long year, EObject context, boolean warning) {
    String name = metadata.qualified(declaration);
    if (name == null || info.error != null) return null;
    String sourceYear = syntaxes.getName(root(context).getEdition());
    if (!warning) {
      if (info.introduced != null && year < info.introduced)
        return format(Messages.featureSupportIntroduced, name, sourceYear, info.introducedName);
      if (info.removed != null && year >= info.removed)
        return format(Messages.featureSupportRemoved, name, sourceYear, info.removedName,
            prose(info.removalError != null ? info.removalError : parent == null ? null : parent.removalError));
    } else if (info.deprecated != null && year >= info.deprecated && (info.removed == null || year < info.removed)) {
      return format(Messages.featureSupportDeprecated, name, sourceYear, info.deprecatedName,
          prose(info.warning != null ? info.warning : parent == null ? null : parent.warning));
    }
    return null;
  }

  private String prose(String text) { return text == null || text.isEmpty() ? "" : " " + text; }

  public Boolean isOpenEnum(com.google.eclipse.protobuf.protobuf.Enum enumeration) {
    if (enumeration == null || enumeration.eIsProxy() || root(enumeration) == null) return null;
    if (syntaxes.isProto2(enumeration)) return Boolean.FALSE;
    if (syntaxes.isProto3(enumeration)) return Boolean.TRUE;
    String value = coreValue(enumeration, "enum_type");
    return "OPEN".equals(value) ? Boolean.TRUE : "CLOSED".equals(value) ? Boolean.FALSE : null;
  }

  /** A definition problem only for real FeatureSet extensions/payload fields (not ordinary options). */
  public String invalidDefinition(IndexedElement declaration) {
    if (declaration == null || declaration.eIsProxy()) return null;
    if (isFeatureExtension(declaration)) return invalidExtension(declaration);
    EObject parent = declaration.eContainer();
    if (parent instanceof OneOf) parent = parent.eContainer();
    if (!(parent instanceof Message)) return null;
    if (metadata.descriptorType(parent, "FeatureSet")) return invalidLeaf(declaration, metadata.of(declaration));
    List<IndexedElement> extensions = payloadExtensions((Message) parent);
    if (extensions.isEmpty()) return null;
    for (IndexedElement extension : extensions) {
      String invalid = invalidExtension(extension);
      if (invalid != null) return invalid;
    }
    return invalidLeaf(declaration, metadata.of(declaration));
  }

  private boolean isFeatureExtension(IndexedElement declaration) {
    if (!(declaration.eContainer() instanceof TypeExtension)) return false;
    TypeExtension extension = (TypeExtension) declaration.eContainer();
    return extension.getType() != null && metadata.descriptorType(extension.getType().getTarget(), "FeatureSet");
  }

  private List<IndexedElement> payloadExtensions(Message payload) {
    // No process-global reverse index: declarations belong to the caller's ResourceSet.
    List<IndexedElement> result = new ArrayList<IndexedElement>();
    if (payload.eResource() == null || payload.eResource().getResourceSet() == null) return result;
    List<Resource> resources = new ArrayList<Resource>(payload.eResource().getResourceSet().getResources());
    for (Resource resource : resources) {
      List<IndexedElement> candidates = cache.get(Tuples.create(FeatureSets.class, "extensionDeclarations"), resource,
          () -> extensionDeclarations(resource));
      for (IndexedElement candidate : candidates)
        if (isFeatureExtension(candidate) && values.typeOf(candidate) == payload) result.add(candidate);
    }
    return result;
  }

  private List<IndexedElement> extensionDeclarations(Resource resource) {
    List<IndexedElement> result = new ArrayList<IndexedElement>();
    TreeIterator<EObject> all = resource.getAllContents();
    while (all.hasNext()) {
      EObject declaration = all.next();
      if (declaration instanceof IndexedElement && declaration.eContainer() instanceof TypeExtension)
        result.add((IndexedElement) declaration);
    }
    return Collections.unmodifiableList(result);
  }

  private String invalidExtension(IndexedElement extension) {
    if (!(extension instanceof MessageField) || values.isRepeated(extension)
        || ((MessageField) extension).getModifier() == ModifierEnum.REQUIRED || !(values.typeOf(extension) instanceof Message))
      return Messages.invalidFeatureExtension;
    Message payload = (Message) values.typeOf(extension);
    TreeIterator<EObject> all = payload.eAllContents();
    while (all.hasNext()) {
      EObject member = all.next();
      if (member instanceof TypeExtension || member instanceof Extensions)
        return Messages.invalidFeatureNestedExtensions;
    }
    return null;
  }

  private String invalidLeaf(IndexedElement declaration, FeatureMetadata.Info info) {
    if (!(declaration instanceof MessageField)) return Messages.invalidFeatureKind;
    MessageField field = (MessageField) declaration;
    EObject type = values.typeOf(field);
    if (values.isRepeated(field) || field.getModifier() == ModifierEnum.REQUIRED || field.eContainer() instanceof OneOf
        || !(isBool(type) || type instanceof com.google.eclipse.protobuf.protobuf.Enum))
      return Messages.invalidFeatureKind;
    if (info.error != null) return info.error;
    if (info.targets.isEmpty()) return Messages.invalidFeatureTargetsRequired;
    if (info.introduced == null && !info.legacyFeatureSchema) return Messages.invalidFeatureIntroduction;
    String support = invalidSupport(info, null);
    if (support != null) return support;
    boolean baseline = false;
    Set<Long> editions = new HashSet<Long>();
    Long edition2023 = info.edition2023;
    for (FeatureMetadata.Default entry : info.defaults) {
      if (!editions.add(entry.edition)) return Messages.invalidFeatureDuplicateDefault;
      if ("EDITION_LEGACY".equals(entry.editionName)
          || info.legacyFeatureSchema && "EDITION_PROTO2".equals(entry.editionName)) baseline = true;
      if (defaultText(field, entry.text) == null) return Messages.invalidFeatureDefaultValue;
      if (edition2023 != null && info.introduced != null && entry.edition >= edition2023
          && (entry.edition < info.introduced || info.removed != null && entry.edition > info.removed))
        return Messages.invalidFeatureDefaultWindow;
    }
    if (!baseline) return Messages.invalidFeatureLegacyDefault;
    if (type instanceof com.google.eclipse.protobuf.protobuf.Enum) for (EObject member : type.eContents()) {
      if (member instanceof Literal) {
        String invalid = invalidSupport(metadata.of(member), info);
        if (invalid != null) return invalid;
      }
    }
    return null;
  }

  private String invalidSupport(FeatureMetadata.Info info, FeatureMetadata.Info parent) {
    if (info.error != null) return info.error;
    Long introduced = info.introduced != null ? info.introduced : parent == null ? null : parent.introduced;
    Long deprecated = info.deprecated != null ? info.deprecated : parent == null ? null : parent.deprecated;
    Long removed = info.removed != null ? info.removed : parent == null ? null : parent.removed;
    if (introduced != null && removed != null && introduced > removed
        || introduced != null && deprecated != null && introduced > deprecated
        || deprecated != null && removed != null && deprecated >= removed)
      return Messages.invalidFeatureSupportWindow;
    String warning = info.warning != null ? info.warning : parent == null ? null : parent.warning;
    String removalError = info.removalError != null ? info.removalError : parent == null ? null : parent.removalError;
    if (deprecated != null && (warning == null || warning.trim().isEmpty()))
      return Messages.invalidFeatureDeprecationText;
    if (removed != null && (introduced == null || !introduced.equals(removed))
        && (removalError == null || removalError.trim().isEmpty()))
      return Messages.invalidFeatureRemovalText;
    if (parent != null && (parent.introduced != null && introduced != null && introduced < parent.introduced
        || parent.removed != null && (removed == null || removed > parent.removed)
        || parent.deprecated != null && (deprecated == null || deprecated > parent.deprecated)))
      return Messages.invalidFeatureValueWindow;
    return null;
  }
}
