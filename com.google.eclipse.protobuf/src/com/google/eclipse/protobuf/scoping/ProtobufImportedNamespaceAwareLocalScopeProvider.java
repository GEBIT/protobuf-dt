/*
 * Copyright (c) 2016 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.scoping;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.resource.ISelectable;
import org.eclipse.xtext.scoping.IGlobalScopeProvider;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.impl.FilteringScope;
import org.eclipse.xtext.scoping.impl.ImportNormalizer;
import org.eclipse.xtext.scoping.impl.ImportScope;
import org.eclipse.xtext.scoping.impl.ImportedNamespaceAwareLocalScopeProvider;
import org.eclipse.xtext.util.Strings;

import com.google.eclipse.protobuf.naming.ProtobufQualifiedNameConverter;
import com.google.eclipse.protobuf.model.util.Resources;
import com.google.eclipse.protobuf.protobuf.AbstractOption;
import com.google.eclipse.protobuf.protobuf.ProtobufPackage;
import com.google.inject.Inject;

/**
 * A local scope provider for the Protocol Buffer language based on namespace resolvers.
 *
 * @author (atrookey@google.com) Alexander Rookey
 */
public class ProtobufImportedNamespaceAwareLocalScopeProvider
    extends ImportedNamespaceAwareLocalScopeProvider {
  @Inject private ProtobufQualifiedNameConverter qualifiedNameConverter;
  @Inject private IGlobalScopeProvider globalScopeProvider;
  @Inject private ImportVisibility visibility;
  @Inject private Resources resources;

  private static final boolean WILDCARD = true;

  @Override
  public IScope getScope(final EObject context, EReference reference) {
    if (context == null || context.eResource() == null) {
      return IScope.NULLSCOPE;
    }
    IScope scope = unfilteredScope(context, reference);
    final Resource resource = context.eResource();
    final ResourceSet resourceSet = resource.getResourceSet();
    final URI ownUri = ImportVisibility.normalizedUri(resource.getURI(), resourceSet);
    final Set<URI> imported = visibility.importedUris(
        resources.rootOf(resource), isOptionReference(context, reference));
    return new FilteringScope(scope, description -> {
      URI targetUri = description.getEObjectURI();
      if (targetUri == null) {
        return false;
      }
      URI targetResource = ImportVisibility.normalizedUri(targetUri, resourceSet);
      // The import set deliberately excludes this resource, including local declarations.
      if (targetResource.equals(ownUri)) {
        return true;
      }
      if (!imported.contains(targetResource) || resourceSet == null) {
        return false;
      }
      EObject target = description.getEObjectOrProxy();
      if (target.eIsProxy() || target.eResource() == null
          || target.eResource().getResourceSet() != resourceSet) {
        try {
          target = resourceSet.getEObject(targetUri, true);
        } catch (RuntimeException exception) {
          return false;
        }
      }
      return visibility.isVisible(target, context);
    });
  }

  /**
   * Preserve Xtext 2.10's lexical scope assembly without its virtual getScope recursion:
   * filtering a container scope would repeat the import walk and lose the original use context.
   */
  private IScope unfilteredScope(EObject context, EReference reference) {
    IScope parent = context.eContainer() == null
        ? getResourceScope(context.eResource(), reference)
        : unfilteredScope(context.eContainer(), reference);
    return getLocalElementsScope(parent, context, reference);
  }

  private boolean isOptionReference(EObject context, EReference reference) {
    if (reference == ProtobufPackage.Literals.OPTION_SOURCE__TARGET
        || reference == ProtobufPackage.Literals.OPTION_FIELD__TARGET
        || reference == ProtobufPackage.Literals.FIELD_NAME__TARGET
        || reference == ProtobufPackage.Literals.ANY_FIELD_NAME__EMBEDDED_TYPE
        || reference == ProtobufPackage.Literals.LITERAL_LINK__TARGET) {
      return true;
    }
    for (EObject owner = context; owner != null; owner = owner.eContainer()) {
      if (owner instanceof AbstractOption) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected ImportScope createImportScope(
      IScope parent,
      List<ImportNormalizer> namespaceResolvers,
      ISelectable importFrom,
      EClass type,
      boolean ignoreCase) {
    return new ProtobufImportScope(namespaceResolvers, parent, importFrom, type, ignoreCase);
  }

  @Override
  protected List<ImportNormalizer> internalGetImportedNamespaceResolvers(
      final EObject context, boolean ignoreCase) {
    List<ImportNormalizer> importedNamespaceResolvers = new ArrayList<>();
    EList<EObject> eContents = context.eContents();
    for (EObject child : eContents) {
      String name = getImportedNamespace(child);
      if (name != null && !name.isEmpty()) {
        ImportNormalizer resolver = createImportedNamespaceResolver(name, ignoreCase);
        if (resolver != null) {
          importedNamespaceResolvers.add(resolver);
        }
        importedNamespaceResolvers.addAll(createResolversForInnerNamespaces(name, ignoreCase));
      }
    }
    return importedNamespaceResolvers;
  }

  /**
   * Creates resolvers required for scoping to handle intersecting packages. The imported namespace
   * {@code com.google.proto.foo} requires the following resolvers:
   *
   * <ul>
   * <li>{@code com.*}
   * <li>{@code com.google.*}
   * <li>{@code com.google.proto.*}
   * </ul>
   *
   * @param namespace the namespace.
   * @param ignoreCase {@code true} if the resolver should be case insensitive.
   * @return a list of the resolvers for an imported namespace
   */
  private List<ImportNormalizer> createResolversForInnerNamespaces(
      String namespace, boolean ignoreCase) {
    String[] splitValue = namespace.split("\\.");
    List<ImportNormalizer> importedNamespaceResolvers = new ArrayList<>();
    String currentNamespaceResolver = "";
    for (int i = 0; i < Array.getLength(splitValue) - 1; i++) {
      currentNamespaceResolver += splitValue[i] + ".";
      ImportNormalizer resolver =
          createImportedNamespaceResolver(currentNamespaceResolver, ignoreCase);
      if (resolver != null) {
        importedNamespaceResolvers.add(resolver);
      }
    }
    return importedNamespaceResolvers;
  }

  /** Creates an {@link ImportNormalizer} with wildcards. */
  @Override
  protected ImportNormalizer createImportedNamespaceResolver(String namespace, boolean ignoreCase) {
    if (Strings.isEmpty(namespace)) {
      return null;
    }
    QualifiedName importedNamespace = qualifiedNameConverter.toQualifiedName(namespace);
    if (importedNamespace == null || importedNamespace.isEmpty()) {
      return null;
    }
    return doCreateImportNormalizer(importedNamespace, WILDCARD, ignoreCase);
  }

  /** Returns a {@link ProtobufSelectableBasedScope} instead of {@link SelectableBasedScope} */
  @Override
  protected IScope getResourceScope(IScope parent, EObject context, EReference reference) {
    if (context.eResource() == null) {
      return parent;
    }
    ISelectable allDescriptions = getAllDescriptions(context.eResource());
    return ProtobufSelectableBasedScope.createScope(
        parent, allDescriptions, reference.getEReferenceType(), isIgnoreCase(reference));
  }

  protected ProtobufImportUriGlobalScopeProvider getGlobalScopeProvider() {
    return (ProtobufImportUriGlobalScopeProvider) globalScopeProvider;
  }
}
