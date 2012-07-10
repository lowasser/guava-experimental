package com.google.common.collect;

import com.google.common.collect.Sets.ImprovedAbstractSet;

import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedSet;

abstract class AbstractNavigableSet<E> extends ImprovedAbstractSet<E> implements NavigableSet<E> {
  @Override
  public E first() {
    return iterator().next();
  }

  @Override
  public E last() {
    return descendingIterator().next();
  }

  @Override
  public E lower(E e) {
    return Iterators.getNext(headSet(e, false).descendingIterator(), null);
  }

  @Override
  public E floor(E e) {
    return Iterators.getNext(headSet(e, true).descendingIterator(), null);
  }

  @Override
  public E ceiling(E e) {
    return Iterators.getNext(tailSet(e, true).iterator(), null);
  }

  @Override
  public E higher(E e) {
    return Iterators.getNext(tailSet(e, false).iterator(), null);
  }

  @Override
  public E pollFirst() {
    return Iterators.pollNext(iterator(), null);
  }

  @Override
  public E pollLast() {
    return Iterators.pollNext(descendingIterator(), null);
  }
  
  private transient NavigableSet<E> descendingSet;
  
  @Override
  public NavigableSet<E> descendingSet() {
    NavigableSet<E> result = descendingSet;
    return (result == null) ? descendingSet = createDescendingSet() : result;
  }
  
  NavigableSet<E> createDescendingSet() {
    return new DescendingSet<E>(this);
  }

  static class DescendingSet<E> extends ForwardingSet<E> implements NavigableSet<E> {
    final NavigableSet<E> forward;

    DescendingSet(NavigableSet<E> forward) {
      this.forward = forward;
    }

    @Override
    public Comparator<? super E> comparator() {
      return Ordering.from(SortedIterables.comparator(forward)).reverse();
    }

    @Override
    public E first() {
      return forward.last();
    }

    @Override
    public E last() {
      return forward.first();
    }

    @Override
    public E lower(E e) {
      return forward.higher(e);
    }

    @Override
    public E floor(E e) {
      return forward.ceiling(e);
    }

    @Override
    public E ceiling(E e) {
      return forward.floor(e);
    }

    @Override
    public E higher(E e) {
      return forward.lower(e);
    }

    @Override
    public E pollFirst() {
      return forward.pollLast();
    }

    @Override
    public E pollLast() {
      return forward.pollFirst();
    }

    @Override
    public NavigableSet<E> descendingSet() {
      return forward;
    }

    @Override
    public Iterator<E> descendingIterator() {
      return forward.iterator();
    }

    @Override
    public NavigableSet<E> subSet(
        E fromElement,
        boolean fromInclusive,
        E toElement,
        boolean toInclusive) {
      return forward.subSet(toElement, toInclusive, fromElement, fromInclusive).descendingSet();
    }

    @Override
    public NavigableSet<E> headSet(E toElement, boolean inclusive) {
      return forward.tailSet(toElement, inclusive).descendingSet();
    }

    @Override
    public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
      return forward.headSet(fromElement, inclusive).descendingSet();
    }

    @Override
    public SortedSet<E> subSet(E fromElement, E toElement) {
      return subSet(fromElement, true, toElement, false);
    }

    @Override
    public SortedSet<E> headSet(E toElement) {
      return headSet(toElement, false);
    }

    @Override
    public SortedSet<E> tailSet(E fromElement) {
      return tailSet(fromElement, true);
    }

    @Override
    protected Set<E> delegate() {
      return forward;
    }

    @Override
    public Iterator<E> iterator() {
      return forward.descendingIterator();
    }

    @Override
    public Object[] toArray() {
      return standardToArray();
    }

    @Override
    public <T> T[] toArray(T[] array) {
      return standardToArray(array);
    }

    @Override
    public String toString() {
      return standardToString();
    }
  }

  @Override
  public NavigableSet<E> subSet(E fromElement, E toElement) {
    return subSet(fromElement, true, toElement, false);
  }

  @Override
  public NavigableSet<E> headSet(E toElement) {
    return headSet(toElement, false);
  }

  @Override
  public NavigableSet<E> tailSet(E fromElement) {
    return tailSet(fromElement, true);
  }
}
