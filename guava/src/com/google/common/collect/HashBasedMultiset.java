package com.google.common.collect;

import com.google.common.base.Objects;
import com.google.common.primitives.Ints;

import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import javax.annotation.Nullable;

/**
 * Skeleton superclass for {@code HashMultiset} and {@code LinkedHashMultiset}.
 * 
 * <p>We don't want to expose a public "is-a" relationship between them, so we extend them both
 * from a superclass.
 * 
 * @author Louis Wasserman
 */
class HashBasedMultiset<E> extends AbstractMultiset<E> {
  transient Object[] elements;
  private transient long[] metadata;
  private transient int[] counts;

  transient int modCount;
  private transient int[] hashTable;
  private transient int distinctElements;
  private transient long totalCount;
  
  HashBasedMultiset() {
    this(8);
  }
  
  HashBasedMultiset(int expectedSize) {
    initHashTable(Math.max(2, expectedSize));
  }
  
  void initHashTable(int expectedSize) {
    elements = new Object[expectedSize];
    metadata = new long[expectedSize];
    Arrays.fill(metadata, -1);
    counts = new int[expectedSize];
    
    int tableSize = Integer.highestOneBit(expectedSize - 1) << 1;
    if (tableSize < 0) {
      tableSize = Ints.MAX_POWER_OF_TWO;
    }
    hashTable = new int[tableSize];
    Arrays.fill(hashTable, -1);
  }

  private int getSmearedHash(int entryIndex) {
    return (int) metadata[entryIndex];
  }

  private int getNextInBucket(int entryIndex) {
    return (int) (metadata[entryIndex] >>> 32);
  }

  private void setNextInBucket(int entryIndex, int next) {
    long entryMeta = metadata[entryIndex];
    entryMeta &= 0xFFFFFFFFL;
    entryMeta |= (long) next << 32;
    metadata[entryIndex] = entryMeta;
  }

  private void setHashCode(int entryIndex, int hashCode) {
    long entryMeta = metadata[entryIndex];
    entryMeta &= 0xFFFFFFFF00000000L;
    entryMeta |= hashCode & 0xFFFFFFFFL;
    metadata[entryIndex] = entryMeta;
  }

  private int entryIndex(@Nullable Object element) {
    Object[] elements = this.elements;

    int hash = Hashing.smear((element == null) ? 0 : element.hashCode());
    int bucket = hash & (hashTable.length - 1);
    for (int next = hashTable[bucket]; next != -1; next = getNextInBucket(next)) {
      if (getSmearedHash(next) == hash && Objects.equal(element, elements[next])) {
        return next;
      }
    }
    return -1;
  }

  @Override
  public int count(@Nullable Object element) {
    int entryIndex = entryIndex(element);
    return (entryIndex == -1) ? 0 : counts[entryIndex];
  }

  @Override
  public void clear() {
    Arrays.fill(elements, 0, distinctElements, null);
    Arrays.fill(metadata, 0, distinctElements, -1);
    Arrays.fill(counts, 0, distinctElements, 0);
    Arrays.fill(hashTable, -1);
    distinctElements = 0;
    totalCount = 0;
    modCount++;
  }

  @Override
  public int add(@Nullable E element, int occurrences) {
    Multisets.checkNonnegative(occurrences, "occurrences");
    if (occurrences == 0) {
      return count(element);
    }
    Object[] elements = this.elements;
    int hash = Hashing.smear((element == null) ? 0 : element.hashCode());
    int bucket = hash & (hashTable.length - 1);
    for (int next = hashTable[bucket]; next != -1; next = getNextInBucket(next)) {
      if (getSmearedHash(next) == hash && Objects.equal(element, elements[next])) {
        int oldCount = counts[next];
        long newCount = (long) oldCount + occurrences;
        if (newCount > Integer.MAX_VALUE) {
          throw new IllegalArgumentException("Too many occurrences: " + newCount);
        }
        counts[next] = (int) newCount;
        totalCount += occurrences;
        return oldCount;
      }
    }

    addEntry(element, hash, occurrences);
    return 0;
  }

  private void addEntry(
      E element,
      int hash,
      int occurrences) {
    resizeEntriesMaybe(distinctElements + 1);
    int bucket = hash & (hashTable.length - 1);
    int nextInBucket = hashTable[bucket];
    int newEntryIndex = distinctElements;
    initEntry(newEntryIndex, element, hash, occurrences, nextInBucket);
    hashTable[bucket] = newEntryIndex;
    distinctElements++;
    totalCount += occurrences;
    resizeHashTable();
    modCount++;
  }

  void initEntry(int newEntryIndex, E element, int hash, int occurrences, int nextInBucket) {
    elements[newEntryIndex] = element;
    counts[newEntryIndex] = occurrences;
    setHashCode(newEntryIndex, hash);
    setNextInBucket(newEntryIndex, nextInBucket);
  }

  private void resizeEntriesMaybe(int minEntries) {
    int oldEntries = elements.length;
    if (minEntries > oldEntries) {
      int newEntries = oldEntries;
      do {
        newEntries = newEntries + (newEntries >>> 1) + 1;
        if (newEntries < 0) {
          newEntries = Integer.MAX_VALUE;
        }
      } while (newEntries < minEntries);
      resizeEntries(newEntries);
    }
  }

  void resizeEntries(int newEntries) {
    int oldEntries = elements.length;
    elements = Arrays.copyOf(elements, newEntries);
    metadata = Arrays.copyOf(metadata, newEntries);
    if (oldEntries < newEntries) {
      Arrays.fill(metadata, oldEntries, newEntries, -1);
    }
    counts = Arrays.copyOf(counts, newEntries);
  }

  private void resizeHashTable() {
    int threshold = hashTable.length;
    if (distinctElements > threshold && hashTable.length < Ints.MAX_POWER_OF_TWO) {
      int newTableSize = hashTable.length * 2;
      int mask = newTableSize - 1;
      int[] newTable = new int[newTableSize];
      Arrays.fill(newTable, -1);
      for (int i = 0; i < distinctElements; i++) {
        int smearedHash = getSmearedHash(i);
        int newBucket = smearedHash & mask;
        setNextInBucket(i, newTable[newBucket]);
        newTable[newBucket] = i;
      }
      this.hashTable = newTable;
    }
  }

  @Override
  public int remove(@Nullable Object element, int occurrences) {
    Multisets.checkNonnegative(occurrences, "occurrences");
    if (occurrences == 0) {
      return count(element);
    }
    Object[] elements = this.elements;
    int hash = Hashing.smear((element == null) ? 0 : element.hashCode());
    int bucket = hash & (hashTable.length - 1);
    int prev = -1;
    for (int next = hashTable[bucket]; next != -1; prev = next, next = getNextInBucket(next)) {
      if (hash == getSmearedHash(next) && Objects.equal(element, elements[next])) {
        int oldCount = counts[next];
        if (oldCount <= occurrences) {
          removeEntry(bucket, prev, next);
        } else {
          counts[next] = oldCount - occurrences;
          totalCount -= occurrences;
        }
        return oldCount;
      }
    }
    return 0;
  }

  int removeAllOccurrences(@Nullable Object element) {
    Object[] elements = this.elements;
    int hash = Hashing.smear((element == null) ? 0 : element.hashCode());
    int bucket = hash & (hashTable.length - 1);
    int prev = -1;
    for (int next = hashTable[bucket]; next != -1; prev = next, next = getNextInBucket(next)) {
      if (hash == getSmearedHash(next) && Objects.equal(element, elements[next])) {
        int oldCount = counts[next];
        removeEntry(bucket, prev, next);
        return oldCount;
      }
    }
    return 0;
  }

  private void removeEntry(int bucket, int prev, int toRemove) {
    int oldCount = counts[toRemove];
    // first, delete from the bucket linked list
    if (prev == -1) { // first in bucket
      hashTable[bucket] = getNextInBucket(toRemove);
    } else {
      setNextInBucket(prev, getNextInBucket(toRemove));
    }

    moveEntry(toRemove);
    distinctElements--;
    modCount++;
    totalCount -= oldCount;
  }

  /**
   * Moves the last entry to the specified index, and nulls out its old position.
   */
  void moveEntry(int dstEntry) {
    int srcEntry = distinctElements - 1;
    if (dstEntry < srcEntry) {
      elements[dstEntry] = elements[srcEntry];
      counts[dstEntry] = counts[srcEntry];
      metadata[dstEntry] = metadata[srcEntry];

      elements[srcEntry] = null;
      counts[srcEntry] = 0;
      metadata[srcEntry] = -1;

      // update the moved entry's bucket links to point to its new location
      int bucket = getSmearedHash(dstEntry) & (hashTable.length - 1);
      int prev = -1;
      int next = hashTable[bucket];
      while (next != srcEntry) {
        prev = next;
        next = getNextInBucket(next);
      }
      if (prev == -1) {
        hashTable[bucket] = dstEntry;
      } else {
        setNextInBucket(prev, dstEntry);
      }
    } else {
      elements[dstEntry] = null;
      counts[dstEntry] = 0;
      metadata[dstEntry] = -1;
    }
  }

  @Override
  public int setCount(@Nullable E element, int count) {
    Multisets.checkNonnegative(count, "count");
    if (count == 0) {
      return removeAllOccurrences(element);
    }

    int hash = Hashing.smear((element == null) ? 0 : element.hashCode());
    int bucket = hash & (hashTable.length - 1);
    Object[] elements = this.elements;
    for (int next = hashTable[bucket]; next != -1; next = getNextInBucket(next)) {
      if (hash == getSmearedHash(next) && Objects.equal(element, elements[next])) {
        int oldCount = counts[next];
        counts[next] = count;
        totalCount += count - oldCount;
        return oldCount;
      }
    }

    addEntry(element, hash, count);
    return 0;
  }

  @Override
  public boolean setCount(@Nullable E element, int oldCount, int newCount) {
    Multisets.checkNonnegative(oldCount, "oldCount");
    Multisets.checkNonnegative(newCount, "newCount");
    if (oldCount == newCount) {
      return count(element) == oldCount;
    }

    Object[] elements = this.elements;
    int hash = Hashing.smear((element == null) ? 0 : element.hashCode());
    int bucket = hash & (hashTable.length - 1);
    int prev = -1;
    for (int next = hashTable[bucket]; next != -1; prev = next, next = getNextInBucket(next)) {
      if (hash == getSmearedHash(next) && Objects.equal(element, elements[next])) {
        if (oldCount == counts[next]) {
          if (newCount == 0) {
            removeEntry(bucket, prev, next);
          } else {
            counts[next] = newCount;
            totalCount += newCount - oldCount;
          }
          return true;
        } else {
          return false;
        }
      }
    }

    if (oldCount == 0) {
      addEntry(element, hash, newCount);
      return true;
    } else {
      return false;
    }
  }

  /**
   * View of the multiset entry for a given element. Reflects changes to the multiset since
   * construction.
   */
  private class Entry extends Multisets.AbstractEntry<E> {
    @Nullable
    private final E element;
    private int entryIndex;
    
    @SuppressWarnings("unchecked")
    Entry(int entryIndex) {
      this.entryIndex = entryIndex;
      this.element = (E) elements[entryIndex];
    }

    private void updateIndex() {
      if (entryIndex == -1 || entryIndex > distinctElements
          || !Objects.equal(element, elements[entryIndex])) {
        entryIndex = entryIndex(element);
      }
    }

    @Override
    public E getElement() {
      return element;
    }

    @Override
    public int getCount() {
      updateIndex();
      return (entryIndex == -1) ? 0 : counts[entryIndex];
    }
  }
  
  Multiset.Entry<E> getEntry(int entryIndex) {
    return new Entry(entryIndex);
  }
  
  private abstract class Itr<T> implements Iterator<T> {
    int next = 0;
    int toRemove = -1;
    int expectedModCount = modCount;
    
    private void checkForConcurrentModification() {
      if (modCount != expectedModCount) {
        throw new ConcurrentModificationException();
      }
    }
    
    abstract T resultForEntry(int entryIndex);

    @Override
    public boolean hasNext() {
      checkForConcurrentModification();
      return next < distinctElements;
    }

    @Override
    public T next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      T result = resultForEntry(next);
      toRemove = next;
      next++;
      return result;
    }

    @Override
    public void remove() {
      checkForConcurrentModification();
      Iterators.checkRemove(toRemove != -1);
      removeAllOccurrences(elements[toRemove]);
      next--;
      toRemove = -1;
      expectedModCount = modCount;
    }
  }

  @Override
  Iterator<Multiset.Entry<E>> entryIterator() {
    return new Itr<Multiset.Entry<E>>() {
      @Override
      Multiset.Entry<E> resultForEntry(int entryIndex) {
        return getEntry(entryIndex);
      }
    };
  }

  @Override
  int distinctElements() {
    return distinctElements;
  }

  @Override
  public int size() {
    return Ints.saturatedCast(totalCount);
  }
}
