/*
 * Copyright (C) 2007 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.collect;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A multiset that supports concurrent modifications and that provides atomic versions of most
 * {@code Multiset} operations (exceptions where noted). Null elements are not supported.
 *
 * <p>See the Guava User Guide article on <a href=
 * "http://code.google.com/p/guava-libraries/wiki/NewCollectionTypesExplained#Multiset">
 * {@code Multiset}</a>.
 *
 * @author Cliff L. Biffle
 * @author mike nonemacher
 * @since 2.0 (imported from Google Collections Library)
 */
public final class ConcurrentHashMultiset<E>
    extends AbstractConcurrentMultiset<E> implements Serializable {

  /**
   * Creates a new, empty {@code ConcurrentHashMultiset} using the default
   * initial capacity, load factor, and concurrency settings.
   */
  public static <E> ConcurrentHashMultiset<E> create() {
    // TODO(schmoe): provide a way to use this class with other (possibly arbitrary)
    // ConcurrentMap implementors. One possibility is to extract most of this class into
    // an AbstractConcurrentMapMultiset.
    return new ConcurrentHashMultiset<E>(new ConcurrentHashMap<E, AtomicInteger>());
  }

  /**
   * Creates a new {@code ConcurrentHashMultiset} containing the specified elements, using
   * the default initial capacity, load factor, and concurrency settings.
   *
   * <p>This implementation is highly efficient when {@code elements} is itself a {@link Multiset}.
   *
   * @param elements the elements that the multiset should contain
   */
  public static <E> ConcurrentHashMultiset<E> create(Iterable<? extends E> elements) {
    ConcurrentHashMultiset<E> multiset = ConcurrentHashMultiset.create();
    Iterables.addAll(multiset, elements);
    return multiset;
  }

  /**
   * Creates a new, empty {@code ConcurrentHashMultiset} using {@code mapMaker}
   * to construct the internal backing map.
   *
   * <p>If this {@link MapMaker} is configured to use entry eviction of any kind, this eviction
   * applies to all occurrences of a given element as a single unit. However, most updates to the
   * multiset do not count as map updates at all, since we're usually just mutating the value
   * stored in the map, so {@link MapMaker#expireAfterAccess} makes sense (evict the entry that
   * was queried or updated longest ago), but {@link MapMaker#expireAfterWrite} doesn't, because
   * the eviction time is measured from when we saw the first occurrence of the object.
   *
   * <p>The returned multiset is serializable but any serialization caveats
   * given in {@code MapMaker} apply.
   *
   * <p>Finally, soft/weak values can be used but are not very useful: the values are created
   * internally and not exposed externally, so no one else will have a strong reference to the
   * values. Weak keys on the other hand can be useful in some scenarios.
   *
   * @since 7.0
   */
  @Beta
  public static <E> ConcurrentHashMultiset<E> create(
      GenericMapMaker<? super E, ? super Number> mapMaker) {
    return new ConcurrentHashMultiset<E>(mapMaker.<E, AtomicInteger>makeMap());
  }

  /**
   * Creates an instance using {@code countMap} to store elements and their counts.
   *
   * <p>This instance will assume ownership of {@code countMap}, and other code
   * should not maintain references to the map or modify it in any way.
   *
   * @param countMap backing map for storing the elements in the multiset and
   *     their counts. It must be empty.
   * @throws IllegalArgumentException if {@code countMap} is not empty
   */
  @VisibleForTesting ConcurrentHashMultiset(ConcurrentMap<E, AtomicInteger> countMap) {
    super(countMap);
  }
}
