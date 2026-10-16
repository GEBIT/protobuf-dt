package com.google.eclipse.protobuf.model.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.naming.IQualifiedNameProvider;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.util.IResourceScopeCache;
import org.eclipse.xtext.util.Tuples;

import com.google.eclipse.protobuf.protobuf.AbstractOption;
import com.google.eclipse.protobuf.protobuf.ComplexValue;
import com.google.eclipse.protobuf.protobuf.ComplexValueField;
import com.google.eclipse.protobuf.protobuf.IndexedElement;
import com.google.eclipse.protobuf.protobuf.Literal;
import com.google.eclipse.protobuf.protobuf.LiteralLink;
import com.google.eclipse.protobuf.protobuf.MessageField;
import com.google.eclipse.protobuf.protobuf.OptionField;
import com.google.eclipse.protobuf.protobuf.SimpleValueField;
import com.google.eclipse.protobuf.protobuf.StringLink;
import com.google.eclipse.protobuf.protobuf.Value;
import com.google.eclipse.protobuf.protobuf.ValueField;
import com.google.inject.Inject;
import com.google.eclipse.protobuf.validation.Messages;
import com.google.inject.Singleton;

/** Reads linked descriptor metadata; cached only on the resource declaring the metadata. */
@Singleton
class FeatureMetadata {
  @Inject private Options options;
  @Inject private OptionValues values;
  @Inject private StringLiterals strings;
  @Inject private IQualifiedNameProvider names;
  @Inject private IResourceScopeCache cache;

  static final class Default {
    final long edition;
    final String editionName;
    final String text;
    Default(long edition, String editionName, String text) {
      this.edition = edition;
      this.editionName = editionName;
      this.text = text;
    }
  }

  static final class Info {
    final Set<String> targets;
    final Long introduced, deprecated, removed, edition2023;
    final String introducedName, deprecatedName, removedName, warning, removalError;
    final List<Default> defaults;
    final String error;
    Info(Set<String> targets, Map<String, Value> support, List<Default> defaults,
        String error, FeatureMetadata reader) {
      this.targets = Collections.unmodifiableSet(targets);
      this.defaults = Collections.unmodifiableList(defaults);
      introduced = reader.edition(support.get("edition_introduced"));
      deprecated = reader.edition(support.get("edition_deprecated"));
      removed = reader.edition(support.get("edition_removed"));
      edition2023 = reader.editionNamed(support.get("edition_introduced"), "EDITION_2023");
      introducedName = reader.enumName(support.get("edition_introduced"));
      deprecatedName = reader.enumName(support.get("edition_deprecated"));
      removedName = reader.enumName(support.get("edition_removed"));
      warning = reader.text(support.get("deprecation_warning"));
      removalError = reader.text(support.get("removal_error"));
      String invalid = error;
      for (Map.Entry<String, Value> entry : support.entrySet()) {
        if (entry.getKey().startsWith("edition_") && reader.edition(entry.getValue()) == null)
          invalid = Messages.invalidFeatureSupportEditions;
        if (("deprecation_warning".equals(entry.getKey()) || "removal_error".equals(entry.getKey()))
            && reader.text(entry.getValue()) == null)
          invalid = Messages.invalidFeatureSupportText;
      }
      this.error = invalid;
    }
  }

  Info of(final EObject declaration) {
    if (declaration == null) return empty();
    if (declaration.eResource() == null) return read(declaration);
    Resource declaring = declaration.eResource();
    return cache.get(Tuples.create(FeatureMetadata.class, declaration, revisionsOf(declaration)), declaring,
        () -> read(declaration));
  }

  List<Object> revisionsOf(EObject declaration) {
    Set<Resource> dependencies = cache.get(Tuples.create(FeatureMetadata.class, "dependencies", declaration),
        declaration.eResource(), () -> dependenciesOf(declaration));
    if (dependencies.isEmpty()) return Collections.emptyList();
    List<Object> revisions = new ArrayList<Object>(dependencies.size());
    for (Resource dependency : dependencies)
      revisions.add(cache.get(Tuples.create(FeatureMetadata.class, "revision"), dependency, () -> new Object()));
    return revisions;
  }

  private Set<Resource> dependenciesOf(EObject declaration) {
    Set<Resource> result = new LinkedHashSet<Resource>();
    TreeIterator<EObject> all = declaration.eAllContents();
    while (all.hasNext()) {
      EObject child = all.next();
      for (EObject target : child.eCrossReferences())
        if (target.eResource() != null && target.eResource() != declaration.eResource()) result.add(target.eResource());
    }
    return Collections.unmodifiableSet(result);
  }

  private Info empty() {
    return new Info(new LinkedHashSet<String>(), new LinkedHashMap<String, Value>(),
        new ArrayList<Default>(), null, this);
  }

  private Info read(EObject declaration) {
    Set<String> targets = new LinkedHashSet<String>();
    Map<String, Value> support = new LinkedHashMap<String, Value>();
    List<Default> defaults = new ArrayList<Default>();
    String error = null;
    boolean supportAggregate = false;
    for (AbstractOption option : optionsOf(declaration)) {
      IndexedElement root = options.rootSourceOf(option);
      String owner = declaration instanceof Literal ? "EnumValueOptions" : "FieldOptions";
      if (!options.isNative(option) || !descriptorType(root == null ? null : root.eContainer(), owner)) continue;
      String name = name(root);
      if ("targets".equals(name)) {
        String target = enumName(option.getValue());
        if (target != null) targets.add(target);
        else error = Messages.invalidFeatureTargets;
      } else if ("feature_support".equals(name)) {
        if (options.fieldsOf(option).isEmpty()) {
          if (supportAggregate) error = Messages.invalidFeatureSupportDuplicate;
          supportAggregate = true;
        }
        Map<String, Value> record = record(option);
        for (Map.Entry<String, Value> entry : record.entrySet()) {
          if (support.containsKey(entry.getKey())) error = Messages.invalidFeatureSupportDuplicate;
          support.put(entry.getKey(), entry.getValue());
        }
      } else if ("edition_defaults".equals(name)) {
        Map<String, Value> record = record(option);
        Long edition = edition(record.get("edition"));
        String text = text(record.get("value"));
        if (edition == null || text == null) error = Messages.invalidFeatureDefaultsRecord;
        else defaults.add(new Default(edition, enumName(record.get("edition")), text));
      }
    }
    return new Info(targets, support, defaults, error, this);
  }

  private Map<String, Value> record(AbstractOption option) {
    Map<String, Value> result = new LinkedHashMap<String, Value>();
    List<OptionField> path = options.fieldsOf(option);
    if (!path.isEmpty()) {
      IndexedElement leaf = values.declarationOf(option);
      if (leaf != null) result.put(name(leaf), option.getValue());
    } else if (option.getValue() instanceof ComplexValue) {
      for (ValueField field : ((ComplexValue) option.getValue()).getFields()) {
        IndexedElement target = values.declarationOf(field);
        List<? extends Value> assigned = valuesOf(field);
        if (target != null) {
          String key = name(target);
          // A null marker preserves invalid duplicate/list metadata rather than choosing a winner.
          result.put(key, result.containsKey(key) || assigned.size() != 1 ? null : assigned.get(0));
        }
      }
    }
    return result;
  }

  static List<AbstractOption> optionsOf(EObject owner) {
    List<AbstractOption> result = new ArrayList<AbstractOption>();
    if (owner != null) for (EObject child : owner.eContents())
      if (child instanceof AbstractOption) result.add((AbstractOption) child);
    return result;
  }

  static List<? extends Value> valuesOf(ValueField field) {
    if (field instanceof SimpleValueField) return ((SimpleValueField) field).getValues();
    if (field instanceof ComplexValueField) return ((ComplexValueField) field).getValues();
    return Collections.emptyList();
  }

  String qualified(EObject declaration) {
    if (declaration == null || declaration.eIsProxy()) return null;
    QualifiedName name = names.getFullyQualifiedName(declaration);
    return name == null ? null : name.toString();
  }

  boolean descriptorType(EObject declaration, String name) {
    String qualified = qualified(declaration);
    return ("google.protobuf." + name).equals(qualified) || ("proto2." + name).equals(qualified);
  }

  static String name(IndexedElement declaration) {
    return declaration instanceof MessageField ? ((MessageField) declaration).getName() : null;
  }

  String enumName(Value value) {
    Literal literal = literal(value);
    return literal == null ? null : literal.getName();
  }

  Literal literal(Value value) {
    Literal literal = value instanceof LiteralLink ? ((LiteralLink) value).getTarget() : null;
    return literal == null || literal.eIsProxy() || literal.eResource() == null
        || ((LiteralLink) value).isIsNegative() ? null : literal;
  }

  private Long edition(Value value) {
    Literal literal = literal(value);
    return literal != null && descriptorType(literal.eContainer(), "Edition") ? literal.getIndex() : null;
  }

  private Long editionNamed(Value value, String name) {
    Literal literal = literal(value);
    if (literal == null || !descriptorType(literal.eContainer(), "Edition")) return null;
    for (EObject child : literal.eContainer().eContents())
      if (child instanceof Literal && name.equals(((Literal) child).getName())) return ((Literal) child).getIndex();
    return null;
  }

  String text(Value value) {
    return value instanceof StringLink ? strings.getCombinedString(((StringLink) value).getTarget()) : null;
  }
}
