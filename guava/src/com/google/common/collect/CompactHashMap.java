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
import java.util.NoSuchElementException;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * {@code CompactHashMap} is an implementation of a {@code Map}, supporting all optional
 * operations.  Null keys and values are permitted.
 *
 * <p>{@code get(k)}, {@code containsKey(k)} and {@code remove(k)}, are all (expected and
 * amortized) constant time operations. Expected in the hashtable sense (depends on the hash
 * function doing a good job of distributing the elements to the buckets to a distribution not far
 * from uniform), and amortized since some operations can trigger a hash table resize.
 *
 * <p>Unlike {@code java.util.HashMap}, iteration takes time proportional to the actual
 * {@code size()}, which is optimal, and <i>not</i> the size of the internal hashtable,
 * which could be much larger than {@code size()}. Furthermore, this implementation consumes
 * significantly less memory, and reduces the work involved in garbage collection.
 *
 * <p>If there are no removals, then the iteration order of {@link #keySet}, {@link #entryValues},
 * and {@link #entrySet} is the same as insertion order.  Any removal invalidates any ordering
 * guarantees.
 *
 * @author Louis Wasserman
 * @author Dimitris Andreou
 */
@GwtIncompatible("java.util.Arrays#copyOf(Object[], int), java.lang.reflect.Array")
public class CompactHashMap<K, V> extends AbstractMap<K, V> implements Serializable {
  // TODO(andreou): constructors and static factories? What to keep, what to drop?
  // TODO(andreou): cache all field accesses in local vars

  private static final int MAXIMUM_CAPACITY = 1 << 30;

  private static final float DEFAULT_LOAD_FACTOR = 1.0f;

  private static final long LOW_32_BITS_MASK  = (1L << 32) - 1;
  private static final long HIGH_32_BITS_MASK = ~LOW_32_BITS_MASK;

  // TODO(andreou): decide default size
  private static final int DEFAULT_SIZE = 3;

  /**
   * The hashtable. Its values are indexes to both the entryKeys, entryValues, and metadata arrays.
   *
   * Currently, the -1 value means "null pointer", and any non negative value x is
   * the actual index.
   *
   * Its size must be a power of two.
   */
  private transient int[] table;

  /**
   * Contains the hash table metadata for each entry, stored in the first [0, size()) positions.
   * The high 32 bits of each long is the smeared hash of the element, whereas the low 32 bits is
   * the "next" pointer (pointing to the next entry in the bucket chain). The pointers in
   * [size(), metadata.length) are all "null" (-1).
   */
  private transient long[] metadata;

  /**
   * The keys of the map, "lined up" with the values and the entry metadata.
   */
  private transient Object[] entryKeys;
  
  /**
   * The values of the map, "lined up" with the keys and the entry metadata.
   */
  private transient Object[] entryValues;

  /**
   * The load factor.
   */
  private transient float loadFactor;

  /**
   * Keeps track of modifications of this map, to make it possible to throw
   * ConcurrentModificationException in the iterator. Note that we choose not to
   * make this volatile, so we do less of a "best effort" to track such errors,
   * for better performance.
   */
  transient short modCount;

  /**
   * When we have this many entries, resize the hashtable.
   */
  private transient int threshold;

  /**
   * The number of entries contained in the map.
   */
  private transient int size;

  /**
   * Constructs a new empty instance of {@code CompactHashSet}.
   */
  CompactHashMap() {
    this(DEFAULT_SIZE, DEFAULT_LOAD_FACTOR);
  }

  /**
   * Constructs a new instance of {@code CompactHashSet} with the specified capacity.
   *
   * @param capacity the initial capacity of this {@code CompactHashSet}.
   */
  CompactHashMap(int capacity) {
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
  private CompactHashMap(int capacity, float loadFactor) {
    init(capacity, loadFactor);
  }

  /**
   * Creates an empty {@code CompactHashMap} instance.
   */
  public static <K, V> CompactHashMap<K, V> create() {
    return new CompactHashMap<K, V>(DEFAULT_SIZE, DEFAULT_LOAD_FACTOR);
  }

  /**
   * Creates a {@code CompactHashMap} instance, with a high enough "initial capacity"
   * that it <i>should</i> hold {@code expectedSize} entries without growth.
   *
   * @param expectedSize the number of entries you expect to add to the returned map
   * @return a new, empty {@code CompactHashMap} with enough capacity to hold {@code
   *         expectedSize} elements without resizing
   * @throws IllegalArgumentException if {@code expectedSize} is negative
   */
  public static <K, V> CompactHashMap<K, V> createWithExpectedSize(int expectedSize) {
    return new CompactHashMap<K, V>(expectedSize);
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
    this.metadata = newMetadata(initialCapacity);
    this.entryKeys = new Object[initialCapacity];
    this.entryValues = new Object[initialCapacity];
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

  private static long[] newMetadata(int size) {
    long[] array = new long[size];
    Arrays.fill(array, -1);
    return array;
  }

  private static int getHash(long metadata) {
    return (int) (metadata >>> 32);
  }

  /**
   * Extracts the "next in bucket" pointer from the entry metadata, or -1 if the pointer is "null."
   */
  private static int getNext(long metadata) {
    return (int) metadata;
  }

  /**
   * Returns a new entry value by changing the "next" index of an existing entry
   */
  private static long swapNext(long entry, int newNext) {
    return (HIGH_32_BITS_MASK & entry) | (LOW_32_BITS_MASK & newNext);
  }
  
  @SuppressWarnings("unchecked")
  K getKeyForEntry(int entryIndex) {
    return (K) entryKeys[entryIndex];
  }
  
  @SuppressWarnings("unchecked")
  V getValueForEntry(int entryIndex) {
    return (V) entryValues[entryIndex];
  }

  @Override
  public V put(@Nullable K key, @Nullable V value) {
    long[] entries = this.metadata;
    Object[] keys = this.entryKeys;
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
          Object k = keys[next];
          if (k == key || (key != null && key.equals(k))) {
            V oldValue = getValueForEntry(next);
            entryValues[next] = value;
            return oldValue;
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
    this.metadata[entryIndex] = ((long) hash << 32) | LOW_32_BITS_MASK; // low bits: -1
    this.entryKeys[entryIndex] = key;
    this.entryValues[entryIndex] = value;
  }

  /**
   * Returns currentSize + 1, after resizing the entries storage is necessary.
   */
  private void resizeMeMaybe(int newSize) {
    int entriesSize = metadata.length;
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
    this.entryKeys = Arrays.copyOf(entryKeys, newCapacity);
    this.entryValues = Arrays.copyOf(entryValues, newCapacity);
    long[] metadata = this.metadata;
    int oldSize = metadata.length;
    metadata = Arrays.copyOf(metadata, newCapacity);
    if (newCapacity > oldSize) {
      Arrays.fill(metadata, oldSize, newCapacity, -1);
    }
    this.metadata = metadata;
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
    long[] metadata = this.metadata;

    int mask = newTable.length - 1;
    for (int i = 0; i < size; i++) {
      long oldMetadata = metadata[i];
      int hash = getHash(oldMetadata);
      int tableIndex = hash & mask;
      int next = newTable[tableIndex];
      newTable[tableIndex] = i;
      metadata[i] = ((long) hash << 32) | (LOW_32_BITS_MASK & next);
    }

    this.threshold = newThreshold;
    this.table = newTable;
  }
  
  private int getEntryForKey(@Nullable Object key) {
    int hash = smear(key == null ? 0 : key.hashCode());
    return getEntryForKey(key, hash);
  }
  
  private int getEntryForKey(@Nullable Object key, int hash) {
    int next = table[hash & (table.length - 1)];
    while (next != -1) {
      long entryMetadata = metadata[next];
      if (getHash(entryMetadata) == hash) {
        Object k = entryKeys[next];
        if (k == key || (key != null && key.equals(k))) {
          return next;
        }
      }
      next = getNext(entryMetadata);
    }
    return -1;
  }

  @Override
  public boolean containsKey(@Nullable Object key) {
    return getEntryForKey(key) != -1;
  }
  
  @Override
  public V get(@Nullable Object key) {
    int index = getEntryForKey(key);
    return (index == -1) ? null : getValueForEntry(index);
  }

  @SuppressWarnings("unchecked")
  @Nullable
  @Override
  public V remove(@Nullable Object key) {
    Object result = remove(key, key == null ? 0 : smear(key.hashCode()));
    if (result == NOT_FOUND) {
      return null;
    } else {
      return (V) result;
    }
  }
  
  /**
   * A hack so keySet().remove(Object) can tell the difference between a removed entry with a null
   * value, and a failure to remove an entry.
   */
  private static final Object NOT_FOUND = new Object();

  private Object remove(@Nullable Object key, int hash) {
    int tableIndex = hash & (table.length - 1);
    int next = table[tableIndex];
    if (next == -1) { // empty bucket
      return NOT_FOUND;
    }
    int last = -1;
    do {
      if (getHash(metadata[next]) == hash) {
        Object k = entryKeys[next];
        if (k == key || (k != null && k.equals(key))) {
          V oldValue = getValueForEntry(next);
          
          if (last == -1) {
            // we need to update the root link from table[]
            table[tableIndex] = getNext(metadata[next]);
          } else {
            // we need to update the link from the chain
            metadata[last] = swapNext(metadata[last], getNext(metadata[next]));
          }

          moveEntry(next);
          size--;
          modCount++;
          return oldValue;
        }
      }
      last = next;
      next = getNext(metadata[next]);
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
      entryKeys[dstIndex] = entryKeys[srcIndex];
      entryValues[dstIndex] = entryValues[srcIndex];
      entryKeys[srcIndex] = null;
      entryValues[srcIndex] = null;

      // move the last entry to the removed spot, just like we moved the element
      long lastEntry = metadata[srcIndex];
      metadata[dstIndex] = lastEntry;
      metadata[srcIndex] = -1;

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
          lastNext = getNext(entry = metadata[lastNext]);
        } while (lastNext != srcIndex);
        // here, entries[previous] points to the old entry location; update it
        metadata[previous] = swapNext(entry, dstIndex);
      }
    } else {
      entryKeys[dstIndex] = null;
      entryValues[dstIndex] = null;
      metadata[dstIndex] = -1;
    }
  }
  
  private abstract class Itr<T> implements Iterator<T> {
    int next = 0;
    boolean nextCalled = false;
    short expectedModCount = modCount;
    
    private void checkForConcurrentModification() {
      if (modCount != expectedModCount) {
        throw new ConcurrentModificationException();
      }
    }

    @Override
    public boolean hasNext() {
      checkForConcurrentModification();
      return next < size();
    }
    
    abstract T resultForEntry(int entryIndex);

    @Override
    public T next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      nextCalled = true;
      return resultForEntry(next++);
    }

    @Override
    public void remove() {
      Iterators.checkRemove(nextCalled);
      next--;
      CompactHashMap.this.remove(getKeyForEntry(next));
      nextCalled = false;
      expectedModCount = modCount;
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
  
  private transient Set<K> keySet;

  @Override
  public Set<K> keySet() {
    Set<K> result = keySet;
    return (result == null) ? keySet = new KeySet() : result;
  }
  
  private class KeySet extends AbstractSet<K> {
    @Override
    public boolean contains(@Nullable Object o) {
      return containsKey(o);
    }

    @Override
    public boolean remove(@Nullable Object o) {
      int hash = smear((o == null) ? 0 : o.hashCode()); 
      return CompactHashMap.this.remove(o, hash) != NOT_FOUND;
    }

    @Override
    public void clear() {
      CompactHashMap.this.clear();
    }

    @Override
    public Iterator<K> iterator() {
      return keyIterator();
    }

    @Override
    public int size() {
      return size;
    }
  }
  
  Iterator<K> keyIterator() {
    return new Itr<K>(){
      @Override
      K resultForEntry(int entryIndex) {
        return getKeyForEntry(entryIndex);
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
      return valueIterator();
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
  
  Iterator<V> valueIterator() {
    return new Itr<V>() {
      @Override
      V resultForEntry(int entryIndex) {
        return getValueForEntry(entryIndex);
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
        int entryIndex = getEntryForKey(key);
        return entryIndex != -1 && Objects.equal(value, getValueForEntry(entryIndex));
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
      Entry<K, V> resultForEntry(int entryIndex) {
        return getMapEntry(entryIndex);
      }
    };
  }
  
  Entry<K, V> getMapEntry(int entryIndex) {
    return new MapEntry(entryIndex);
  }
  
  /**
   * A map entry implementation that transparently behaves properly in the face of map
   * modifications.
   */
  private final class MapEntry extends AbstractMapEntry<K, V> {
    private int entryIndex; // May be -1, if this entry has been removed from the map
    private final K key;
    private final int keyHash;
    private short expectedModCount;
    
    MapEntry(int entryIndex) {
      this.entryIndex = entryIndex;
      this.key = getKeyForEntry(entryIndex);
      this.keyHash = getHash(metadata[entryIndex]);
      this.expectedModCount = modCount;
    }
    
    /**
     * If modifications to the map have occurred since this entry was created, update the index
     * for the entry associated with this key.
     * 
     * <p>Usually, we can depend on the modCount telling us, but we can't depend on it for
     * correctness, so we check that the key is still actually there.
     */
    private void updateIndex() {
      if (modCount != expectedModCount 
          || entryIndex >= size() 
          || !Objects.equal(key, getKeyForEntry(entryIndex))) {
        entryIndex = getEntryForKey(key, keyHash);
        expectedModCount = modCount;
      }
    }

    @Override
    public K getKey() {
      return key;
    }

    @Override
    public V getValue() {
      updateIndex();
      return (entryIndex == -1) ? null : getValueForEntry(entryIndex);
    }

    @Override
    public V setValue(@Nullable V value) {
      updateIndex();
      if (entryIndex == -1) {
        put(key, value);
        entryIndex = size() - 1;
        expectedModCount = modCount;
        return null;
      } else {
        V oldValue = getValueForEntry(entryIndex);
        entryValues[entryIndex] = value;
        return oldValue;
      }
    }
  }

  /**
   * Ensures that this {@code CompactHashMap} has the smallest representation in memory,
   * given its current size.
   */
  public void trimToSize() {
    int size = this.size;
    if (size < metadata.length) {
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
    Arrays.fill(entryKeys, 0, size, null);
    Arrays.fill(entryValues, 0, size, null);
    Arrays.fill(table, -1);
    Arrays.fill(metadata, -1);
    this.size = 0;
  }

  private void writeObject(ObjectOutputStream stream) throws IOException {
    stream.defaultWriteObject();
    stream.writeInt(size);
    stream.writeFloat(loadFactor);
    for (Entry<K, V> e : entrySet()) {
      stream.writeObject(e.getKey());
      stream.writeObject(e.getValue());
    }
  }

  @SuppressWarnings("unchecked")
  private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
    stream.defaultReadObject();
    int size = stream.readInt();
    float loadFactor = stream.readFloat();
    try {
      init(size, loadFactor);
    } catch (IllegalArgumentException e) {
      throw new InvalidObjectException(e.getMessage());
    }
    for (int i = 0; i < size; i++) {
      K key = (K) stream.readObject();
      V value = (V) stream.readObject();
      put(key, value);
    }
  }
}