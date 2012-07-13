/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements. See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.common.collect;

import static com.google.common.collect.Hashing.smear;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Preconditions;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import javax.annotation.Nullable;


/**
 * CompactHashSet is an implementation of a Set. All optional operations (adding and
 * removing) are supported. The elements can be any objects.
 *
 * <p>{@code contains(x)}, {@code add(x)} and {@code remove(x)}, are all (expected and amortized)
 * constant time operations. Expected in the hashtable sense (depends on the hash function
 * doing a good job of distributing the elements to the buckets to a distribution not far from
 * uniform), and amortized since some operations can trigger a hash table resize.
 *
 * <p>Unlike {@code java.util.HashSet}, iteration is only proportional to the actual
 * {@code size()}, which is optimal, and <i>not</i> the size of the internal hashtable,
 * which could be much larger than {@code size()}. Furthermore, this structure only depends
 * on a fixed number of arrays; {@code add(x)} operations <i>do not</i> create objects
 * for the garbage collector to deal with, and for every element added, the garbage collector
 * will have to traverse {@code 1.5} references on average, in the marking phase, not {@code 5.0}
 * as in {@code java.util.HashSet}.
 *
 * <p>If there are no removals, then {@link #iterator iteration} order is the same as insertion
 * order. Any removal invalidates any ordering guarantees.
 *
 * @author Dimitris Andreou
 */
@GwtIncompatible("java.util.Arrays#copyOf(Object[], int), java.lang.reflect.Array")
public class CompactHashSet<E> extends AbstractSet<E> implements Serializable {
  // TODO(andreou): constructors and static factories? What to keep, what to drop?
  // TODO(andreou): cache all field accesses in local vars

  private static final int MAXIMUM_CAPACITY = 1 << 30;

  private static final float DEFAULT_LOAD_FACTOR = 1.0f;

  private static final long LOW_32_BITS_MASK  = (1L << 32) - 1;
  private static final long HIGH_32_BITS_MASK = ~LOW_32_BITS_MASK;

  // TODO(andreou): decide default size
  private static final int DEFAULT_SIZE = 3;

  /**
   * The hashtable. Its values are indexes to both the elements and entries arrays.
   *
   * Currently, the -1 value means "null pointer", and any non negative value x is
   * the actual index.
   *
   * Its size must be a power of two.
   */
  private transient int[] table;

  /**
   * Contains the logical entries, in the range of [0, size()). The high 32 bits of each
   * long is the smeared hash of the element, whereas the low 32 bits is the "next" pointer
   * (pointing to the next entry in the bucket chain). The pointers in [size(), entries.length)
   * are all "null" (-1).
   */
  private transient long[] entries;

  /**
   * The elements contained in the set, in the range of [0, size()).
   */
  transient Object[] elements;

  /**
   * The load factor.
   */
  private transient float loadFactor;

  /**
   * Keeps track of modifications of this set, to make it possible to throw
   * ConcurrentModificationException in the iterator. Note that we choose not to
   * make this volatile, so we do less of a "best effort" to track such errors,
   * for better performance.
   */
  transient short modCount;

  /**
   * When we have this many elements, resize the hashtable.
   */
  private transient int threshold;

  /**
   * The number of elements contained in the set.
   */
  private transient int size;

  /**
   * Constructs a new empty instance of {@code CompactHashSet}.
   */
  CompactHashSet() {
    this(DEFAULT_SIZE, DEFAULT_LOAD_FACTOR);
  }

  /**
   * Constructs a new instance of {@code CompactHashSet} containing the unique
   * elements in the specified collection.
   *
   * @param collection the collection of elements to add.
   */
  private CompactHashSet(Collection<? extends E> collection) {
    this(collection.size(), DEFAULT_LOAD_FACTOR);
    addAll(collection);
  }

  /**
   * Constructs a new instance of {@code CompactHashSet} with the specified capacity.
   *
   * @param capacity the initial capacity of this {@code CompactHashSet}.
   */
  CompactHashSet(int capacity) {
    this(capacity, DEFAULT_LOAD_FACTOR);
  }

  /**
   * Constructs a new instance of {@code CompactHashSet} with the specified capacity
   * and load factor.
   *
   * @param capacity the initial capacity.
   * @param loadFactor the initial load factor.
   */
  // TODO(andreou): replace with some static factory taking loadFactor
  private CompactHashSet(int capacity, float loadFactor) {
    init(capacity, loadFactor);
  }

  /**
   * Creates an empty {@code CompactHashSet} instance.
   */
  public static <E> CompactHashSet<E> create() {
    return new CompactHashSet<E>(DEFAULT_SIZE, DEFAULT_LOAD_FACTOR);
  }

  public static <E> CompactHashSet<E> create(Collection<? extends E> collection) {
    return new CompactHashSet<E>(collection);
  }

  /**
   * Creates a <i>mutable</i> {@code CompactHashSet} instance containing the given
   * elements in unspecified order.
   *
   * @param elements the elements that the set should contain
   * @return a new {@code CompactHashSet} containing those elements (minus duplicates)
   */
  public static <E> CompactHashSet<E> create(E... elements) {
    CompactHashSet<E> set = createWithExpectedSize(elements.length);
    Collections.addAll(set, elements);
    return set;
  }

  /**
   * Creates a {@code CompactHashSet} instance, with a high enough "initial capacity"
   * that it <i>should</i> hold {@code expectedSize} elements without growth.
   *
   * @param expectedSize the number of elements you expect to add to the returned set
   * @return a new, empty {@code CompactHashSet} with enough capacity to hold {@code
   *         expectedSize} elements without resizing
   * @throws IllegalArgumentException if {@code expectedSize} is negative
   */
  public static <E> CompactHashSet<E> createWithExpectedSize(int expectedSize) {
    return new CompactHashSet<E>(expectedSize);
  }

  /**
   * Pseudoconstructor for serialization support.
   */
  void init(int initialCapacity, float loadFactor) {
    Preconditions.checkArgument(initialCapacity >= 0, "Initial capacity must be non-negative");
    Preconditions.checkArgument(loadFactor > 0, "Illegal load factor");
    int buckets = tableSizeFor(initialCapacity);
    this.table = newTable(buckets);
    this.loadFactor = loadFactor;
    this.elements = new Object[initialCapacity];
    this.entries = newEntries(initialCapacity);
    this.threshold = Math.max(1, (int) (buckets * loadFactor));
  }

  /**
   * Returns a power of two table size for the given desired capacity.
   * See Hackers Delight, sec 3.2
   */
  private static final int tableSizeFor(int c) {
    int n = c - 1;
    n |= n >>> 1;
    n |= n >>> 2;
    n |= n >>> 4;
    n |= n >>> 8;
    n |= n >>> 16;
    return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
  }

  private static int[] newTable(int size) {
    int[] array = new int[size];
    Arrays.fill(array, -1);
    return array;
  }

  private static long[] newEntries(int size) {
    long[] array = new long[size];
    Arrays.fill(array, -1);
    return array;
  }

  private static int getHash(long entry) {
    return (int) (entry >>> 32);
  }

  /**
   * Returns the index, or -1 if the pointer is "null"
   */
  private static int getNext(long entry) {
    return (int) entry;
  }

  /**
   * Returns a new entry value by changing the "next" index of an existing entry
   */
  private static long swapNext(long entry, int newNext) {
    return (HIGH_32_BITS_MASK & entry) | (LOW_32_BITS_MASK & newNext);
  }

  @Override
  public boolean add(@Nullable E object) {
    long[] entries = this.entries;
    Object[] elements = this.elements;
    int hash = smear(object == null ? 0 : object.hashCode());
    int tableIndex = hash & (table.length - 1);
    int newEntryIndex = this.size; // current size, and pointer to the entry to be appended
    int next = table[tableIndex];
    if (next == -1) { // uninitialized bucket
      table[tableIndex] = newEntryIndex;
    } else {
      int last;
      long entry;
      do {
        last = next;
        entry = entries[next];
        if (getHash(entry) == hash) {
          Object e = elements[next];
          if (e == object || (e != null && e.equals(object))) {
            return false;
          }
        }
        next = getNext(entry);
      } while (next != -1);
      entries[last] = swapNext(entry, newEntryIndex);
    }
    if (newEntryIndex == Integer.MAX_VALUE) {
      throw new IllegalStateException("Cannot contain more than Integer.MAX_VALUE elements!");
    }
    int newSize = newEntryIndex + 1;
    resizeMeMaybe(newSize);
    insertEntry(newEntryIndex, object, hash);
    this.size = newSize;
    if (newEntryIndex >= threshold) {
      resizeTable(2 * table.length);
    }
    modCount++;
    return true;
  }

  /**
   * Creates a fresh entry with the specified object at the specified position in the entry
   * arrays.
   */
  void insertEntry(int entryIndex, E object, int hash) {
    this.entries[entryIndex] = ((long) hash << 32) | LOW_32_BITS_MASK; // low bits: -1
    this.elements[entryIndex] = object;
  }

  /**
   * Returns currentSize + 1, after resizing the entries storage is necessary.
   */
  private void resizeMeMaybe(int newSize) {
    int entriesSize = entries.length;
    if (newSize > entriesSize) {
      int newCapacity = entriesSize + Math.max(1, entriesSize >>> 1);
      if (newCapacity < 0) {
        newCapacity = Integer.MAX_VALUE;
      }
      if (newCapacity != entriesSize) {
        resizeEntries(newCapacity);
      }
    }
  }

  /**
   * Resizes the internal entries array to the specified capacity, which may be greater or less
   * than the current capacity.
   */
  void resizeEntries(int newCapacity) {
    // andreou: this apparently is gwt-INcompatible.
    // TODO(lowasser): ObjectArrays is compatible, and for entries we can fall-back to
    // System.arraycopy, which is compatible as well.
    this.elements = Arrays.copyOf(elements, newCapacity);
    long[] entries = this.entries;
    int oldSize = entries.length;
    entries = Arrays.copyOf(entries, newCapacity);
    if (newCapacity > oldSize) {
      Arrays.fill(entries, oldSize, newCapacity, -1);
    }
    this.entries = entries;

  }

  private void resizeTable(int newCapacity) { // newCapacity always a power of two
    int[] oldTable = table;
    int oldCapacity = oldTable.length;
    if (oldCapacity >= MAXIMUM_CAPACITY) {
      threshold = Integer.MAX_VALUE;
      return;
    }
    int newThreshold = 1 + (int) (newCapacity * loadFactor);
    int[] newTable = newTable(newCapacity);
    long[] entries = this.entries;

    int mask = newTable.length - 1;
    for (int i = 0; i < size; i++) {
      long oldEntry = entries[i];
      int hash = getHash(oldEntry);
      int tableIndex = hash & mask;
      int next = newTable[tableIndex];
      newTable[tableIndex] = i;
      entries[i] = ((long) hash << 32) | (LOW_32_BITS_MASK & next);
    }

    this.threshold = newThreshold;
    this.table = newTable;
  }

  @Override
  public boolean contains(@Nullable Object object) {
    int hash = smear(object == null ? 0 : object.hashCode());
    int next = table[hash & (table.length - 1)];
    while (next != -1) {
      long entry = entries[next];
      if (getHash(entry) == hash) {
        Object e = elements[next];
        if (e == object || (e != null && e.equals(object))) {
          return true;
        }
      }
      next = getNext(entry);
    }
    return false;
  }

  @Override
  public boolean remove(@Nullable Object object) {
    return remove(object, object == null ? 0 : smear(object.hashCode()));
  }

  private boolean remove(Object o, int hash) {
    int tableIndex = hash & (table.length - 1);
    int next = table[tableIndex];
    if (next == -1) { // empty bucket
      return false;
    }
    int last = -1;
    do {
      if (getHash(entries[next]) == hash) {
        Object e = elements[next];
        if (e == o || (e != null && e.equals(o))) {
          if (last == -1) {
            // we need to update the root link from table[]
            table[tableIndex] = getNext(entries[next]);
          } else {
            // we need to update the link from the chain
            entries[last] = swapNext(entries[last], getNext(entries[next]));
          }

          moveEntry(next);
          size--;
          modCount++;
          return true;
        }
      }
      last = next;
      next = getNext(entries[next]);
    } while (next != -1);
    return false;
  }

  /**
   * Moves the last entry in the entry array into {@code dstIndex}, and nulls out its old position.
   */
  void moveEntry(int dstIndex) {
    int srcIndex = size() - 1;
    if (dstIndex < srcIndex) {
      // move last entry to deleted spot
      elements[dstIndex] = elements[srcIndex];
      elements[srcIndex] = null;

      // move the last entry to the removed spot, just like we moved the element
      long lastEntry = entries[srcIndex];
      entries[dstIndex] = lastEntry;
      entries[srcIndex] = -1;

      // also need to update whoever's "next" pointer was pointing to the last entry place
      // reusing "tableIndex" and "next"; these variables were no longer needed
      int tableIndex = getHash(lastEntry) & (table.length - 1);
      int lastNext = table[tableIndex];
      if (lastNext == srcIndex) {
        // we need to update the root pointer
        table[tableIndex] = dstIndex;
      } else {
        // we need to update a pointer in an entry
        int previous;
        long entry;
        do {
          previous = lastNext;
          lastNext = getNext(entry = entries[lastNext]);
        } while (lastNext != srcIndex);
        // here, entries[previous] points to the old entry location; update it
        entries[previous] = swapNext(entry, dstIndex);
      }
    } else {
      elements[dstIndex] = null;
      entries[dstIndex] = -1;
    }
  }

  @Override
  public Iterator<E> iterator() {
    return new Iterator<E>() {
      short expectedModCount = modCount;
      boolean nextCalled = false;
      int index = 0;

      @Override
      public boolean hasNext() {
        return index < size;
      }

      @Override
      @SuppressWarnings("unchecked")
      public E next() {
        checkForConcurrentModification();
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        nextCalled = true;
        return (E) elements[index++];
      }

      @Override
      public void remove() {
        checkForConcurrentModification();
        if (!nextCalled) {
          throw new IllegalStateException();
        }
        expectedModCount++;
        index--;
        CompactHashSet.this.remove(elements[index], getHash(entries[index]));
        nextCalled = false;
      }

      private void checkForConcurrentModification() {
        if (modCount != expectedModCount) {
          throw new ConcurrentModificationException();
        }
      }
    };
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public boolean isEmpty() {
    return size == 0;
  }

  @Override
  public Object[] toArray() {
    return Arrays.copyOf(elements, size);
  }

  @Override
  @SuppressWarnings("unchecked") // safe, because JVM checks the writes to the array
  public <T> T[] toArray(T[] a) {
    int size = this.size;
    Object[] elements = this.elements;
    T[] array = (a.length >= size) ?
        a : (T[]) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), size);
    System.arraycopy(elements, 0, array, 0, size);
    if (array.length > size) {
      array[size] = null;
    }
    return array;
  }

  /**
   * Ensures that this {@code CompactHashSet} has the smallest representation in memory,
   * given its current size.
   */
  public void trimToSize() {
    int size = this.size;
    if (size < entries.length) {
      resizeEntries(size);
    }
    // size / loadFactor gives the table size of the appropriate load factor,
    // but that may not be a power of two. We floor it to a power of two by
    // keeping its highest bit. But the smaller table may have a load factor
    // larger than what we want; then we want to go to the next power of 2 if we can
    int minimumTableSize = Math.max(1, Integer.highestOneBit((int) (size / loadFactor)));
    if (minimumTableSize < MAXIMUM_CAPACITY) {
      double load = (double) size / minimumTableSize;
      if (load > loadFactor) {
        minimumTableSize <<= 1; // increase to next power if possible
      }
    }

    if (minimumTableSize < table.length) {
      resizeTable(minimumTableSize);
    }
  }

  @Override
  public void clear() {
    modCount++;
    Arrays.fill(elements, 0, size, null);
    Arrays.fill(table, -1);
    Arrays.fill(entries, -1);
    this.size = 0;
  }

  /**
   * The serial form currently mimicks Android's java.util.HashSet version, e.g. see
   * http://omapzoom.org/?p=platform/libcore.git;a=blob;f=luni/src/main/java/java/util/HashSet.java
   */
  private void writeObject(ObjectOutputStream stream) throws IOException {
    stream.defaultWriteObject();
    stream.writeInt(table.length);
    stream.writeFloat(loadFactor);
    stream.writeInt(size);
    for (E e : this) {
      stream.writeObject(e);
    }
  }

  @SuppressWarnings("unchecked")
  private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
    stream.defaultReadObject();
    int length = stream.readInt();
    float loadFactor = stream.readFloat();
    int elementCount = stream.readInt();
    try {
      init(length, loadFactor);
    } catch (IllegalArgumentException e) {
      throw new InvalidObjectException(e.getMessage());
    }
    for (int i = elementCount; --i >= 0;) {
      E element = (E) stream.readObject();
      add(element);
    }
  }
}