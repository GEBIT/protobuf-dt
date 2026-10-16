/*
 * Copyright (c) 2016 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.scoping;

import static com.google.eclipse.protobuf.util.Tracer.DEBUG_SCOPING;
import static com.google.eclipse.protobuf.validation.ProtobufResourceValidator.getScopeProviderTimingCollector;
import com.google.eclipse.protobuf.model.util.ModelObjects;
import com.google.eclipse.protobuf.model.util.OptionValues;
import com.google.eclipse.protobuf.model.util.Options;
import com.google.eclipse.protobuf.model.util.MessageFields;
import com.google.eclipse.protobuf.protobuf.AbstractOption;
import com.google.eclipse.protobuf.protobuf.AnyFieldName;
import com.google.eclipse.protobuf.protobuf.BuiltinLiteral;
import com.google.eclipse.protobuf.protobuf.EnumElement;
import com.google.eclipse.protobuf.protobuf.Literal;
import com.google.eclipse.protobuf.protobuf.MapType;
import com.google.eclipse.protobuf.protobuf.Message;
import com.google.eclipse.protobuf.protobuf.ScalarTypeLink;
import com.google.eclipse.protobuf.protobuf.TypeExtension;
import org.eclipse.xtext.resource.EObjectDescription;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.scoping.impl.SimpleScope;
import org.eclipse.xtext.scoping.impl.FilteringScope;
import com.google.eclipse.protobuf.naming.ProtobufQualifiedNameProvider;
import com.google.eclipse.protobuf.protobuf.Enum;
import com.google.eclipse.protobuf.protobuf.ExtensionFieldName;
import com.google.eclipse.protobuf.protobuf.ExtensionOptionField;
import com.google.eclipse.protobuf.protobuf.FieldName;
import com.google.eclipse.protobuf.protobuf.Group;
import com.google.eclipse.protobuf.protobuf.IndexedElement;
import com.google.eclipse.protobuf.protobuf.LiteralLink;
import com.google.eclipse.protobuf.protobuf.MessageField;
import com.google.eclipse.protobuf.protobuf.OptionField;
import com.google.eclipse.protobuf.protobuf.OptionSource;
import com.google.eclipse.protobuf.protobuf.Protobuf;
import com.google.eclipse.protobuf.protobuf.ValueField;
import com.google.inject.Inject;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.impl.AbstractDeclarativeScopeProvider;
import org.eclipse.xtext.util.IResourceScopeCache;
import java.util.ArrayList;
import java.util.List;

/**
 * A scope provider for the Protobuf language.
 *
 * @author atrookey@google.com (Alexander Rookey)
 */
public class ProtobufScopeProvider extends AbstractDeclarativeScopeProvider {

  @Inject private ProtoDescriptorProvider descriptorProvider;
  @Inject private IResourceScopeCache cache;
  @Inject private ProtobufQualifiedNameProvider nameProvider;
  @Inject private OptionValues optionValues;
  @Inject private Options options;
  @Inject private ModelObjects modelObjects;
  @Inject private MessageFields messageFields;




  private IScope memberScope(EObject type) {
    if (type == null || type.eIsProxy()) {
      return IScope.NULLSCOPE;
    }
    return type.eResource() == null ? createMemberScope(type)
        : cache.get(org.eclipse.xtext.util.Tuples.pair(type, "optionMembers"), type.eResource(),
            () -> createMemberScope(type));
  }

  private IScope createMemberScope(EObject type) {
    List<IEObjectDescription> descriptions = new ArrayList<>();
    if (type instanceof Message) {
      for (MessageField field : messageFields.fieldsOf((Message) type)) {
        addDescription(descriptions, field);
      }
      for (EObject child : type.eContents()) {
        if (child instanceof Group) {
          addDescription(descriptions, child);
        }
      }
    } else if (type instanceof Group) {
      for (EObject child : type.eContents()) {
        if (child instanceof IndexedElement) {
          addDescription(descriptions, child);
        }
      }
    } else if (type instanceof MapType) {
      for (MessageField field : ((MapType) type).getEntryFields()) {
        addDescription(descriptions, field);
      }
    } else if (type instanceof Enum) {
      for (EnumElement element : ((Enum) type).getElements()) {
        if (element instanceof Literal && !(element instanceof BuiltinLiteral)) {
          addDescription(descriptions, element);
          QualifiedName qualifiedName = nameProvider.getFullyQualifiedName(element);
          if (qualifiedName != null) {
            descriptions.add(EObjectDescription.create(qualifiedName, element));
          }
        }
      }
    }
    return new SimpleScope(IScope.NULLSCOPE, descriptions);
  }

  private void addDescription(List<IEObjectDescription> descriptions, EObject element) {
    String name = element instanceof IndexedElement ? options.nameForOption((IndexedElement) element)
        : org.eclipse.xtext.util.SimpleAttributeResolver.NAME_RESOLVER.apply(element);
    if (name != null && !name.isEmpty()) {
      descriptions.add(EObjectDescription.create(QualifiedName.create(name), element));
    }
  }


  /** Returns descriptor associated with the current project. */
  private Resource getDescriptorResource(EObject context) {
    if (context == null || context.eResource() == null || context.eResource().getResourceSet() == null) {
      return null;
    }
    ResourceSet resourceSet = context.eResource().getResourceSet();
    ProtoDescriptorProvider.ProtoDescriptorInfo descriptorInfo = descriptorProvider.primaryDescriptor(context.eResource());
    if (descriptorInfo == null || descriptorInfo.location == null) {
      return null;
    }
    try {
      return resourceSet.getResource(descriptorInfo.location, true);
    } catch (RuntimeException exception) {
      return null;
    }
  }


  /** Returns the local scope provider. */
  private ProtobufImportedNamespaceAwareLocalScopeProvider getLocalScopeProvider() {
    return (ProtobufImportedNamespaceAwareLocalScopeProvider) super.getDelegate();
  }


  @Override
  public IScope getScope(EObject context, EReference reference) {
    if (DEBUG_SCOPING) {
      getScopeProviderTimingCollector().startTimer();
    }
    IScope scope = super.getScope(context, reference);
    if (DEBUG_SCOPING) {
      getScopeProviderTimingCollector().stopTimer();
    }
    return scope;
  }

  /**
   * Scopes the {@code FieldName}.
   *
   * <p>For example: <pre>
   * message FooOptions {
   *   optional int32 opt1 = 1;
   * }
   * extend google.protobuf.FieldOptions {
   *   optional FooOptions foo_options = 1234;
   * }
   * message Bar {
   *   optional int32 b = 1 [(foo_options) = { opt1: 123 }];
   * }
   * </pre>
   *
   * The {@code NormalFieldName} {@code opt1} contains a cross-reference to {@code FooOptions.opt1}.
   */
  public IScope scope_FieldName_target(FieldName fieldName, EReference reference) {
    if (fieldName == null || fieldName.eResource() == null) {
      return IScope.NULLSCOPE;
    }
    EObject field = fieldName.eContainer();
    EObject aggregate = field == null ? null : field.eContainer();
    EObject expected = optionValues.expectedType(aggregate);
    if (expected == null) {
      return IScope.NULLSCOPE;
    }
    if (fieldName instanceof ExtensionFieldName) {
      return extensionScope(fieldName, expected, reference);
    }
    return memberScope(expected);
  }

  private IScope extensionScope(EObject context, EObject expected, EReference reference) {
    if (expected == null || context.eResource() == null) {
      return IScope.NULLSCOPE;
    }
    // Keep lexical/package-relative names; raw resource descriptions expose only qualified names.
    IScope visible = getLocalScopeProvider().getScope(context, reference);
    return new FilteringScope(visible, description -> {
      EObject target = org.eclipse.emf.ecore.util.EcoreUtil.resolve(description.getEObjectOrProxy(), context);
      return target instanceof IndexedElement && target.eContainer() instanceof TypeExtension
          && ((TypeExtension) target.eContainer()).getType() != null
          && ((TypeExtension) target.eContainer()).getType().getTarget() == expected;
    });
  }

  public IScope scope_AnyFieldName_embeddedType(AnyFieldName fieldName, EReference reference) {
    return fieldName == null || fieldName.eResource() == null ? IScope.NULLSCOPE
        : getLocalScopeProvider().getScope(fieldName, reference);
  }

  /**
   * Creates a scope containing elements of type {@code Literal} that can be referenced with their
   * local name only.
   *
   * <p>For example: <pre>
   * enum MyEnum {
   *   FOO = 1;
   * }
   * extend google.protobuf.ServiceOptions {
   *   optional MyEnum my_service_option = 50005;
   * }
   * service MyService {
   *   option (my_service_option) = FOO;
   * }
   * </pre>
   *
   * The {@code LiteralLink} {@code FOO} references the actual FOO declaration in MyEnum.
   */
  public IScope scope_LiteralLink_target(LiteralLink literalLink, EReference reference) {
    EObject expected = optionValues.expectedType(literalLink);
    if (expected instanceof Enum) {
      return memberScope(expected);
    }
    if (!(expected instanceof ScalarTypeLink) || literalLink.eResource() == null) {
      return IScope.NULLSCOPE;
    }
    literalLink.eResource().getContents();
    Protobuf root = modelObjects.rootOf(literalLink);
    if (root == null) {
      return IScope.NULLSCOPE;
    }
    String scalar = ((ScalarTypeLink) expected).getTarget().getName();
    boolean aggregate = literalLink.eContainer() instanceof ValueField;
    return cache.get(org.eclipse.xtext.util.Tuples.create("scalarLiterals", scalar, aggregate),
        literalLink.eResource(), () -> scalarScope(root, scalar, aggregate));
  }

  private IScope scalarScope(Protobuf root, String scalar, boolean aggregate) {
    List<IEObjectDescription> descriptions = new ArrayList<>();
    for (BuiltinLiteral builtin : root.getBuiltinLiterals()) {
      String name = builtin.getName();
      if ("bool".equals(scalar) && ("true".equals(name) || "false".equals(name))) {
        addDescription(descriptions, builtin);
        if (aggregate) {
          descriptions.add(EObjectDescription.create(QualifiedName.create("true".equals(name) ? "True" : "False"), builtin));
          descriptions.add(EObjectDescription.create(QualifiedName.create("true".equals(name) ? "t" : "f"), builtin));
        }
      } else if (("float".equals(scalar) || "double".equals(scalar))
          && ("inf".equals(name) || "nan".equals(name) || aggregate && "infinity".equals(name))) {
        addDescription(descriptions, builtin);
      }
    }
    return new SimpleScope(IScope.NULLSCOPE, descriptions,
        aggregate && ("float".equals(scalar) || "double".equals(scalar)));
  }

  /**
   * Recursively scopes the {@code OptionField} starting with the {@code OptionSource}.
   *
   * <p>For example: <pre>
   * message Code {
   *   optional double number = 1;
   * }
   * message Type {
   *   optional Code code = 1;
   * }
   * extend proto2.FieldOptions {
   *   optional Type type = 1000;
   * }
   * message Person {
   *   optional bool active = 1 [(type).code.number = 68];
   * }
   * </pre>
   *
   * The {@code OptionField} {@code number} contains a cross-reference to {@code Code.number}.
   */
  public IScope scope_OptionField_target(OptionField optionField, EReference reference) {
    if (optionField == null || optionField.eResource() == null
        || !(optionField.eContainer() instanceof AbstractOption)) {
      return IScope.NULLSCOPE;
    }
    AbstractOption option = (AbstractOption) optionField.eContainer();
    List<OptionField> fields = options.fieldsOf(option);
    int index = fields.indexOf(optionField);
    if (index < 0) {
      return IScope.NULLSCOPE;
    }
    IndexedElement declaration = index == 0 ? options.rootSourceOf(option) : fields.get(index - 1).getTarget();
    EObject expected = optionValues.typeOf(declaration);
    return optionField instanceof ExtensionOptionField
        ? extensionScope(optionField, expected, reference) : memberScope(expected);
  }

  /**
   * Creates a scope containing the default options defined in descriptor.proto.
   *
   * <p>For example: <pre>
   * option java_package = "com.example.foo";
   * </pre>
   *
   * The {@code OptionSource} {@code java_package} contains a cross-reference to {@code
   * google.protobuf.FileOptions.java_package} defined in descriptor.proto.
   */
  public IScope scope_OptionSource_target(OptionSource optionSource, EReference reference) {
    OptionType optionType = OptionType.typeOf(optionSource);
    Resource resource = optionSource.eResource();
    if (optionType == null || resource == null || !(optionSource.eContainer() instanceof AbstractOption)) {
      return IScope.NULLSCOPE;
    }
    Resource descriptor = getDescriptorResource(optionSource);
    if (descriptor == null || descriptor.getContents().isEmpty()) {
      return IScope.NULLSCOPE;
    }
    EObject root = descriptor.getContents().get(0);
    Message declaration = null;
    for (EObject candidate : root.eContents()) {
      if (candidate instanceof Message && optionType.messageName().equals(((Message) candidate).getName())) {
        declaration = (Message) candidate;
        break;
      }
    }
    if (declaration == null) {
      return IScope.NULLSCOPE;
    }
    if (options.isNative((AbstractOption) optionSource.eContainer())) {
      return memberScope(declaration);
    }
    final Message extendee = declaration;
    // Apply the same lexical/package normalization used for ordinary protobuf references.
    IScope visible = getLocalScopeProvider().getScope(optionSource, reference);
    return new FilteringScope(visible, description -> {
      EObject target = org.eclipse.emf.ecore.util.EcoreUtil.resolve(description.getEObjectOrProxy(), optionSource);
      return target instanceof IndexedElement && target.eContainer() instanceof TypeExtension
          && ((TypeExtension) target.eContainer()).getType() != null
          && ((TypeExtension) target.eContainer()).getType().getTarget() == extendee;
    });
  }
}
