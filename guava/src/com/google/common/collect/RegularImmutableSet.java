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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.GwtCompatible;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Ints;

import java.util.Arrays;

import javax.annotation.Nullable;

/**
 * Implementation of {@link ImmutableSet} with two or more elements.
 *
 * @author Louis Wasserman
 * @author Kevin Bourrillion
 */
@GwtCompatible(serializable = true, emulated = true)
@SuppressWarnings("serial") // uses writeReplace(), not default serialization
final class RegularImmutableSet<E> extends ImmutableSet<E> {
  final Object[] elements;
  final int[] table;
  private final transient int hashCode;

  // We use power-of-2 tables, and this is the highest int that's a power of 2
  static final int MAX_TABLE_SIZE = Ints.MAX_POWER_OF_TWO;

  // Represents how tightly we can pack things, as a maximum.
  private static final double DESIRED_LOAD_FACTOR = 0.7;

  // If the set has this many elements, it will "max out" the table size
  private static final int CUTOFF =
      (int) Math.floor(MAX_TABLE_SIZE * DESIRED_LOAD_FACTOR);

  /**
   * Returns an array size suitable for the backing array of a hash table that
   * uses open addressing with linear probing in its implementation.  The
   * returned size is the smallest power of two that can hold setSize elements
   * with the desired load factor.
   *
   * <p>Do not call this method with setSize < 2.
   */
  @VisibleForTesting static int chooseTableSize(int setSize) {
    // Correct the size for open addressing to match desired load factor.
    setSize = Math.max(2, setSize);
    if (setSize < CUTOFF) {
      // Round up to the next highest power of 2.
      int tableSize = Integer.highestOneBit(setSize - 1) << 1;
      while (tableSize * DESIRED_LOAD_FACTOR < setSize) {
        tableSize <<= 1;
      }
      return tableSize;
    }

    // The table can't be completely full or we'll get infinite reprobes
    checkArgument(setSize < MAX_TABLE_SIZE, "collection too large");
    
    return MAX_TABLE_SIZE;
  }
  
  static <E> ImmutableSet<E> create(int n, Object[] elements) {
    int tableSize = chooseTableSize(n);
    int mask = tableSize - 1;
    
    int[] hashTable = new int[tableSize];
    Arrays.fill(hashTable, -1);
    
    int nUnique = 0;
    int hashCode = 0;
    for (int i = 0; i < n; i++) {
      Object o = elements[i];
      int hash = o.hashCode();
      
      for (int index = Hashing.smear(hash);; index++) {
        int tableIndex = index & mask;
        // we rotate tableIndex cyclically around the hash table
        // TODO: experiment with different probing algorithms?
        int tableValue = hashTable[tableIndex];
        
        if (tableValue == -1) {
          // open space; insert the new unique element
          int newIndex = nUnique;
          hashTable[tableIndex] = newIndex;
          elements[newIndex] = o;
          nUnique++;
          hashCode += hash;
          break;
        } else if (elements[tableValue].equals(o)) {
          // duplicate element; skip it
          break;
        }
      }
    }
    Arrays.fill(elements, nUnique, n, null);
    
    switch (nUnique) {
      case 0:
        return of();
      case 1: {
        @SuppressWarnings("unchecked")
        E elem = (E) elements[0];
        return of(elem);
      }
      default:
        if (chooseTableSize(nUnique) < tableSize) {
          return create(nUnique, elements);
        }
        
        Object[] uniques;
        if (nUnique < elements.length) {
          uniques = ObjectArrays.arraysCopyOf(elements, nUnique);
        } else {
          uniques = elements;
        }
        
        return new RegularImmutableSet<E>(uniques, hashTable, hashCode);
    }
  }

  RegularImmutableSet(Object[] elements, int[] table, int hashCode) {
    this.elements = elements;
    this.table = table;
    this.hashCode = hashCode;
  }

  @Override public int hashCode() {
    return hashCode;
  }

  @Override boolean isHashCodeFast() {
    return true;
  }

  @Override
  public int size() {
    return elements.length;
  }

  @SuppressWarnings("unchecked")
  @Override
  public UnmodifiableIterator<E> iterator() {
    return (UnmodifiableIterator<E>) Iterators.forArray(elements);
  }

  @Override
  public Object[] toArray() {
    Object[] result = new Object[size()];
    System.arraycopy(elements, 0, result, 0, size());
    return result;
  }

  @Override
  public <T> T[] toArray(T[] other) {
    if (other.length < size()) {
      other = ObjectArrays.newArray(other, size());
    }
    System.arraycopy(elements, 0, other, 0, size());
    if (other.length > size()) {
      other[size()] = null;
    }
    return other;
  }

  @Override
  ImmutableList<E> createAsList() {
    return new ImmutableAsList<E>() {
      @SuppressWarnings("unchecked")
      @Override
      public E get(int index) {
        return (E) elements[index];
      }

      @SuppressWarnings("unchecked")
      @Override
      public UnmodifiableListIterator<E> listIterator(int index) {
        return (UnmodifiableListIterator<E>)
            Iterators.forArray(elements, 0, elements.length, index);
      }

      @Override
      public int indexOf(@Nullable Object object) {
        return RegularImmutableSet.this.indexOf(object);
      }

      @Override
      public int lastIndexOf(@Nullable Object object) {
        return indexOf(object);
      }

      @Override
      ImmutableCollection<E> delegateCollection() {
        return RegularImmutableSet.this;
      }
    };
  }

  @Override
  boolean isPartialView() {
    return false;
  }
}
