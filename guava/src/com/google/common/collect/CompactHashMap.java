/*
 * Copyright (C) 2012 The Guava Authors
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

import static com.google.common.collect.Hashing.smear;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * CompactHashMap is an implementation of a Map. All optional operations (put and
 * remove) are supported.  Null keys and values are specifically supported.
 *
 * <p>{@code get(k)}, {@code containsKey(k)}, {@code put(k, v)}, and {@code remove(k)}, are all
 * (expected and amortized) constant time operations. Expected in the hashtable sense (depends on
 * the hash function doing a good job of distributing the elements to the buckets to a distribution
 * not far from uniform), and amortized since some operations can trigger a hash table resize.
 *
 * <p>Unlike {@code java.util.HashMap}, iteration is only proportional to the actual
 * {@code size()}, which is optimal, and <i>not</i> the size of the internal hashtable,
 * which could be much larger than {@code size()}.  {@code CompactHashMap} is specifically
 * optimized to reduce absolute memory consumption and load on the garbage collector with minimal
 * impact on performance. 
 *
 * <p>If there are no removals, then {@link #iterator iteration} order is the same as insertion
 * order. Any removal invalidates any ordering guarantees.
 *
 * @author Louis Wasserman
 * @author Dimitris Andreou
 */
@GwtIncompatible("java.util.Arrays#copyOf(Object[], int), java.lang.reflect.Array")
public class CompactHashMap<K, V> extends AbstractMap<K, V> implements Serializable {
  // TODO(andreou): cache all field accesses in local vars

  /**
   * Creates an empty {@code CompactHashMap} instance.
   */
  public static <K, V> CompactHashMap<K, V> create() {
    return new CompactHashMap<K, V>();
  }

  /**
   * Creates a <i>mutable</i> {@code CompactHashMap} instance containing the entries
   * of the given map in unspecified order.
   *
   * @param map the entries that the map should contain
   * @return a new {@code CompactHashMap} containing those entries
   */
  public static <K, V> CompactHashMap<K, V> create(Map<? extends K, V> map) {
    CompactHashMap<K, V> result = createWithExpectedSize(map.size());
    result.putAll(map);
    return result;
  }

  /**
   * Creates a {@code CompactHashMap} instance, with a high enough "initial capacity"
   * that it <i>should</i> hold {@code expectedSize} entries without growth.
   *
   * @param expectedSize the number of elements you expect to add to the returned set
   * @return a new, empty {@code CompactHashMap} with enough capacity to hold {@code
   *         expectedSize} elements without resizing
   * @throws IllegalArgumentException if {@code expectedSize} is negative
   */
  public static <K, V> CompactHashMap<K, V> createWithExpectedSize(int expectedSize) {
    return new CompactHashMap<K, V>(expectedSize);
  }

  private static final int MAXIMUM_CAPACITY = 1 << 30;

  // TODO(andreou): decide, and inline, load factor. 0.75?
  private static final float DEFAULT_LOAD_FACTOR = 1.0f;

  /**
   * Bitmask that selects the low 32 bits.
   */
  private static final long NEXT_MASK  = (1L << 32) - 1;

  /**
   * Bitmask that selects the high 32 bits.
   */
  private static final long HASH_MASK = ~NEXT_MASK;

  // TODO(andreou): decide default size
  private static final int DEFAULT_SIZE = 3;
  
  private static final int MAX_SIZE = Integer.MAX_VALUE / 2;

  /**
   * The hashtable. Its values are indexes to both the keysAndValues and entries arrays.
   *
   * Currently, the -1 value means "null pointer", and any non negative value x is
   * the actual index.
   *
   * Its size must be a power of two.
   */
  private transient int[] table;

  /**
   * Contains the logical entries, in the range of [0, size()). The high 32 bits of each
   * long is the smeared hash of the key, whereas the low 32 bits is the "next" pointer
   * (pointing to the next entry in the bucket chain). The pointers in [size(), entries.length)
   * are all "null" (-1).
   */
  private transient long[] entries;

  /**
   * The keys and values in the map.  Conceptually, the key for entry i is at position i * 2,
   * and the value is at position i * 2 + 1.
   */
  transient Object[] keysAndValues;

  /**
   * The load factor.
   */
  transient float loadFactor;

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
  
  @SuppressWarnings("unchecked")
  final K getKey(int entryIndex) {
    return (K) keysAndValues[2 * entryIndex];
  }
  
  @SuppressWarnings("unchecked")
  final V getValue(int entryIndex) {
    return (V) keysAndValues[2 * entryIndex + 1];
  }
  
  private void setKey(int entryIndex, @Nullable K key) {
    keysAndValues[2 * entryIndex] = key;
  }
  
  private void setValue(int entryIndex, @Nullable V value) {
    keysAndValues[2 * entryIndex + 1] = value;
  }

  /**
   * Constructs a new empty instance of {@code CompactHashMap}.
   */
  CompactHashMap() {
    init(DEFAULT_SIZE, DEFAULT_LOAD_FACTOR);
  }

  /**
   * Constructs a new instance of {@code CompactHashMap} with the specified capacity.
   *
   * @param capacity the initial capacity of this {@code CompactHashMap}.
   */
  CompactHashMap(int capacity) {
    init(capacity, DEFAULT_LOAD_FACTOR);
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
    this.keysAndValues = new Object[initialCapacity * 2];
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
    return (HASH_MASK & entry) | (NEXT_MASK & newNext);
  }

  @Override
  public V put(@Nullable K key, @Nullable V value) {
    long[] entries = this.entries;
    int hash = smear(key == null ? 0 : key.hashCode());
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
          Object k = getKey(next);
          if (key == k || (key != null && key.equals(k))) {
            V oldValue = getValue(next);
            setValue(next, value);
            return oldValue;
          }
        }
        next = getNext(entry);
      } while (next != -1);
      entries[last] = swapNext(entry, newEntryIndex);
    }
    if (newEntryIndex == MAX_SIZE) {
      throw new IllegalStateException("Cannot contain more than Integer.MAX_VALUE elements!");
    }
    int newSize = newEntryIndex + 1;
    resizeMeMaybe(newSize);
    insertEntry(newEntryIndex, key, value, hash);
    this.size = newSize;
    if (newEntryIndex >= threshold) {
      resizeTable(2 * table.length);
    }
    modCount++;
    return null;
  }

  /**
   * Creates a fresh entry with the specified object at the specified position in the entry
   * arrays.
   */
  void insertEntry(int entryIndex, @Nullable K key, @Nullable V value, int hash) {
    this.entries[entryIndex] = ((long) hash << 32) | NEXT_MASK; // low bits: -1
    setKey(entryIndex, key);
    setValue(entryIndex, value);
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
    this.keysAndValues = Arrays.copyOf(keysAndValues, newCapacity * 2);
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
      if (next != -1) {
        // we already had a chain at that index; chain it back
        entries[i] = ((long) hash << 32) | (NEXT_MASK & next);
      } else {
        entries[i] = ((long) hash << 32) | NEXT_MASK; // low bits: -1
      }
    }

    this.threshold = newThreshold;
    this.table = newTable;
  }
  
  private int findEntry(@Nullable Object key, int hash) {
    int next = table[hash & (table.length - 1)];
    while (next != -1) {
      long entry = entries[next];
      if (getHash(entry) == hash) {
        Object k = getKey(next);
        if (key == k || (key != null && key.equals(k))) {
          return next;
        }
      }
      next = getNext(entry);
    }
    return -1;
  }

  @Override
  public boolean containsKey(@Nullable Object key) {
    int hash = smear(key == null ? 0 : key.hashCode());
    return findEntry(key, hash) != -1;
  }
  
  private static final Object NOT_FOUND = new Object();

  @SuppressWarnings("unchecked")
  @Override
  public V remove(@Nullable Object object) {
    Object result = remove(object, object == null ? 0 : smear(object.hashCode()));
    if (result == NOT_FOUND) {
      return null;
    } else {
      return (V) result;
    }
  }

  /**
   * Returns NOT_FOUND if the key was absent, so as to help keySet distinguish between "absent"
   * and "associated with null" in remove operations.
   */
  private Object remove(@Nullable Object key, int hash) {
    int tableIndex = hash & (table.length - 1);
    int next = table[tableIndex];
    if (next == -1) { // empty bucket
      return NOT_FOUND;
    }
    int last = -1;
    do {
      if (getHash(entries[next]) == hash) {
        Object k = getKey(next);
        if (key == k || (key != null && key.equals(k))) {
          V oldValue = getValue(next);
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
          return oldValue;
        }
      }
      last = next;
      next = getNext(entries[next]);
    } while (next != -1);
    return NOT_FOUND;
  }

  /**
   * Moves the last entry in the entry array into {@code dstIndex}, and nulls out its old position.
   */
  void moveEntry(int dstIndex) {
    int srcIndex = size() - 1;
    if (dstIndex < srcIndex) {
      // move last entry to deleted spot
      setKey(dstIndex, getKey(srcIndex));
      setValue(dstIndex, getValue(srcIndex));
      setKey(srcIndex, null);
      setValue(srcIndex, null);

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
      setKey(dstIndex, null);
      setValue(dstIndex, null);
      entries[dstIndex] = -1;
    }
  }
  
  private abstract class Itr<T> implements Iterator<T> {
    short expectedModCount = modCount;
    boolean nextCalled = false;
    int index = 0;
    
    private void checkForConcurrentModification() {
      if (modCount != expectedModCount) {
        throw new ConcurrentModificationException();
      }
    }
    
    abstract T outputForEntry(int entryIndex);

    @Override
    public boolean hasNext() {
      return index < size();
    }

    @Override
    public T next() {
      checkForConcurrentModification();
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      nextCalled = true;
      return outputForEntry(index++);
    }

    @Override
    public void remove() {
      checkForConcurrentModification();
      if (!nextCalled) {
        throw new IllegalStateException();
      }
      expectedModCount++;
      index--;
      CompactHashMap.this.remove(getKey(index), getHash(entries[index]));
      nextCalled = false;
    }
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public boolean isEmpty() {
    return size == 0;
  }

  /**
   * Ensures that this {@code CompactHashMap} has the smallest representation in memory,
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
  
  private transient Set<K> keySet;

  @Override
  public Set<K> keySet() {
    Set<K> result = keySet;
    return (result == null) ? keySet = new KeySet() : result;
  }
  
  private final class KeySet extends AbstractSet<K> {
    @Override
    public Iterator<K> iterator() {
      return keyIterator();
    }

    @Override
    public int size() {
      return size;
    }

    @Override
    public boolean contains(@Nullable Object o) {
      return containsKey(o);
    }

    @Override
    public boolean remove(@Nullable Object o) {
      int hash = Hashing.smear((o == null) ? 0 : o.hashCode());
      return CompactHashMap.this.remove(o, hash) != NOT_FOUND;
    }

    @Override
    public void clear() {
      CompactHashMap.this.clear();
    }
  }
  
  Iterator<K> keyIterator() {
    return new Itr<K>() {
      @Override
      K outputForEntry(int entryIndex) {
        return getKey(entryIndex);
      }
    };
  }
  
  private transient Collection<V> values;

  @Override
  public Collection<V> values() {
    Collection<V> result = values;
    return (result == null) ? values = new Values() : result;
  }
  
  private final class Values extends AbstractCollection<V> {
    @Override
    public Iterator<V> iterator() {
      return valuesIterator();
    }

    @Override
    public int size() {
      return size;
    }

    @Override
    public void clear() {
      CompactHashMap.this.clear();
    }
  }
  
  Iterator<V> valuesIterator() {
    return new Itr<V>() {
      @Override
      V outputForEntry(int entryIndex) {
        return getValue(entryIndex);
      }
    };
  }
  
  private transient Set<Entry<K, V>> entrySet;

  @Override
  public Set<Entry<K, V>> entrySet() {
    Set<Entry<K, V>> result = entrySet;
    return (result == null) ? entrySet = new EntrySet() : result;
  }
  
  private final class EntrySet extends AbstractSet<Entry<K, V>> {
    @Override
    public Iterator<Entry<K, V>> iterator() {
      return entryIterator();
    }

    @Override
    public int size() {
      return size;
    }

    @Override
    public boolean contains(@Nullable Object o) {
      if (o instanceof Entry) {
        Entry<?, ?> entry = (Entry<?, ?>) o;
        Object key = entry.getKey();
        Object value = entry.getValue();
        int keyHash = smear((key == null) ? 0 : key.hashCode());
        int entryIndex = findEntry(key, keyHash);
        return entryIndex != -1 && Objects.equal(value, getValue(entryIndex));
      }
      return false;
    }

    @Override
    public boolean remove(@Nullable Object o) {
      if (contains(o)) {
        Entry<?, ?> entry = (Entry<?, ?>) o;
        CompactHashMap.this.remove(entry.getKey());
        return true;
      }
      return false;
    }

    @Override
    public void clear() {
      CompactHashMap.this.clear();
    }
  }
  
  Iterator<Entry<K, V>> entryIterator() {
    return new Itr<Entry<K, V>>() {
      @Override
      Entry<K, V> outputForEntry(int entryIndex) {
        return getEntry(entryIndex);
      }
    };
  }
  
  final Entry<K, V> getEntry(int entryIndex) {
    return new EntryView(entryIndex);
  }
  
  /**
   * A {@code Map.Entry} implementation that updates correctly in response to further map
   * modifications.
   */
  private final class EntryView extends AbstractMapEntry<K, V> {
    private final K key;
    private final int keyHash;
    private int entryIndex;
    
    private EntryView(int entryIndex) {
      this.entryIndex = entryIndex;
      this.key = CompactHashMap.this.getKey(entryIndex);
      this.keyHash = getHash(entries[entryIndex]);
    }

    @Override
    public K getKey() {
      return key;
    }
    
    private void updateIndex() {
      if (entryIndex < 0 || entryIndex >= size ||
          !Objects.equal(key, CompactHashMap.this.getKey(entryIndex))) {
        entryIndex = findEntry(key, keyHash);
      }
    }

    @Override
    public V getValue() {
      updateIndex();
      return (entryIndex == -1) ? null : CompactHashMap.this.getValue(entryIndex);
    }
    
  }

  @Override
  public boolean containsValue(@Nullable Object value) {
    return values().contains(value);
  }

  @Override
  public void clear() {
    modCount++;
    Arrays.fill(keysAndValues, 0, size * 2, null);
    Arrays.fill(table, -1);
    Arrays.fill(entries, -1);
    this.size = 0;
  }

  /**
   * The serial form currently mimicks Android's java.util.HashMap version, e.g. see
   * http://omapzoom.org/?p=platform/libcore.git;a=blob;f=luni/src/main/java/java/util/HashMap.java
   */
  private void writeObject(ObjectOutputStream stream) throws IOException {
    stream.defaultWriteObject();
    stream.writeInt(table.length);
    stream.writeFloat(loadFactor);
    stream.writeInt(size);
    for (int i = 0; i < size; i++) {
      stream.writeObject(getKey(i));
      stream.writeObject(getValue(i));
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
      K key = (K) stream.readObject();
      V value = (V) stream.readObject();
      put(key, value);
    }
  }
}