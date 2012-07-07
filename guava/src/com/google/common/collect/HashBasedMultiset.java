package com.google.common.collect;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Objects;
import com.google.common.math.IntMath;
import com.google.common.primitives.Ints;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import javax.annotation.Nullable;

/**
 * Skeleton superclass for {@code HashMultiset} and {@code LinkedHashMultiset}.
 * 
 * <p>We don't want to expose a public "is-a" relationship between them, so we extend them both from a superclass.
 * 
 * @author Louis Wasserman
 */
class HashBasedMultiset<E> extends AbstractMultiset<E> {
  static class HashEntry<E> extends Multisets.AbstractEntry<E> {
    private final E elem;
    private final int smearedHash;
    private int count;
    @Nullable private HashEntry<E> nextInBucket;

    HashEntry(E elem, int smearedHash, int count, @Nullable HashEntry<E> nextInBucket) {
      this.elem = elem;
      this.smearedHash = smearedHash;
      this.count = count;
      this.nextInBucket = nextInBucket;
    }

    @Override
    public final E getElement() {
      return elem;
    }

    @Override
    public final int getCount() {
      return count;
    }
  }

  private transient HashEntry<E>[] hashTable;
  private transient int distinctElements;
  transient int modCount;
  private transient long size;
  
  HashBasedMultiset() {
    this(8);
  }

  HashBasedMultiset(int expectedElements) {
    initHashTable(expectedElements);
  }

  void initHashTable(int expectedElements) {
    checkArgument(
        expectedElements >= 0,
        "expectedElements must be >= 0 but was %s",
        expectedElements);
    expectedElements = Math.max(2, expectedElements);
    int tableSize = Integer.highestOneBit(expectedElements - 1) << 1;
    this.hashTable = createTable(tableSize);
  }

  @SuppressWarnings("unchecked")
  private HashEntry<E>[] createTable(int tableSize) {
    return new HashEntry[tableSize];
  }

  @Override
  public int size() {
    return Ints.saturatedCast(size);
  }

  @Override
  public int count(@Nullable Object element) {
    int smearedHash = Hashing.smear((element == null) ? 0 : element.hashCode());
    int bucket = smearedHash & (hashTable.length - 1);
    for (HashEntry<E> entry = hashTable[bucket]; entry != null; entry = entry.nextInBucket) {
      if (smearedHash == entry.smearedHash && Objects.equal(element, entry.elem)) {
        return entry.getCount();
      }
    }
    return 0;
  }

  private HashEntry<E> insertEntry(E element, int smearedHash, int count) {
    distinctElements++;
    modCount++;
    size += count;
    int bucket = smearedHash & (hashTable.length - 1);
    HashEntry<E> newEntry = createEntry(element, smearedHash, count, hashTable[bucket]);
    hashTable[bucket] = newEntry;
    expandIfNecessary();
    return newEntry;
  }

  HashEntry<E> createEntry(
      @Nullable E element, int smearedHash, int count, HashEntry<E> nextInBucket) {
    return new HashEntry<E>(element, smearedHash, count, nextInBucket);
  }

  private void expandIfNecessary() {
    if (distinctElements > hashTable.length && hashTable.length < Ints.MAX_POWER_OF_TWO) {
      HashEntry<E>[] newTable = createTable(hashTable.length * 2);
      int mask = newTable.length - 1;
      for (@Nullable HashEntry<E> bucketHead : hashTable) {
        HashEntry<E> entry = bucketHead;
        while (entry != null) {
          HashEntry<E> next = entry.nextInBucket;
          
          int newBucket = entry.smearedHash & mask;
          entry.nextInBucket = newTable[newBucket];
          newTable[newBucket] = entry;
          
          entry = next;
        }
      }
      this.hashTable = newTable;
    }
  }

  void deleteEntry(HashEntry<E> entry, @Nullable HashEntry<E> prev) {
    int bucket = entry.smearedHash & (hashTable.length - 1);
    if (prev == null) { // first in the bucket
      hashTable[bucket] = entry.nextInBucket;
    } else {
      prev.nextInBucket = entry.nextInBucket;
    }
    size -= entry.count;
    entry.count = 0;
    distinctElements--;
    modCount++;
  }

  @Override
  public int add(@Nullable E element, int occurrences) {
    Multisets.checkNonnegative(occurrences, "occurrences");
    if (occurrences == 0) {
      return count(element);
    }
    int smearedHash = Hashing.smear((element == null) ? 0 : element.hashCode());
    int bucket = smearedHash & (hashTable.length - 1);
    for (HashEntry<E> entry = hashTable[bucket]; entry != null; entry = entry.nextInBucket) {
      if (smearedHash == entry.smearedHash && Objects.equal(element, entry.elem)) {
        // entry already exists
        int oldCount = entry.count;
        try {
          entry.count = IntMath.checkedAdd(occurrences, oldCount);
          size += occurrences;
        } catch (ArithmeticException e) {
          throw new IllegalArgumentException("Overflow adding " + occurrences
              + " occurrences to a count of " + oldCount);
        }
        return oldCount;
      }
    }
    insertEntry(element, smearedHash, occurrences);
    return 0;
  }

  @Override
  public int remove(@Nullable Object element, int occurrences) {
    Multisets.checkNonnegative(occurrences, "occurrences");
    if (occurrences == 0) {
      return count(element);
    }
    int smearedHash = Hashing.smear((element == null) ? 0 : element.hashCode());
    int bucket = smearedHash & (hashTable.length - 1);
    HashEntry<E> prev = null;
    for (HashEntry<E> entry = hashTable[bucket]; entry != null; prev = entry, entry = entry.nextInBucket) {
      if (smearedHash == entry.smearedHash && Objects.equal(element, entry.elem)) {
        int oldCount = entry.count;
        if (oldCount > occurrences) {
          entry.count -= occurrences;
          size -= occurrences;
        } else {
          deleteEntry(entry, prev);
        }
        return oldCount;
      }
    }
    return 0; // not found
  }

  @Override
  public int setCount(@Nullable E element, int count) {
    Multisets.checkNonnegative(count, "count");
    int smearedHash = Hashing.smear((element == null) ? 0 : element.hashCode());
    int bucket = smearedHash & (hashTable.length - 1);
    HashEntry<E> prev = null;
    for (HashEntry<E> entry = hashTable[bucket]; entry != null; prev = entry, entry = entry.nextInBucket) {
      if (smearedHash == entry.smearedHash && Objects.equal(element, entry.elem)) {
        int oldCount = entry.count;
        if (count == 0) {
          deleteEntry(entry, prev);
        } else {
          entry.count = count;
          size += count - oldCount;
        }
        return oldCount;
      }
    }

    if (count > 0) {
      insertEntry(element, smearedHash, count);
    }
    return 0;
  }

  @Override
  public boolean setCount(@Nullable E element, int oldCount, int newCount) {
    Multisets.checkNonnegative(oldCount, "oldCount");
    Multisets.checkNonnegative(newCount, "newCount");
    if (oldCount == newCount) {
      return count(element) == oldCount;
    } else if (count(element) == oldCount) {
      setCount(element, newCount);
      return true;
    } else {
      return false;
    }
  }

  @Override
  public void clear() {
    size = 0;
    distinctElements = 0;
    for (int i = 0; i < hashTable.length; i++) {
      for (HashEntry<E> entry = hashTable[i]; entry != null; entry = entry.nextInBucket) {
        entry.count = 0; // zero out the old entries
      }
      hashTable[i] = null;
    }
    modCount++;
  }

  @Override
  Iterator<Entry<E>> entryIterator() {
    return new Iterator<Entry<E>>() {
      int bucket = -1;
      HashEntry<E> next = null;

      HashEntry<E> toRemove = null;

      int expectedModCount = modCount;

      private void checkForComodification() {
        if (modCount != expectedModCount) {
          throw new ConcurrentModificationException();
        }
      }

      @Override
      public boolean hasNext() {
        checkForComodification();
        while (next == null && bucket + 1 < hashTable.length) {
          next = hashTable[++bucket];
        }
        return next != null;
      }

      @Override
      public Entry<E> next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        try {
          toRemove = next;

          return wrapEntry(next);
        } finally {
          next = next.nextInBucket;
        }
      }

      @Override
      public void remove() {
        checkForComodification();
        Iterators.checkRemove(toRemove != null);
        setCount(toRemove.getElement(), 0);
        toRemove = null;
        expectedModCount = modCount;
      }
    };
  }

  /**
   * Returns a {@code Entry} that will always reflect the current count of the element in the
   * backing multiset.
   */
  Entry<E> wrapEntry(final HashEntry<E> backingEntry) {
    return new Multisets.AbstractEntry<E>() {
      @Override
      public E getElement() {
        return backingEntry.getElement();
      }

      @Override
      public int getCount() {
        int result = backingEntry.getCount();
        return (result == 0) ? HashBasedMultiset.this.count(getElement()) : result;
      }
    };
  }

  @Override
  int distinctElements() {
    return distinctElements;
  }
}
