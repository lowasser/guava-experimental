/*
 * Copyright (C) 2011 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.common.collect;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.GwtCompatible;

import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * This class provides a skeletal implementation of the {@link SortedMultiset} interface.
 * 
 * <p>
 * The {@link #count} and {@link #size} implementations all iterate across the set returned by
 * {@link Multiset#entrySet()}, as do many methods acting on the set returned by
 * {@link #elementSet()}. Override those methods for better performance.
 * 
 * @author Louis Wasserman
 */
@GwtCompatible
abstract class AbstractSortedMultiset<E> extends AbstractMultiset<E> implements SortedMultiset<E> {
  

  @GwtTransient
  final Comparator<? super E> comparator;

  // needed for serialization
  @SuppressWarnings("unchecked")
  AbstractSortedMultiset() {
    this((Comparator) Ordering.natural());
  }

  AbstractSortedMultiset(Comparator<? super E> comparator) {
    this.comparator = checkNotNull(comparator);
  }

  @Override
  public NavigableSet<E> elementSet() {
    return (NavigableSet<E>) super.elementSet();
  }

  @Override
  NavigableSet<E> createElementSet() {
    return new AbstractNavigableSet<E>() {

      @Override
      public Iterator<E> iterator() {
        return Multisets.elementIterator(entrySet().iterator());
      }

      @Override
      public Iterator<E> descendingIterator() {
        return Multisets.elementIterator(descendingEntryIterator());
      }

      @Override
      public NavigableSet<E> subSet(
          E fromElement,
          boolean fromInclusive,
          E toElement,
          boolean toInclusive) {
        return subMultiset(
            fromElement,
            BoundType.forBoolean(fromInclusive),
            toElement,
            BoundType.forBoolean(toInclusive)).elementSet();
      }

      @Override
      public NavigableSet<E> headSet(E toElement, boolean inclusive) {
        return headMultiset(toElement, BoundType.forBoolean(inclusive)).elementSet();
      }

      @Override
      public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
        return tailMultiset(fromElement, BoundType.forBoolean(inclusive)).elementSet();
      }

      @Override
      public Comparator<? super E> comparator() {
        return AbstractSortedMultiset.this.comparator();
      }

      @Override
      public int size() {
        return AbstractSortedMultiset.this.distinctElements();
      }
    };
  }

  @Override
  public Comparator<? super E> comparator() {
    return comparator;
  }

  @Override
  public Entry<E> firstEntry() {
    Iterator<Entry<E>> entryIterator = entryIterator();
    return entryIterator.hasNext() ? entryIterator.next() : null;
  }

  @Override
  public Entry<E> lastEntry() {
    Iterator<Entry<E>> entryIterator = descendingEntryIterator();
    return entryIterator.hasNext() ? entryIterator.next() : null;
  }

  @Override
  public Entry<E> pollFirstEntry() {
    Iterator<Entry<E>> entryIterator = entryIterator();
    if (entryIterator.hasNext()) {
      Entry<E> result = entryIterator.next();
      result = Multisets.immutableEntry(result.getElement(), result.getCount());
      entryIterator.remove();
      return result;
    }
    return null;
  }

  @Override
  public Entry<E> pollLastEntry() {
    Iterator<Entry<E>> entryIterator = descendingEntryIterator();
    if (entryIterator.hasNext()) {
      Entry<E> result = entryIterator.next();
      result = Multisets.immutableEntry(result.getElement(), result.getCount());
      entryIterator.remove();
      return result;
    }
    return null;
  }

  @Override
  public SortedMultiset<E> subMultiset(@Nullable E fromElement, BoundType fromBoundType,
      @Nullable E toElement, BoundType toBoundType) {
    // These are checked elsewhere, but NullPointerTester wants them checked eagerly.
    checkNotNull(fromBoundType);
    checkNotNull(toBoundType);
    return tailMultiset(fromElement, fromBoundType).headMultiset(toElement, toBoundType);
  }

  abstract Iterator<Entry<E>> descendingEntryIterator();

  Iterator<E> descendingIterator() {
    return Multisets.iteratorImpl(descendingMultiset());
  }

  private transient SortedMultiset<E> descendingMultiset;

  @Override
  public SortedMultiset<E> descendingMultiset() {
    SortedMultiset<E> result = descendingMultiset;
    return (result == null) ? descendingMultiset = createDescendingMultiset() : result;
  }

  SortedMultiset<E> createDescendingMultiset() {
    return new AbstractSortedMultiset.DescendingMultiset<E>() {
      @Override
      SortedMultiset<E> forwardMultiset() {
        return AbstractSortedMultiset.this;
      }

      @Override
      Iterator<Entry<E>> entryIterator() {
        return descendingEntryIterator();
      }

      @Override
      public Iterator<E> iterator() {
        return descendingIterator();
      }
    };
  }
  
  /**
   * A skeleton implementation of a descending multiset.  Only needs
   * {@code forwardMultiset()} and {@code entryIterator()}.
   */
  static abstract class DescendingMultiset<E> extends ForwardingMultiset<E>
      implements SortedMultiset<E> {
    abstract SortedMultiset<E> forwardMultiset();
  
    private transient Comparator<? super E> comparator;
  
    @Override public Comparator<? super E> comparator() {
      Comparator<? super E> result = comparator;
      if (result == null) {
        return comparator =
            Ordering.from(forwardMultiset().comparator()).<E>reverse();
      }
      return result;
    }
  
    @Override public NavigableSet<E> elementSet() {
      return forwardMultiset().elementSet().descendingSet();
    }
  
    @Override public Entry<E> pollFirstEntry() {
      return forwardMultiset().pollLastEntry();
    }
  
    @Override public Entry<E> pollLastEntry() {
      return forwardMultiset().pollFirstEntry();
    }
  
    @Override public SortedMultiset<E> headMultiset(E toElement,
        BoundType boundType) {
      return forwardMultiset().tailMultiset(toElement, boundType)
          .descendingMultiset();
    }
  
    @Override public SortedMultiset<E> subMultiset(E fromElement,
        BoundType fromBoundType, E toElement, BoundType toBoundType) {
      return forwardMultiset().subMultiset(toElement, toBoundType, fromElement,
          fromBoundType).descendingMultiset();
    }
  
    @Override public SortedMultiset<E> tailMultiset(E fromElement,
        BoundType boundType) {
      return forwardMultiset().headMultiset(fromElement, boundType)
          .descendingMultiset();
    }
  
    @Override protected Multiset<E> delegate() {
      return forwardMultiset();
    }
  
    @Override public SortedMultiset<E> descendingMultiset() {
      return forwardMultiset();
    }
  
    @Override public Entry<E> firstEntry() {
      return forwardMultiset().lastEntry();
    }
  
    @Override public Entry<E> lastEntry() {
      return forwardMultiset().firstEntry();
    }
  
    abstract Iterator<Entry<E>> entryIterator();
  
    private transient Set<Entry<E>> entrySet;
  
    @Override public Set<Entry<E>> entrySet() {
      Set<Entry<E>> result = entrySet;
      return (result == null) ? entrySet = createEntrySet() : result;
    }
  
    Set<Entry<E>> createEntrySet() {
      return new Multisets.EntrySet<E>() {
        @Override Multiset<E> multiset() {
          return DescendingMultiset.this;
        }
  
        @Override public Iterator<Entry<E>> iterator() {
          return entryIterator();
        }
  
        @Override public int size() {
          return forwardMultiset().entrySet().size();
        }
      };
    }
  
    @Override public Iterator<E> iterator() {
      return Multisets.iteratorImpl(this);
    }
  
    @Override public Object[] toArray() {
      return standardToArray();
    }
  
    @Override public <T> T[] toArray(T[] array) {
      return standardToArray(array);
    }
  
    @Override public String toString() {
      return entrySet().toString();
    }
  }
}
