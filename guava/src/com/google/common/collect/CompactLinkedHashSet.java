package com.google.common.collect;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * CompactLinkedHashSet is an implementation of a Set. All optional operations (adding and
 * removing) are supported. All elements, including {@code null}, are permitted..
 *
 * <p>{@code contains(x)}, {@code add(x)} and {@code remove(x)}, are all (expected and amortized)
 * constant time operations. Expected in the hashtable sense (depends on the hash function
 * doing a good job of distributing the elements to the buckets to a distribution not far from
 * uniform), and amortized since some operations can trigger a hash table resize.
 *
 * <p>This implementation consumes significantly less memory than {@code java.util.LinkedHashSet}
 * or even {@code java.util.HashSet}, and places considerably less load on the garbage collector.
 * Like {@code java.util.LinkedHashSet}, it offers insertion-order iteration, with identical
 * behavior.
 *
 * @author Dimitris Andreou
 * @author Louis Wasserman
 */
public class CompactLinkedHashSet<E> extends CompactHashSet<E> {

  /**
   * Creates an empty {@code CompactLinkedHashSet} instance.
   */
  public static <E> CompactLinkedHashSet<E> create() {
    return new CompactLinkedHashSet<E>();
  }

  public static <E> CompactLinkedHashSet<E> create(Collection<? extends E> collection) {
    CompactLinkedHashSet<E> set = create();
    set.addAll(collection);
    return set;
  }

  /**
   * Creates a <i>mutable</i> {@code CompactHashSet} instance containing the given
   * elements in unspecified order.
   *
   * @param elements the elements that the set should contain
   * @return a new {@code CompactHashSet} containing those elements (minus duplicates)
   */
  public static <E> CompactLinkedHashSet<E> create(E... elements) {
    CompactLinkedHashSet<E> set = create();
    Collections.addAll(set, elements);
    return set;
  }

  /**
   * Creates a {@code CompactLinkedHashSet} instance, with a high enough "initial capacity"
   * that it <i>should</i> hold {@code expectedSize} elements without growth.
   *
   * @param expectedSize the number of elements you expect to add to the returned set
   * @return a new, empty {@code CompactLinkedHashSet} with enough capacity to hold {@code
   *         expectedSize} elements without resizing
   * @throws IllegalArgumentException if {@code expectedSize} is negative
   */
  public static <E> CompactLinkedHashSet<E> createWithExpectedSize(int expectedSize) {
    return new CompactLinkedHashSet<E>(expectedSize);
  }
  
  private static final int UNSET = -1;
  private static final int ENDPOINT = -2;
  
  /**
   * Pointer to the predecessor of an entry in insertion order. ENDPOINT indicates a node is the
   * first node in insertion order; all values at indices >= size() are UNSET.
   */
  private transient int[] predecessor;

  /**
   * Pointer to the successor of an entry in insertion order. ENDPOINT indicates a node is the last
   * node in insertion order; all values at indices >= size() are UNSET.
   */
  private transient int[] successor;

  private transient int firstEntry;
  private transient int lastEntry;

  CompactLinkedHashSet() {
    super();
  }

  CompactLinkedHashSet(int capacity) {
    super(capacity);
  }

  @Override
  void init(int initialCapacity, float loadFactor) {
    super.init(initialCapacity, loadFactor);
    this.predecessor = new int[initialCapacity];
    this.successor = new int[initialCapacity];

    Arrays.fill(predecessor, UNSET);
    Arrays.fill(successor, UNSET);
    firstEntry = ENDPOINT;
    lastEntry = ENDPOINT;
  }

  private void succeeds(int pred, int succ) {
    if (pred == ENDPOINT) {
      firstEntry = succ;
    } else {
      successor[pred] = succ;
    }

    if (succ == ENDPOINT) {
      lastEntry = pred;
    } else {
      predecessor[succ] = pred;
    }
  }

  @Override
  void insertEntry(int entryIndex, E object, int hash) {
    super.insertEntry(entryIndex, object, hash);
    succeeds(lastEntry, entryIndex);
    succeeds(entryIndex, ENDPOINT);
  }

  @Override
  void moveEntry(int dstIndex) {
    int srcIndex = size() - 1;
    super.moveEntry(dstIndex);

    succeeds(predecessor[dstIndex], successor[dstIndex]);
    if (srcIndex != dstIndex) {
      succeeds(predecessor[srcIndex], dstIndex);
      succeeds(dstIndex, successor[srcIndex]);
    }
    predecessor[srcIndex] = UNSET;
    successor[srcIndex] = UNSET;
  }

  @Override
  public void clear() {
    super.clear();
    firstEntry = ENDPOINT;
    lastEntry = ENDPOINT;
    Arrays.fill(predecessor, UNSET);
    Arrays.fill(successor, UNSET);
  }

  @Override
  void resizeEntries(int newCapacity) {
    super.resizeEntries(newCapacity);
    int oldCapacity = predecessor.length;
    predecessor = Arrays.copyOf(predecessor, newCapacity);
    successor = Arrays.copyOf(successor, newCapacity);

    if (oldCapacity < newCapacity) {
      Arrays.fill(predecessor, oldCapacity, newCapacity, UNSET);
      Arrays.fill(successor, oldCapacity, newCapacity, UNSET);
    }
  }

  @Override
  public Iterator<E> iterator() {
    return new Iterator<E>() {
      int next = firstEntry;
      int toRemove = UNSET;
      int expectedModCount = modCount;
      
      private void checkForConcurrentModification() {
        if (modCount != expectedModCount) {
          throw new ConcurrentModificationException();
        }
      }

      @Override
      public boolean hasNext() {
        checkForConcurrentModification();
        return next != ENDPOINT;
      }

      @Override
      public E next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        @SuppressWarnings("unchecked") // elements only contains E's
        E result = (E) elements[next];
        toRemove = next;
        next = successor[next];
        return result;
      }

      @Override
      public void remove() {
        Iterators.checkRemove(toRemove != UNSET);
        CompactLinkedHashSet.this.remove(elements[toRemove]);
        if (next == size()) {
          next = toRemove; // we moved the next entry into the deleted position!
        }
        toRemove = UNSET;
        expectedModCount = modCount;
      }
    };
  }
}
