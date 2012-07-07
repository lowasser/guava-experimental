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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import javax.annotation.Nullable;

/**
 * A {@code Multiset} implementation with predictable iteration order. Its iterator orders elements
 * according to when the first occurrence of the element was added. When the multiset contains
 * multiple instances of an element, those instances are consecutive in the iteration order. If all
 * occurrences of an element are removed, after which that element is added to the multiset, the
 * element will appear at the end of the iteration.
 * 
 * <p>
 * See the Guava User Guide article on <a href=
 * "http://code.google.com/p/guava-libraries/wiki/NewCollectionTypesExplained#Multiset">
 * {@code Multiset}</a>.
 * 
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

  private transient LinkedEntry<E> headerEntry;

  LinkedHashMultiset(int expectedElements) {
    super(expectedElements);
    headerEntry = new LinkedEntry<E>(null, 0, 0, null);
  }

  private static final class LinkedEntry<E> extends HashEntry<E> {
    LinkedEntry<E> successor;
    LinkedEntry<E> predecessor;

    LinkedEntry(E elem, int smearedHash, int count, @Nullable HashEntry<E> nextInBucket) {
      super(elem, smearedHash, count, nextInBucket);
      this.successor = this;
      this.predecessor = this;
    }
  }

  @Override
  LinkedEntry<E> createEntry(
      @Nullable E element,
      int smearedHash,
      int count,
      HashEntry<E> nextInBucket) {
    LinkedEntry<E> result = new LinkedEntry<E>(element, smearedHash, count, nextInBucket);
    succeeds(headerEntry.predecessor, result);
    succeeds(result, headerEntry);
    return result;
  }

  private static <E> void succeeds(LinkedEntry<E> pred, LinkedEntry<E> succ) {
    pred.successor = succ;
    succ.predecessor = pred;
  }

  @Override
  void deleteEntry(HashEntry<E> entry, HashEntry<E> prev) {
    super.deleteEntry(entry, prev);
    LinkedEntry<E> toDelete = (LinkedEntry<E>) entry;
    succeeds(toDelete.predecessor, toDelete.successor);
  }

  @Override
  Iterator<Entry<E>> entryIterator() {
    return new Iterator<Entry<E>>() {
      LinkedEntry<E> next = headerEntry.successor;
      LinkedEntry<E> toRemove = null;
      int expectedModCount = modCount;
      
      private void checkForComodification() {
        if (modCount != expectedModCount) {
          throw new ConcurrentModificationException();
        }
      }

      @Override
      public boolean hasNext() {
        checkForComodification();
        return next != headerEntry;
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
          next = next.successor;
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
    this.headerEntry = new LinkedEntry<E>(null, 0, 0, null);
    int distinctElements = Serialization.readCount(stream);
    initHashTable(distinctElements);
    Serialization.populateMultiset(this, stream, distinctElements);
  }

  @GwtIncompatible("not needed in emulated source") private static final long serialVersionUID = 0;
}
