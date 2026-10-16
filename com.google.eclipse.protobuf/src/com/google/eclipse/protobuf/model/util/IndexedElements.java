/*
 * Copyright (c) 2011 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.model.util;

import static java.util.Collections.emptyList;
import static org.eclipse.xtext.util.SimpleAttributeResolver.newResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.util.SimpleAttributeResolver;
import org.eclipse.xtext.util.IResourceScopeCache;
import org.eclipse.xtext.util.Tuples;

import com.google.common.collect.Iterables;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import com.google.eclipse.protobuf.protobuf.Extensions;
import com.google.eclipse.protobuf.protobuf.TypeExtension;
import com.google.eclipse.protobuf.model.util.IndexRanges.BackwardsRangeException;
import com.google.eclipse.protobuf.protobuf.FieldOption;
import com.google.eclipse.protobuf.protobuf.Group;
import com.google.eclipse.protobuf.protobuf.IndexRange;
import com.google.eclipse.protobuf.protobuf.IndexedElement;
import com.google.eclipse.protobuf.protobuf.Message;
import com.google.eclipse.protobuf.protobuf.OneOf;
import com.google.eclipse.protobuf.protobuf.Reserved;
import com.google.eclipse.protobuf.protobuf.BuiltinLiteral;
import com.google.eclipse.protobuf.protobuf.MapType;
import com.google.eclipse.protobuf.protobuf.MessageField;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.Provider;

/**
 * Utility methods related to <code>{@link IndexedElement}</code>s.
 *
 * @author alruiz@google.com (Alex Ruiz)
 */
@Singleton public class IndexedElements {
  private final static SimpleAttributeResolver<EObject, Long> INDEX_RESOLVER = newResolver(long.class, "index");

  @Inject private IndexRanges indexRanges;
  @Inject private ModelObjects modelObjects;
  @Inject private final IResourceScopeCache cache = IResourceScopeCache.NullImpl.INSTANCE;

  /**
   * Prefers a number above all occupied ranges, then searches legal gaps from one.
   * Returns -1 when no number is available or the declaration has no field scope.
   */
  public long calculateNewIndexFor(IndexedElement e) {
    return calculateNewIndexAfter(e, -1L);
  }

  /** Allocates with a pending document edit's tag occupied, without mutating the model. */
  public long calculateNewIndexAfter(IndexedElement e, long assignedIndex) {
    EObject owner = e == null ? null : e.eContainer();
    if (owner instanceof OneOf) {
      owner = owner.eContainer();
    }
    if (!(owner instanceof Message || owner instanceof Group || owner instanceof TypeExtension)) {
      return -1L;
    }
    RangeSet<Long> occupied = TreeRangeSet.create();
    RangeSet<Long> allowed = TreeRangeSet.create();
    long maximum = IndexRanges.MAX_FIELD_NUMBER;
    if (owner instanceof TypeExtension) {
      TypeExtension extension = (TypeExtension) owner;
      EObject target = extension.getType() == null ? null : extension.getType().getTarget();
      if (target == null || target.eIsProxy()) {
        return -1L;
      }
      if (indexRanges.isMessageSet(target)) {
        maximum = IndexRanges.MAX_MESSAGE_SET_EXTENSION_NUMBER;
      }
      for (EObject element : ownedElements(target)) {
        if (element instanceof Extensions) {
          for (IndexRange range : ((Extensions) element).getRanges()) {
            addRange(allowed, range, maximum);
          }
        }
      }
    } else {
      allowed.add(Range.closed(1L, maximum));
    }
    long highest = 0L;
    if (assignedIndex >= 1L && assignedIndex <= maximum) {
      occupied.add(Range.singleton(assignedIndex));
      highest = assignedIndex;
    }
    for (EObject element : ownedElements(owner)) {
      if (element instanceof IndexedElement) {
        long number = indexOf((IndexedElement) element);
        if (number >= 1 && number <= maximum) {
          occupied.add(Range.singleton(number));
          highest = Math.max(highest, number);
        }
      } else if (element instanceof Reserved || element instanceof Extensions) {
        Iterable<IndexRange> ranges = element instanceof Reserved
            ? Iterables.filter(((Reserved) element).getReservations(), IndexRange.class)
            : ((Extensions) element).getRanges();
        for (IndexRange range : ranges) {
          addRange(occupied, range, maximum);
        }
      }
    }
    if (owner instanceof TypeExtension && owner.eResource() != null
        && owner.eResource().getResourceSet() != null) {
      EObject target = ((TypeExtension) owner).getType().getTarget();
      List<org.eclipse.emf.ecore.resource.Resource> resources = owner.eResource().getResourceSet().getResources();
      for (int i = 0, size = resources.size(); i < size; i++) {
        org.eclipse.emf.ecore.resource.Resource resource = resources.get(i);
        for (EObject root : resource.getContents()) {
          for (TypeExtension extension : EcoreUtil2.getAllContentsOfType(root, TypeExtension.class)) {
            if (extension.getType() != null && extension.getType().getTarget() == target) {
              for (IndexedElement field : extension.getElements()) {
                long number = indexOf(field);
                if (number >= 1 && number <= maximum) {
                  occupied.add(Range.singleton(number));
                  highest = Math.max(highest, number);
                }
              }
            }
          }
        }
      }
    }
    for (Range<Long> range : occupied.asRanges()) {
      highest = Math.max(highest, range.upperEndpoint());
    }
    occupied.add(Range.closed(IndexRanges.FIRST_RESERVED_NUMBER, IndexRanges.LAST_RESERVED_NUMBER));
    allowed.removeAll(occupied);
    long preferred = firstAvailableAtOrAfter(allowed, highest + 1L);
    return preferred >= 1 ? preferred : firstAvailableAtOrAfter(allowed, 1L);
  }

  private long firstAvailableAtOrAfter(RangeSet<Long> available, long start) {
    for (Range<Long> range : available.asRanges()) {
      long first = range.lowerEndpoint()
          + (range.lowerBoundType() == com.google.common.collect.BoundType.OPEN ? 1L : 0L);
      long last = range.upperEndpoint()
          - (range.upperBoundType() == com.google.common.collect.BoundType.OPEN ? 1L : 0L);
      long candidate = Math.max(first, start);
      if (candidate <= last) {
        return candidate;
      }
    }
    return -1L;
  }

  private void addRange(RangeSet<Long> ranges, IndexRange source, long maximum) {
    try {
      Range<Long> range = indexRanges.toLongRange(source);
      long first = Math.max(1L, range.lowerEndpoint());
      long last = Math.min(maximum, range.upperEndpoint());
      if (first <= last) {
        ranges.add(Range.closed(first, last));
      }
    } catch (BackwardsRangeException e) {
      // An invalid range does not occupy any legal numbers.
    }
  }

  /** Direct declarations and real oneof members, never nested scope or derived metadata contents. */
  public List<EObject> ownedElements(final EObject owner) {
    if (owner == null) {
      return Collections.emptyList();
    }
    Provider<List<EObject>> declarations = () -> {
      List<EObject> elements = new ArrayList<>();
      collectOwnedElements(owner, elements);
      return Collections.unmodifiableList(elements);
    };
    return owner.eResource() == null ? declarations.get()
        : cache.get(Tuples.pair(owner, "semanticOwnedElements"), owner.eResource(), declarations);
  }

  private void collectOwnedElements(EObject owner, List<EObject> elements) {
    for (EObject element : owner.eContents()) {
      if (element instanceof BuiltinLiteral || element instanceof MessageField && owner instanceof MapType) {
        continue;
      }
      elements.add(element);
      if (element instanceof OneOf) {
        collectOwnedElements(element, elements);
      }
    }
  }

  /**
   * Returns the index of the given {@link IndexedElement}, or {@code Long.MIN_VALUE} if the given
   * {@code IndexedElement} is {@code null}.
   */
  public long indexOf(IndexedElement e) {
    long index = Long.MIN_VALUE;
    EStructuralFeature feature = indexFeatureOf(e);
    if (feature != null) {
      index = (Long) e.eGet(feature);
    }
    return index;
  }

  /**
   * Returns the options of the given <code>{@link IndexedElement}</code>.
   * @param e the given {@code IndexedElement}.
   * @return the options of the given {@code IndexedElement}, or an empty list if the given {@code IndexedElement} is
   * {@code null}.
   */
  @SuppressWarnings("unchecked")
  public List<FieldOption> fieldOptionsOf(IndexedElement e) {
    List<FieldOption> options = modelObjects.valueOfFeature(e, "fieldOptions", List.class);
    if (options == null) {
      options = emptyList();
    }
    return options;
  }

  /**
   * Sets the index of the given <code>{@link IndexedElement}</code>.
   * @param e e the given {@code IndexedElement}.
   * @param newIndex the new index to set.
   */
  public void setIndexTo(IndexedElement e, long newIndex) {
    EStructuralFeature feature = indexFeatureOf(e);
    if (feature != null) {
      e.eSet(feature, newIndex);
    }
  }

  /**
   * Returns the "index" structural feature of the given <code>{@link IndexedElement}</code>.
   * @param e the given {@code IndexedElement}.
   * @return the "index" structural feature of the given {@code IndexedElement}, or {@code null} if the given
   * {@code IndexedElement} is {@code null}.
   */
  public EStructuralFeature indexFeatureOf(IndexedElement e) {
    return (e != null) ? INDEX_RESOLVER.getAttribute(e) : null;
  }
}
