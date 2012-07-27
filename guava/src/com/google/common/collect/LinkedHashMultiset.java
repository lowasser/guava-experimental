/*
 * Copyright (C) 2007 The Guava Authors
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

import com.google.common.annotations.GwtCompatible;
import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.Multiset.Entry;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A {@code Multiset} implementation with predictable iteration order. Its iterator orders elements
 * according to when the first occurrence of the element was added. When the multiset contains
 * multiple instances of an element, those instances are consecutive in the iteration order. If all
 * occurrences of an element are removed, after which that element is added to the multiset, the
 * element will appear at the end of the iteration.
 * 
 * <p>See the Guava User Guide article on <a href=
 * "http://code.google.com/p/guava-libraries/wiki/NewCollectionTypesExplained#Multiset">
 * {@code Multiset}</a>.
 * 
 * @author Louis Wasserman
 * @author Kevin Bourrillion
 * @author Jared Levy
 * @since 2.0 (imported from Google Collections Library)
 */
@GwtCompatible(serializable = true, emulated = true)
public final class LinkedHashMultiset<E> extends HashBasedMultiset<E> implements Serializable {
  /**
   * Creates a new, empty {@code LinkedHashMultiset} using the default initial capacity.
   */
  public static <E> LinkedHashMultiset<E> create() {
    return new LinkedHashMultiset<E>(8);
  }

  /**
   * Creates a new, empty {@code LinkedHashMultiset} with the specified expected number of distinct
   * elements.
   * 
   * @param distinctElements
   *          the expected number of distinct elements
   * @throws IllegalArgumentException
   *           if {@code distinctElements} is negative
   */
  public static <E> LinkedHashMultiset<E> create(int distinctElements) {
    return new LinkedHashMultiset<E>(distinctElements);
  }

  /**
   * Creates a new {@code LinkedHashMultiset} containing the specified elements.
   * 
   * <p>
   * This implementation is highly efficient when {@code elements} is itself a {@link Multiset}.
   * 
   * @param elements
   *          the elements that the multiset should contain
   */
  public static <E> LinkedHashMultiset<E> create(
      Iterable<? extends E> elements) {
    LinkedHashMultiset<E> multiset =
        create(Multisets.inferDistinctElements(elements));
    Iterables.addAll(multiset, elements);
    return multiset;
  }
  
  private static final int ENDPOINT = -2;
  
  private transient int firstEntry;
  private transient int lastEntry;
  // the high 32 bits is the predecessor, the low 32 is the successor
  private transient long[] links;

  LinkedHashMultiset() {
    super();
  }

  LinkedHashMultiset(int expectedSize) {
    super(expectedSize);
  }
  
  private static final long INT_MASK = 0xFFFFFFFFL;

  private void setSuccessor(int pred, int succ) {
    if (pred == ENDPOINT) {
      firstEntry = succ;
    } else {
      links[pred] = 
          (links[pred] & (INT_MASK << 32)) | (succ & INT_MASK);
    }
  }
  
  private void setPredecessor(int succ, int pred) {
    if (succ == ENDPOINT) {
      lastEntry = pred;
    } else {
      links[succ] = (links[succ] & INT_MASK)
          | ((pred & INT_MASK) << 32);
    }
  }
  
  private int getPredecessor(int entryIndex) {
    return (int) (links[entryIndex] >> 32);
  }
  
  private int getSuccessor(int entryIndex) {
    return (int) links[entryIndex];
  }
  
  private void succeeds(int pred, int succ) {
    setSuccessor(pred, succ);
    setPredecessor(succ, pred);
  }

  @Override
  void initHashTable(int expectedSize) {
    expectedSize = Math.max(2, expectedSize);
    firstEntry = ENDPOINT;
    lastEntry = ENDPOINT;
    super.initHashTable(expectedSize);
    links = new long[expectedSize];
    Arrays.fill(links, -1);
  }

  @Override
  void resizeEntries(int newEntries) {
    int oldEntries = links.length;
    links = Arrays.copyOf(links, newEntries);
    if (oldEntries < newEntries) {
      Arrays.fill(links, oldEntries, newEntries, -1);
    }
    super.resizeEntries(newEntries);
  }

  @Override
  void initEntry(int newEntryIndex, E element, int hash, int occurrences, int nextInBucket) {
    super.initEntry(newEntryIndex, element, hash, occurrences, nextInBucket);
    succeeds(lastEntry, newEntryIndex);
    succeeds(newEntryIndex, ENDPOINT);
  }

  @Override
  void moveEntry(int dstEntry) {
    int srcEntry = distinctElements() - 1;
    succeeds(getPredecessor(dstEntry), getSuccessor(dstEntry));
    if (dstEntry < srcEntry) {
      succeeds(getPredecessor(srcEntry), dstEntry);
      succeeds(dstEntry, getSuccessor(srcEntry));
    }
    links[srcEntry] = -1;
    super.moveEntry(dstEntry);
  }

  @Override
  public void clear() {
    Arrays.fill(links, 0, distinctElements(), -1);
    super.clear();
    firstEntry = ENDPOINT;
    lastEntry = ENDPOINT;
  }

  @Override
  Iterator<Entry<E>> entryIterator() {
    return new Iterator<Entry<E>>() {
      int next = firstEntry;
      int toRemove = -1;
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
      public Multiset.Entry<E> next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        toRemove = next;
        Multiset.Entry<E> result = getEntry(next);
        next = getSuccessor(next);
        assert next != -1;
        return result;
      }

      @Override
      public void remove() {
        checkForConcurrentModification();
        Iterators.checkRemove(toRemove != -1);
        removeAllOccurrences(elements[toRemove]);
        if (next == distinctElements()) {
          next = toRemove;
        }
        toRemove = -1;
        expectedModCount = modCount;
      }
    };
  }

  /**
   * @serialData the number of distinct elements, the first element, its count, the second element,
   *             its count, and so on
   */
  @GwtIncompatible("java.io.ObjectOutputStream")
  private void writeObject(ObjectOutputStream stream) throws IOException {
    stream.defaultWriteObject();
    Serialization.writeMultiset(this, stream);
  }

  @GwtIncompatible("java.io.ObjectInputStream")
  private void readObject(ObjectInputStream stream)
      throws IOException, ClassNotFoundException {
    stream.defaultReadObject();
    int distinctElements = Serialization.readCount(stream);
    initHashTable(Math.max(2, distinctElements));
    Serialization.populateMultiset(this, stream, distinctElements);
  }

  @GwtIncompatible("not needed in emulated source") private static final long serialVersionUID = 0;
}
