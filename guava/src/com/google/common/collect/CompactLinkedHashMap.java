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

import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

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
 * significantly less memory, and is considerably friendlier to garbage collectors.
 *
 * <p>If there are no removals, then the iteration order of {@link #keySet}, {@link #entryValues},
 * and {@link #entrySet} is the same as insertion order.  Any removal invalidates any ordering
 * guarantees.
 *
 * @author Louis Wasserman
 */
public final class CompactLinkedHashMap<K, V> extends CompactHashMap<K, V> {
  /**
   * Creates an empty {@code CompactLinkedHashMap} instance.
   */
  public static <K, V> CompactLinkedHashMap<K, V> create() {
    return new CompactLinkedHashMap<K, V>();
  }

  /**
   * Creates a {@code CompactLinkedHashMap} instance, with a high enough "initial capacity"
   * that it <i>should</i> hold {@code expectedSize} entries without growth.
   *
   * @param expectedSize the number of entries you expect to add to the returned map
   * @return a new, empty {@code CompactLinkedHashMap} with enough capacity to hold {@code
   *         expectedSize} elements without resizing
   * @throws IllegalArgumentException if {@code expectedSize} is negative
   */
  public static <K, V> CompactLinkedHashMap<K, V> createWithExpectedSize(int expectedSize) {
    return new CompactLinkedHashMap<K, V>(expectedSize);
  }
  
  private static final int UNSET = -1;
  private static final int ENDPOINT = -2;
  
  /**
   * Pointer to the first entry (in insertion order) in the map, or ENDPOINT if the map is empty.
   */
  private transient int firstEntry;
  
  /**
   * Pointer to the last entry (in insertion order) in the map, or ENDPOINT if the map is empty.
   */
  private transient int lastEntry;
  
  /**
   * Pointer to the predecessor (in insertion order) of the corresponding entry. An ENDPOINT value
   * indicates the first entry in insertion order. Values at indices >= size() are all UNSET.
   */
  private transient int[] predecessor;
  
  /**
   * Pointer to the successor (in insertion order) of the corresponding entry. An ENDPOINT value
   * indicates the last entry in insertion order. Values at indices >= size() are all UNSET.
   */
  private transient int[] successor;

  CompactLinkedHashMap() {
    super();
  }

  CompactLinkedHashMap(int capacity) {
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
  
  /**
   * Update the linked list to make succ come right after pred.  Either argument may be ENDPOINT,
   * indicating the conceptual "end" of the linked list.
   */
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
  void insertEntry(int entryIndex, K key, V value, int hash) {
    super.insertEntry(entryIndex, key, value, hash);
    succeeds(lastEntry, entryIndex);
    succeeds(entryIndex, ENDPOINT);
  }

  @Override
  void resizeEntries(int newCapacity) {
    super.resizeEntries(newCapacity);
    int oldCapacity = predecessor.length;
    predecessor = Arrays.copyOf(predecessor, newCapacity);
    successor = Arrays.copyOf(successor, newCapacity);
    if (newCapacity > oldCapacity) {
      Arrays.fill(predecessor, oldCapacity, newCapacity, UNSET);
      Arrays.fill(successor, oldCapacity, newCapacity, UNSET);
    }
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
  
  private abstract class Itr<T> implements Iterator<T> {
    int next = firstEntry;
    int toRemove = UNSET;
    short expectedModCount = modCount;
    
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
    
    abstract T getResultForEntry(int entryIndex);

    @Override
    public T next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      toRemove = next;
      T result = getResultForEntry(next);
      next = successor[next];
      return result;
    }

    @Override
    public void remove() {
      Iterators.checkRemove(toRemove != UNSET);
      CompactLinkedHashMap.this.remove(getKeyForEntry(toRemove));
      if (next == size()) {
        // we just moved the next entry into the position held by toRemove
        next = toRemove;
      }
      toRemove = -1;
      expectedModCount = modCount;
    }
  }

  @Override
  Iterator<K> keyIterator() {
    return new Itr<K>() {
      @Override
      K getResultForEntry(int entryIndex) {
        return getKeyForEntry(entryIndex);
      }
    };
  }

  @Override
  Iterator<V> valueIterator() {
    return new Itr<V>() {
      @Override
      V getResultForEntry(int entryIndex) {
        return getValueForEntry(entryIndex);
      }
    };
  }

  @Override
  Iterator<Entry<K, V>> entryIterator() {
    return new Itr<Entry<K, V>>() {
      @Override
      Entry<K, V> getResultForEntry(int entryIndex) {
        return getMapEntry(entryIndex);
      }
    };
  }
}
