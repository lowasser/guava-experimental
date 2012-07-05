package com.google.common.collect;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Comparator;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ConcurrentSkipListMultiset<E>
    extends AbstractConcurrentMultiset<E> implements SortedMultiset<E> {
  
  public static <E extends Comparable<E>> ConcurrentSkipListMultiset<E> create() {
    return create(Ordering.natural());
  }
  
  public static <E> ConcurrentSkipListMultiset<E> create(Comparator<? super E> comparator) {
    return new ConcurrentSkipListMultiset<E>(
        new ConcurrentSkipListMap<E, AtomicInteger>(checkNotNull(comparator)));
  }

  ConcurrentSkipListMultiset(
      ConcurrentNavigableMap<E, AtomicInteger> countMap,
      SortedMultiset<E> descendingMultiset) {
    super(countMap);
    this.descendingMultiset = descendingMultiset;
  }

  ConcurrentSkipListMultiset(ConcurrentNavigableMap<E, AtomicInteger> countMap) {
    super(countMap);
  }

  @Override
  ConcurrentNavigableMap<E, AtomicInteger> backingMap() {
    return (ConcurrentNavigableMap<E, AtomicInteger>) super.backingMap();
  }

  @Override
  public Comparator<? super E> comparator() {
    return backingMap().comparator();
  }

  @Override
  public Entry<E> firstEntry() {
    return Iterables.getFirst(entrySet(), null);
  }

  @Override
  public Entry<E> lastEntry() {
    return Iterables.getFirst(descendingMultiset().entrySet(), null);
  }

  @Override
  public Entry<E> pollFirstEntry() {
    return Iterators.pollNext(entryIterator(), null);
  }

  @Override
  public com.google.common.collect.Multiset.Entry<E> pollLastEntry() {
    return descendingMultiset().pollFirstEntry();
  }

  private transient SortedMultiset<E> descendingMultiset;

  @Override
  public SortedMultiset<E> descendingMultiset() {
    SortedMultiset<E> result = descendingMultiset;
    if (result == null) {
      result = descendingMultiset = new ConcurrentSkipListMultiset<E>(
          backingMap().descendingMap(), this);
    }
    return result;
  }

  @Override
  public SortedMultiset<E> headMultiset(E upperBound, BoundType boundType) {
    return new ConcurrentSkipListMultiset<E>(
        backingMap().headMap(upperBound, boundType.isInclusive()));
  }

  @Override
  public SortedMultiset<E> subMultiset(
      E lowerBound,
      BoundType lowerBoundType,
      E upperBound,
      BoundType upperBoundType) {
    return new ConcurrentSkipListMultiset<E>(backingMap().subMap(
        lowerBound, lowerBoundType.isInclusive(), upperBound, upperBoundType.isInclusive()));
  }

  @Override
  public SortedMultiset<E> tailMultiset(E lowerBound, BoundType boundType) {
    return new ConcurrentSkipListMultiset<E>(
        backingMap().tailMap(lowerBound, boundType.isInclusive()));
  }

  @Override
  NavigableSet<E> createElementSet() {
    final NavigableSet<E> delegate = backingMap().navigableKeySet();
    return new ForwardingNavigableSet<E>() {
      @Override
      protected NavigableSet<E> delegate() {
        return delegate;
      }

      @Override
      public boolean remove(Object object) {
        try {
          return delegate.remove(object);
        } catch (NullPointerException e) {
          return false;
        } catch (ClassCastException e) {
          return false;
        }
      }

      @Override
      public boolean removeAll(Collection<?> c) {
        return standardRemoveAll(c);
      }
    };
  }

  @Override
  public NavigableSet<E> elementSet() {
    return (NavigableSet<E>) super.elementSet();
  }
}
