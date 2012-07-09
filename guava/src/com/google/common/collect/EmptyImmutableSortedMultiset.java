/*
 * Copyright (C) 2011 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.common.collect;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Comparator;

import javax.annotation.Nullable;

/**
 * An empty immutable sorted multiset.
 *
 * @author Louis Wasserman
 */
@SuppressWarnings("serial") // Uses writeReplace, not default serialization
final class EmptyImmutableSortedMultiset<E> extends ImmutableSortedMultiset<E> {
  EmptyImmutableSortedMultiset(Comparator<? super E> comparator) {
    super(comparator);
  }

  public Entry<E> firstEntry() {
    return null;
  }

  public Entry<E> lastEntry() {
    return null;
  }

  public int count(@Nullable Object element) {
    return 0;
  }

  public int size() {
    return 0;
  }

  @Override
  ImmutableSortedSet<E> createElementSet() {
    return ImmutableSortedSet.emptySet(comparator());
  }

  @Override
  ImmutableSortedSet<E> createDescendingElementSet() {
    return ImmutableSortedSet.emptySet(reverseComparator());
  }

  @Override
  ImmutableSet<Entry<E>> createEntrySet() {
    return ImmutableSet.of();
  }

  @Override
  public ImmutableSortedMultiset<E> headMultiset(E upperBound, BoundType boundType) {
    checkNotNull(upperBound);
    checkNotNull(boundType);
    return this;
  }

  @Override
  public ImmutableSortedMultiset<E> tailMultiset(E lowerBound, BoundType boundType) {
    checkNotNull(lowerBound);
    checkNotNull(boundType);
    return this;
  }

  @Override
  boolean isPartialView() {
    return false;
  }
}
