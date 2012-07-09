/*
 * Copyright (C) 2008 The Guava Authors
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
import com.google.common.annotations.GwtCompatible;
import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;

import javax.annotation.Nullable;

/**
 * {@code FluentIterable} provides a rich interface for manipulating {@code Iterable}s in a chained
 * fashion. A {@code FluentIterable} can be created from an {@code Iterable}, or from a set of
 * elements. The following types of methods are provided on {@code FluentIterable}:
 * <ul>
 * <li>chained methods which return a new {@code FluentIterable} based in some way on the contents
 * of the current one (for example {@link #transform})
 * <li>conversion methods which copy the {@code FluentIterable}'s contents into a new collection or
 * array (for example {@link #toImmutableList})
 * <li>element extraction methods which facilitate the retrieval of certain elements (for example
 * {@link #last})
 * <li>query methods which answer questions about the {@code FluentIterable}'s contents (for example
 * {@link #anyMatch})
 * </ul>
 *
 * <p>Here is an example that merges the lists returned by two separate database calls, transforms
 * it by invoking {@code toString()} on each element, and returns the first 10 elements as an
 * {@code ImmutableList}: <pre>   {@code
 *
 *   FluentIterable
 *       .from(database.getClientList())
 *       .transform(Functions.toStringFunction())
 *       .limit(10)
 *       .toImmutableList();}</pre>
 *
 * Anything which can be done using {@code FluentIterable} could be done in a different fashion
 * (often with {@link Iterables}), however the use of {@code FluentIterable} makes many sets of
 * operations significantly more concise.
 *
 * @author Marcin Mikosik
 * @since 12.0
 */
@Beta
@GwtCompatible(emulated = true)
public abstract class FluentIterable<E> implements Iterable<E> {
  // We store 'iterable' and use it instead of 'this' to allow Iterables to perform instanceof
  // checks on the _original_ iterable when FluentIterable.from is used.
  private final Iterable<E> iterable;

  /** Constructor for use by subclasses. */
  protected FluentIterable() {
    this.iterable = this;
  }

  FluentIterable(Iterable<E> iterable) {
    this.iterable = Preconditions.checkNotNull(iterable);
  }

  /**
   * Returns a fluent iterable that wraps {@code iterable}, or {@code iterable} itself if it
   * is already a {@code FluentIterable}.
   */
  public static <E> FluentIterable<E> from(final Iterable<E> iterable) {
    return (iterable instanceof FluentIterable) ? (FluentIterable<E>) iterable
        : new FluentIterable<E>(iterable) {
          public Iterator<E> iterator() {
            return iterable.iterator();
          }
        };
  }

  /**
   * Construct a fluent iterable from another fluent iterable. This is obviously never necessary,
   * but is intended to help call out cases where one migration from {@code Iterable} to
   * {@code FluentIterable} has obviated the need to explicitly convert to a {@code FluentIterable}.
   *
   * @deprecated instances of {@code FluentIterable} don't need to be converted to
   *     {@code FluentIterable}
   */
  @Deprecated
  public static <E> FluentIterable<E> from(FluentIterable<E> iterable) {
    return Preconditions.checkNotNull(iterable);
  }

  /**
   * Returns a string representation of this fluent iterable, with the format
   * {@code [e1, e2, ..., en]}.
   */
  @Override
  public String toString() {
    return Iterables.toString(iterable);
  }

  /**
   * Returns the number of elements in this fluent iterable.
   */
  public final int size() {
    return Iterables.size(iterable);
  }

  /**
   * Returns {@code true} if this fluent iterable contains any object for which
   * {@code equals(element)} is true.
   */
  public final boolean contains(@Nullable Object element) {
    return Iterables.contains(iterable, element);
  }

  /**
   * Returns a fluent iterable whose {@code Iterator} cycles indefinitely over the elements of
   * this fluent iterable.
   *
   * <p>That iterator supports {@code remove()} if {@code iterable.iterator()} does. After
   * {@code remove()} is called, subsequent cycles omit the removed element, which is no longer in
   * this fluent iterable. The iterator's {@code hasNext()} method returns {@code true} until
   * this fluent iterable is empty.
   *
   * <p><b>Warning:</b> Typical uses of the resulting iterator may produce an infinite loop. You
   * should use an explicit {@code break} or be certain that you will eventually remove all the
   * elements.
   */
  public final FluentIterable<E> cycle() {
    return from(Iterables.cycle(iterable));
  }

  /**
   * Returns the elements from this fluent iterable that satisfy a predicate. The
   * resulting fluent iterable's iterator does not support {@code remove()}.
   */
  public final FluentIterable<E> filter(Predicate<? super E> predicate) {
    return from(Iterables.filter(iterable, predicate));
  }

  /**
   * Returns the elements from this fluent iterable that are instances of class {@code type}.
   *
   * @param type the type of elements desired
   */
  @GwtIncompatible("Class.isInstance")
  public final <T> FluentIterable<T> filter(Class<T> type) {
    return from(Iterables.filter(iterable, type));
  }

  /**
   * Returns {@code true} if any element in this fluent iterable satisfies the predicate.
   */
  public final boolean anyMatch(Predicate<? super E> predicate) {
    return Iterables.any(iterable, predicate);
  }

  /**
   * Returns {@code true} if every element in this fluent iterable satisfies the predicate.
   * If this fluent iterable is empty, {@code true} is returned.
   */
  public final boolean allMatch(Predicate<? super E> predicate) {
    return Iterables.all(iterable, predicate);
  }

  /**
   * Returns an {@link Optional} containing the first element in this fluent iterable that
   * satisfies the given predicate, if such an element exists.
   *
   * <p><b>Warning:</b> avoid using a {@code predicate} that matches {@code null}. If {@code null}
   * is matched in this fluent iterable, a {@link NullPointerException} will be thrown.
   */
  public final Optional<E> firstMatch(Predicate<? super E> predicate) {
    return Iterables.tryFind(iterable, predicate);
  }

  /**
   * Returns a fluent iterable that applies {@code function} to each element of this
   * fluent iterable.
   *
   * <p>The returned fluent iterable's iterator supports {@code remove()} if this iterable's
   * iterator does. After a successful {@code remove()} call, this fluent iterable no longer
   * contains the corresponding element.
   */
  public final <T> FluentIterable<T> transform(Function<? super E, T> function) {
    return from(Iterables.transform(iterable, function));
  }

  /**
   * Returns an {@link Optional} containing the first element in this fluent iterable.
   * If the iterable is empty, {@code Optional.absent()} is returned.
   *
   * @throws NullPointerException if the first element is null; if this is a possibility, use
   *     {@code iterator().next()} or {@link Iterables#getFirst} instead.
   */
  public final Optional<E> first() {
    Iterator<E> iterator = iterable.iterator();
    return iterator.hasNext()
        ? Optional.of(iterator.next())
        : Optional.<E>absent();
  }

  /**
   * Returns an {@link Optional} containing the last element in this fluent iterable.
   * If the iterable is empty, {@code Optional.absent()} is returned.
   *
   * @throws NullPointerException if the last element is null; if this is a possibility, use
   *     {@link Iterables#getLast} instead.
   */
  public final Optional<E> last() {
    // Iterables#getLast was inlined here so we don't have to throw/catch a NSEE

    // TODO(kevinb): Support a concurrently modified collection?
    if (iterable instanceof List) {
      List<E> list = (List<E>) iterable;
      if (list.isEmpty()) {
        return Optional.absent();
      }
      return Optional.of(list.get(list.size() - 1));
    }
    Iterator<E> iterator = iterable.iterator();
    if (!iterator.hasNext()) {
      return Optional.absent();
    }

    /*
     * TODO(kevinb): consider whether this "optimization" is worthwhile. Users
     * with SortedSets tend to know they are SortedSets and probably would not
     * call this method.
     */
    if (iterable instanceof SortedSet) {
      SortedSet<E> sortedSet = (SortedSet<E>) iterable;
      return Optional.of(sortedSet.last());
    }

    while (true) {
      E current = iterator.next();
      if (!iterator.hasNext()) {
        return Optional.of(current);
      }
    }
  }

  /**
   * Returns a view of this fluent iterable that skips its first {@code numberToSkip}
   * elements. If this fluent iterable contains fewer than {@code numberToSkip} elements,
   * the returned fluent iterable skips all of its elements.
   *
   * <p>Modifications to this fluent iterable before a call to {@code iterator()} are
   * reflected in the returned fluent iterable. That is, the its iterator skips the first
   * {@code numberToSkip} elements that exist when the iterator is created, not when {@code skip()}
   * is called.
   *
   * <p>The returned fluent iterable's iterator supports {@code remove()} if the
   * {@code Iterator} of this fluent iterable supports it. Note that it is <i>not</i>
   * possible to delete the last skipped element by immediately calling {@code remove()} on the
   * returned fluent iterable's iterator, as the {@code Iterator} contract states that a call
   * to {@code * remove()} before a call to {@code next()} will throw an
   * {@link IllegalStateException}.
   */
  public final FluentIterable<E> skip(int numberToSkip) {
    return from(Iterables.skip(iterable, numberToSkip));
  }

  /**
   * Creates a fluent iterable with the first {@code size} elements of this
   * fluent iterable. If this fluent iterable does not contain that many elements,
   * the returned fluent iterable will have the same behavior as this fluent iterable.
   * The returned fluent iterable's iterator supports {@code remove()} if this
   * fluent iterable's iterator does.
   *
   * @param size the maximum number of elements in the returned fluent iterable
   * @throws IllegalArgumentException if {@code size} is negative
   */
  public final FluentIterable<E> limit(int size) {
    return from(Iterables.limit(iterable, size));
  }

  /**
   * Determines whether this fluent iterable is empty.
   */
  public final boolean isEmpty() {
    return !iterable.iterator().hasNext();
  }

  /**
   * Returns an {@code ImmutableList} containing all of the elements from this
   * fluent iterable in proper sequence.
   */
  public final ImmutableList<E> toImmutableList() {
    return ImmutableList.copyOf(iterable);
  }

  /**
   * Returns an {@code ImmutableSet} containing all of the elements from this
   * fluent iterable with duplicates removed.
   */
  public final ImmutableSet<E> toImmutableSet() {
    return ImmutableSet.copyOf(iterable);
  }

  /**
   * Returns an {@code ImmutableSortedSet} containing all of the elements from this
   * {@code FluentIterable} in the order specified by {@code comparator}, with duplicates
   * (determined by {@code comaprator.compare(x, y) == 0}) removed. To produce an
   * {@code ImmutableSortedSet} sorted by its natural ordering, use
   * {@code toImmutableSortedSet(Ordering.natural())}.
   *
   * @param comparator the function by which to sort set elements
   * @throws NullPointerException if any element is null
   */
  public final ImmutableSortedSet<E> toImmutableSortedSet(Comparator<? super E> comparator) {
    return ImmutableSortedSet.copyOf(comparator, iterable);
  }

  /**
   * Returns an array containing all of the elements from this fluent iterable in iteration order.
   *
   * @param type the type of the elements
   * @return a newly-allocated array into which all the elements of this fluent iterable have
   *     been copied
   */
  @GwtIncompatible("Array.newArray(Class, int)")
  public final E[] toArray(Class<E> type) {
    return Iterables.toArray(iterable, type);
  }

  /**
   * Returns the element at the specified position in this fluent iterable.
   *
   * @param position position of the element to return
   * @return the element at the specified position in this fluent iterable
   * @throws IndexOutOfBoundsException if {@code position} is negative or greater than or equal to
   *     the size of this fluent iterable
   */
  public final E get(int position) {
    return Iterables.get(iterable, position);
  }

  /**
   * Function that transforms {@code Iterable<E>} into a fluent iterable.
   */
  private static class FromIterableFunction<E>
      implements Function<Iterable<E>, FluentIterable<E>> {
    public FluentIterable<E> apply(Iterable<E> fromObject) {
      return FluentIterable.from(fromObject);
    }
  }
}
