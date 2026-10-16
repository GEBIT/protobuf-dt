/*
 * Copyright (c) 2011 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.model.util;

import static org.eclipse.xtext.util.SimpleAttributeResolver.newResolver;

import com.google.common.collect.BoundType;
import com.google.common.collect.Iterables;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import com.google.eclipse.protobuf.model.util.IndexRanges.BackwardsRangeException;
import com.google.eclipse.protobuf.protobuf.EnumElement;
import com.google.eclipse.protobuf.protobuf.IndexRange;
import com.google.eclipse.protobuf.protobuf.ReservedEnum;
import com.google.inject.Inject;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.xtext.util.SimpleAttributeResolver;

import com.google.eclipse.protobuf.protobuf.Literal;
import com.google.inject.Singleton;

/**
 * Utility methods related to <code>{@link Literal}</code>.
 *
 * @author alruiz@google.com (Alex Ruiz)
 */
@Singleton public class Literals {
	private final static SimpleAttributeResolver<EObject, Long> INDEX_RESOLVER = newResolver(long.class, "index");
  @Inject private IndexRanges indexRanges;
  @Inject private FeatureSets features;
	
  /** Returns a free int32 enum value, or Long.MIN_VALUE if none exists. */
  public long calculateNewIndexOf(Literal literal) {
    return calculateNewIndexAfter(literal, Long.MIN_VALUE);
  }

  /** Allocates after a pending document assignment without mutating the enum. */
  public long calculateNewIndexAfter(Literal literal, long assignedIndex) {
    if (literal == null || !(literal.eContainer() instanceof com.google.eclipse.protobuf.protobuf.Enum)) {
      return Long.MIN_VALUE;
    }
    com.google.eclipse.protobuf.protobuf.Enum enumeration =
        (com.google.eclipse.protobuf.protobuf.Enum) literal.eContainer();
    RangeSet<Long> available = TreeRangeSet.create();
    available.add(Range.closed((long) Integer.MIN_VALUE, (long) Integer.MAX_VALUE));
    long highest = -1L;
    if (assignedIndex >= Integer.MIN_VALUE && assignedIndex <= Integer.MAX_VALUE) {
      available.remove(Range.singleton(assignedIndex));
      highest = Math.max(highest, assignedIndex);
    }
    boolean first = true;
    for (EnumElement element : enumeration.getElements()) {
      if (element instanceof Literal) {
        first = element == literal;
        break;
      }
    }
    for (EnumElement element : enumeration.getElements()) {
      if (element instanceof Literal && element != literal) {
        Literal other = (Literal) element;
        if (other.getIndex() >= Integer.MIN_VALUE && other.getIndex() <= Integer.MAX_VALUE) {
          available.remove(Range.singleton(other.getIndex()));
          highest = Math.max(highest, other.getIndex());
        }
      } else if (element instanceof ReservedEnum) {
        for (IndexRange source : Iterables.filter(((ReservedEnum) element).getReservations(), IndexRange.class)) {
          try {
            available.remove(indexRanges.toLongRange(source));
          } catch (BackwardsRangeException e) {
            // Invalid reservations do not occupy a number.
          }
        }
      }
    }
    if (first && assignedIndex == Long.MIN_VALUE && Boolean.TRUE.equals(features.isOpenEnum(enumeration))) {
      return available.contains(0L) ? 0L : Long.MIN_VALUE;
    }
    long candidate = firstAtOrAfter(available, highest + 1L);
    if (candidate != Long.MIN_VALUE) {
      return candidate;
    }
    candidate = firstAtOrAfter(available, 0L);
    return candidate != Long.MIN_VALUE ? candidate : firstAtOrAfter(available, Integer.MIN_VALUE);
  }

  private long firstAtOrAfter(RangeSet<Long> available, long start) {
    for (Range<Long> range : available.asRanges()) {
      long first = range.lowerEndpoint() + (range.lowerBoundType() == BoundType.OPEN ? 1L : 0L);
      long last = range.upperEndpoint() - (range.upperBoundType() == BoundType.OPEN ? 1L : 0L);
      long candidate = Math.max(first, start);
      if (candidate <= last) {
        return candidate;
      }
    }
    return Long.MIN_VALUE;
  }
  
  /**
   * Returns the index of the given {@link Literal}, or {@code Long.MIN_VALUE} if the given
   * {@code IndexedElement} is {@code null}.
   */
  public long indexOf(Literal e) {
    long index = Long.MIN_VALUE;
    EStructuralFeature feature = indexFeatureOf(e);
    if (feature != null) {
      index = (Long) e.eGet(feature);
    }
    return index;
  }
  
  /**
   * Returns the "index" structural feature of the given <code>{@link Literal}</code>.
   * @param e the given {@code Literal}.
   * @return the "index" structural feature of the given {@code Literal}, or {@code null} if the given
   * {@code Literal} is {@code null}.
   */
  public EStructuralFeature indexFeatureOf(Literal e) {
	  return (e != null) ? INDEX_RESOLVER.getAttribute(e) : null;
  }
}
