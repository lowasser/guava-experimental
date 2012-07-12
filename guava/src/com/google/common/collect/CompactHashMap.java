package com.google.common.collect;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;

import com.google.common.base.Objects;
import com.google.common.primitives.Ints;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.annotation.Nullable;

final class CompactHashMap<K, V> extends AbstractMap<K, V> implements Serializable {
  // We use a fixed load factor of 1.0 for simplicity.

  private static int getSmearedHash(long metadata) {
    return (int) metadata;
  }

  private static int getNextInBucket(long metadata) {
    return (int) (metadata >>> 32);
  }

  private static long packMetadata(int nextInBucket, int smearedHash) {
    long mask = 0xFFFFFFFF;
    return (((long) (nextInBucket & mask)) << 32) | (smearedHash & mask);
  }

  private static long updateNextInBucket(long metadata, int newNextInBucket) {
    long mask = 0xFFFFFFFF;
    return (metadata & mask) | ((long) (newNextInBucket & mask) << 32);
  }

  private static int smearHash(Object key) {
    return Hashing.smear((key == null) ? 0 : key.hashCode());
  }
  
  private static final int DEFAULT_INITIAL_SIZE = 8;

  /*
   * The low 32 bits contain the smeared hash code of the key; the high 32 bits are an index to the
   * next entry in the bucket.
   */
  private transient long[] packedMetadata;
  private transient Object[] entryKeys;
  private transient Object[] entryValues;
  private transient int modCount;
  private transient int size;

  private transient int[] hashTable;
  
  public CompactHashMap() {
    this(DEFAULT_INITIAL_SIZE);
  }
  
  public CompactHashMap(int expectedSize) {
    init(expectedSize);
  }
  
  private void init(int expectedSize) {
    checkArgument(expectedSize >= 0, "expectedSize (%s) must be >= 0", expectedSize);
    expectedSize = Math.max(2, expectedSize);
    int entrySize = Lists.computeArrayListCapacity(expectedSize);
    packedMetadata = new long[entrySize];
    entryKeys = new Object[entrySize];
    entryValues = new Object[entrySize];
    
    int tableSize = Integer.highestOneBit(expectedSize - 1) << 1;
    if (tableSize < 0) {
      tableSize = Ints.MAX_POWER_OF_TWO;
    }
    this.hashTable = new int[tableSize];
    Arrays.fill(hashTable, -1);
  }

  private int entryFor(@Nullable Object key, int smearedHash) {
    int bucket = smearedHash & (hashTable.length - 1);
    for (int entry = hashTable[bucket]; entry != -1; entry = getNextInBucket(packedMetadata[entry])) {
      if (smearedHash == getSmearedHash(packedMetadata[entry])
          && Objects.equal(entryKeys[entry], key)) {
        return entry;
      }
    }
    return -1;
  }

  @Override
  public boolean containsKey(Object key) {
    return entryFor(key, smearHash(key)) != -1;
  }

  @Override
  public V get(@Nullable Object key) {
    int smearedHash = smearHash(key);
    int entry = entryFor(key, smearedHash);
    if (entry == -1) {
      return null;
    } else {
      @SuppressWarnings("unchecked")
      // guaranteed to be a V
      V value = (V) entryValues[entry];
      return value;
    }
  }

  @Override
  public V put(@Nullable K key, @Nullable V value) {
    int smearedHash = smearHash(key);
    int entry = entryFor(key, smearedHash);
    if (entry != -1) {
      @SuppressWarnings("unchecked")
      // guaranteed to be a V
      V oldValue = (V) entryValues[entry];
      entryValues[entry] = value;
      return oldValue;
    }

    expandEntries(size + 1);
    entryKeys[size] = key;
    entryValues[size] = value;
    int bucket = smearedHash & (hashTable.length - 1);
    packedMetadata[size] = packMetadata(hashTable[bucket], smearedHash);
    hashTable[bucket] = size;
    size++;
    modCount++;
    expandTableIfNecessary();
    return null;
  }

  private int threshold() {
    return hashTable.length;
  }

  private void expandTableIfNecessary() {
    if (size > threshold() && hashTable.length < Ints.MAX_POWER_OF_TWO) {
      int newTableSize = hashTable.length * 2;
      int[] newHashTable = new int[newTableSize];
      Arrays.fill(newHashTable, -1);
      int mask = newTableSize - 1;
      for (int entry = 0; entry < size; entry++) {
        int smearedHash = getSmearedHash(packedMetadata[entry]);
        int bucket = smearedHash & mask;
        packedMetadata[entry] = packMetadata(newHashTable[bucket], smearedHash);
        newHashTable[bucket] = entry;
      }
      this.hashTable = newHashTable;
    }
  }

  private void expandEntries(int minSize) {
    int oldLength = entryKeys.length;
    if (oldLength < minSize) {
      int newLength = oldLength;
      do {
        newLength = newLength + (newLength >>> 1) + 1;
      } while (newLength < minSize);
      entryKeys = Arrays.copyOf(entryKeys, newLength);
      entryValues = Arrays.copyOf(entryValues, newLength);
      packedMetadata = Arrays.copyOf(packedMetadata, newLength);
    }
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public V remove(@Nullable Object key) {
    int smearedHash = smearHash(key);
    int bucket = smearedHash & (hashTable.length - 1);
    int prev = -1;
    for (int entry = hashTable[bucket]; entry != -1;
         prev = entry, entry = getNextInBucket(packedMetadata[entry])) {
      if (smearedHash == getSmearedHash(packedMetadata[entry])
          && Objects.equal(entryKeys[entry], key)) {
        @SuppressWarnings("unchecked")
        // guaranteed to be a V
        V oldValue = (V) entryValues[entry];
        removeEntry(entry, prev);
        return oldValue;
      }
    }

    return null;
  }

  private void removeEntry(int entry, int prev) {
    int bucket = getSmearedHash(packedMetadata[entry]) & (hashTable.length - 1);
    // first, delete from the bucket linked list
    if (prev == -1) { // first in bucket
      hashTable[bucket] = getNextInBucket(packedMetadata[entry]);
    } else {
      packedMetadata[prev] = updateNextInBucket(
          packedMetadata[prev],
          getNextInBucket(packedMetadata[entry]));
    }

    if (entry == size - 1) {
      // this is the last entry in the list; we can just null it out
      entryKeys[entry] = null;
      entryValues[entry] = null;
    } else {
      // we need to swap the last entry in the list into this position
      int swappedEntry = size - 1;
      entryKeys[entry] = entryKeys[swappedEntry];
      entryValues[entry] = entryValues[swappedEntry];
      packedMetadata[entry] = packedMetadata[swappedEntry];

      // we need to update the linked list for that entry
      int swappedBucket = getSmearedHash(packedMetadata[entry]) & (hashTable.length - 1);
      int swappedPrev = -1;
      for (int swapBucketEntry = hashTable[swappedBucket];;
           swapBucketEntry = getNextInBucket(packedMetadata[swapBucketEntry])) {

        if (swapBucketEntry == swappedEntry) {
          // need to update pointers to point to the new location
          if (swappedPrev == -1) {
            hashTable[swappedBucket] = entry;
          } else {
            packedMetadata[swappedPrev] = updateNextInBucket(
                packedMetadata[swappedPrev],
                entry);
          }
          break;
        } else if (swapBucketEntry == -1) {
          swapBucketEntry = -1;
        }

        swappedPrev = swapBucketEntry;
      }
      entryKeys[swappedEntry] = null;
      entryValues[swappedEntry] = null;
    }

    size--;
    modCount++;
  }

  @Override
  public void clear() {
    Arrays.fill(hashTable, -1);
    Arrays.fill(entryKeys, null);
    Arrays.fill(entryValues, null);
    size = 0;
    modCount++;
  }

  private transient Set<Entry<K, V>> entrySet;

  @Override
  public Set<Entry<K, V>> entrySet() {
    Set<Entry<K, V>> result = entrySet;
    if (result == null) {
      result = entrySet = new Maps.EntrySet<K, V>() {
        @Override
        Map<K, V> map() {
          return CompactHashMap.this;
        }

        @Override
        public Iterator<Entry<K, V>> iterator() {
          return new Itr<Entry<K, V>>() {
            @Override
            Entry<K, V> result(int entry) {
              return new MapEntry(entry);
            }
          };
        }
      };
    }
    return result;
  }

  private transient Set<K> keySet;

  @Override
  public Set<K> keySet() {
    Set<K> result = keySet;
    if (result == null) {
      result = keySet = new Maps.KeySet<K, V>() {
        @Override
        Map<K, V> map() {
          return CompactHashMap.this;
        }

        @Override
        public Iterator<K> iterator() {
          return new Itr<K>() {
            @SuppressWarnings("unchecked")
            @Override
            K result(int entry) {
              return (K) entryKeys[entry];
            }
          };
        }
      };
    }
    return result;
  }

  private transient Collection<V> valuesCollection;

  @Override
  public Collection<V> values() {
    Collection<V> result = valuesCollection;
    if (result == null) {
      result = valuesCollection = new Maps.Values<K, V>() {
        @Override
        Map<K, V> map() {
          return CompactHashMap.this;
        }

        @Override
        public Iterator<V> iterator() {
          return new Itr<V>() {

            @SuppressWarnings("unchecked")
            @Override
            V result(int entry) {
              return (V) entryValues[entry];
            }
          };
        }
      };
    }
    return result;
  }

  private abstract class Itr<T> implements Iterator<T> {
    int index = 0;
    int toRemove = -1;
    int expectedModCount = modCount;

    private void checkForComodification() {
      if (modCount != expectedModCount) {
        throw new ConcurrentModificationException();
      }
    }

    abstract T result(int entry);

    @Override
    public boolean hasNext() {
      checkForComodification();
      return index < size;
    }

    @Override
    public T next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      toRemove = index;
      return result(index++);
    }

    @Override
    public void remove() {
      Iterators.checkRemove(toRemove != -1);
      CompactHashMap.this.remove(entryKeys[toRemove]);
      index--;
      /*
       * We swapped an entry into the position toRemove used to hold, so the iteration must go back
       * to hit that entry next.
       */
      toRemove = -1;
      expectedModCount = modCount;
    }
  }

  /**
   * An implementation of {@code Map.Entry} that updates with the backing map.
   */
  private final class MapEntry extends AbstractMapEntry<K, V> {
    private final K key;
    private int keySmearedHash;
    private int index;
    private int expectedModCount;

    @SuppressWarnings("unchecked")
    MapEntry(int index) {
      checkElementIndex(index, size);
      this.index = index;
      this.key = (K) entryKeys[index];
      this.expectedModCount = modCount;
      this.keySmearedHash = getSmearedHash(packedMetadata[index]);
    }

    private void updateIndex() {
      if (modCount != expectedModCount) {
        index = entryFor(key, keySmearedHash);
      }
    }

    @Override
    public K getKey() {
      return key;
    }

    @Override
    public V getValue() {
      updateIndex();
      if (index == -1) {
        return null;
      } else {
        @SuppressWarnings("unchecked")
        V value = (V) entryValues[index];
        return value;
      }
    }

    @Override
    public V setValue(@Nullable V value) {
      updateIndex();
      if (index == -1) {
        return put(key, value);
      } else {
        @SuppressWarnings("unchecked")
        // known to be a V
        V oldValue = (V) entryValues[index];
        entryValues[index] = value;
        return oldValue;
      }
    }
  }

  private void writeObject(ObjectOutputStream stream) throws IOException {
    stream.defaultWriteObject();
    Serialization.writeMap(this, stream);
  }
  
  private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
    stream.defaultReadObject();
    init(DEFAULT_INITIAL_SIZE);
    Serialization.populateMap(this, stream);
  }
}
