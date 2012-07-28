package com.google.common.collect;

import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * CompactLinkedHashMap is an implementation of a Map. All optional operations (put and
 * remove) are supported.  Null keys and values are specifically supported.
 *
 * <p>{@code get(k)}, {@code containsKey(k)}, {@code put(k, v)}, and {@code remove(k)}, are all
 * (expected and amortized) constant time operations. Expected in the hashtable sense (depends on
 * the hash function doing a good job of distributing the elements to the buckets to a distribution
 * not far from uniform), and amortized since some operations can trigger a hash table resize.
 *
 * <p>Like {@code java.util.LinkedHashMap}, {@code CompactLinkedHashMap} is guaranteed to iterate
 * over entries in insertion order.  However, {@code CompactLinkedHashMap} is specifically
 * optimized to reduce absolute memory consumption and load on the garbage collector with minimal
 * impact on performance.
 *
 * @author Louis Wasserman
 */
public class CompactLinkedHashMap<K, V> extends CompactHashMap<K, V> {
  /**
   * Creates an empty {@code CompactLinkedHashMap} instance.
   */
  public static <K, V> CompactLinkedHashMap<K, V> create() {
    return new CompactLinkedHashMap<K, V>();
  }

  /**
   * Creates a <i>mutable</i> {@code CompactLinkedHashMap} instance containing the entries of the
   * given map in unspecified order.
   *
   * @param map the entries that the map should contain
   * @return a new {@code CompactLinkedHashMap} containing those entries
   */
  public static <K, V> CompactLinkedHashMap<K, V> create(Map<? extends K, V> map) {
    CompactLinkedHashMap<K, V> result = createWithExpectedSize(map.size());
    result.putAll(map);
    return result;
  }

  /**
   * Creates a {@code CompactLinkedHashMap} instance, with a high enough "initial capacity"
   * that it <i>should</i> hold {@code expectedSize} entries without growth.
   *
   * @param expectedSize the number of elements you expect to add to the returned set
   * @return a new, empty {@code CompactLinkedHashMap} with enough capacity to hold {@code
   *         expectedSize} elements without resizing
   * @throws IllegalArgumentException if {@code expectedSize} is negative
   */
  public static <K, V> CompactLinkedHashMap<K, V> createWithExpectedSize(int expectedSize) {
    return new CompactLinkedHashMap<K, V>(expectedSize);
  }
  
  /**
   * Conceptual "endpoint" index.  If an entry's predecessor is ENDPOINT, it's the first entry,
   * if its successor is ENDPOINT, it's the last entry.
   */
  private static final int ENDPOINT = -2;
  
  private static final long PRED_MASK = 0xFFFFFFFF00000000L;
  private static final long SUCC_MASK = 0xFFFFFFFFL;
  
  /**
   * The high 32 bits are the predecessor link, the low 32 bits are the successor link.
   */
  private transient long[] links;
  private transient int firstEntry;
  private transient int lastEntry;
  
  private int getPredecessor(int entryIndex) {
    return (int) (links[entryIndex] >>> 32);
  }
  
  private int getSuccessor(int entryIndex) {
    return (int) links[entryIndex];
  }
  
  private void setPredecessor(int entryIndex, int pred) {
    links[entryIndex] =
        (links[entryIndex] & SUCC_MASK)
        | ((long) pred << 32);
  }
  
  private void setSuccessor(int entryIndex, int succ) {
    links[entryIndex] =
        (links[entryIndex] & PRED_MASK)
        | (succ & SUCC_MASK);
  }

  CompactLinkedHashMap() {
    super();
  }

  CompactLinkedHashMap(int capacity) {
    super(capacity);
  }

  @Override
  void init(int initialCapacity, float loadFactor) {
    super.init(initialCapacity, loadFactor);
    this.links = new long[initialCapacity];
    this.firstEntry = ENDPOINT;
    this.lastEntry = ENDPOINT;
  }
  
  private void succeeds(int pred, int succ) {
    if (pred == ENDPOINT) {
      firstEntry = succ;
    } else {
      setSuccessor(pred, succ);
    }
    
    if (succ == ENDPOINT) {
      lastEntry = pred;
    } else {
      setPredecessor(succ, pred);
    }
  }

  @Override
  void insertEntry(int entryIndex, K key, V value, int hash) {
    super.insertEntry(entryIndex, key, value, hash);
    succeeds(lastEntry, entryIndex);
    succeeds(entryIndex, ENDPOINT);
  }

  @Override
  void moveEntry(int dstIndex) {
    int srcIndex = size() - 1;
    succeeds(getPredecessor(dstIndex), getSuccessor(dstIndex));
    if (dstIndex < srcIndex) {
      succeeds(getPredecessor(srcIndex), dstIndex);
      succeeds(dstIndex, getSuccessor(srcIndex));
      links[srcIndex] = -1L;
    }
    super.moveEntry(dstIndex);
  }

  @Override
  public void trimToSize() {
    super.trimToSize();
    if (size() < links.length) {
      this.links = Arrays.copyOf(links, size());
    }
  }
  
  @Override
  void resizeEntries(int newCapacity) {
    super.resizeEntries(newCapacity);
    int oldCapacity = links.length;
    links = Arrays.copyOf(links, newCapacity);
    if (newCapacity > oldCapacity) {
      Arrays.fill(links, oldCapacity, newCapacity, -1L);
    }
  }

  @Override
  Iterator<K> keyIterator() {
    return new Itr<K>() {
      @Override
      K outputForEntry(int entryIndex) {
        return getKey(entryIndex);
      }
    };
  }

  @Override
  Iterator<V> valuesIterator() {
    return new Itr<V>() {
      @Override
      V outputForEntry(int entryIndex) {
        return getValue(entryIndex);
      }
    };
  }

  @Override
  Iterator<Entry<K, V>> entryIterator() {
    return new Itr<Entry<K, V>>() {
      @Override
      Entry<K, V> outputForEntry(int entryIndex) {
        return getEntry(entryIndex);
      }
    };
  }

  private abstract class Itr<T> implements Iterator<T> {
    int next = firstEntry;
    int toRemove = -1;
    short expectedModCount = modCount;
    
    private void checkForConcurrentModification() {
      if (modCount != expectedModCount) {
        throw new ConcurrentModificationException();
      }
    }
    
    abstract T outputForEntry(int entryIndex);

    @Override
    public boolean hasNext() {
      return next != ENDPOINT;
    }

    @Override
    public T next() {
      checkForConcurrentModification();
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      toRemove = next;
      T result = outputForEntry(next);
      next = getSuccessor(next);
      return result;
    }

    @Override
    public void remove() {
      checkForConcurrentModification();
      Iterators.checkRemove(toRemove != -1);
      CompactLinkedHashMap.this.remove(getKey(toRemove));
      if (next == size()) {
        // we swapped next into the position held by toRemove
        next = toRemove;
      }
      expectedModCount = modCount;
      toRemove = -1;
    }
  }
}
