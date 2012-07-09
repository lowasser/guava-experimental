/*
 * Copyright (C) 2010 The Guava Authors
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.GwtCompatible;

import java.util.List;

import javax.annotation.Nullable;

/**
 * A transforming wrapper around an ImmutableList. For internal use only. {@link
 * #transform(Object)} must be functional.
 *
 * @author Louis Wasserman
 */
@GwtCompatible
@SuppressWarnings("serial") // uses writeReplace(), not default serialization
abstract class TransformedImmutableList<D, E> extends ImmutableList<E> {
  private class TransformedView extends TransformedImmutableList<D, E> {
    TransformedView(ImmutableList<D> backingList) {
      super(backingList);
    }

    @Override
    E transform(D d) {
      return TransformedImmutableList.this.transform(d);
    }
  }

  private transient final ImmutableList<D> backingList;

  TransformedImmutableList(ImmutableList<D> backingList) {
    this.backingList = checkNotNull(backingList);
  }

  abstract E transform(D d);

  public E get(int index) {
    return transform(backingList.get(index));
  }

  public int size() {
    return backingList.size();
  }

  @Override
  public ImmutableList<E> subList(int fromIndex, int toIndex) {
    return new TransformedView(backingList.subList(fromIndex, toIndex));
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj instanceof List) {
      List<?> list = (List<?>) obj;
      return size() == list.size()
          && Iterators.elementsEqual(iterator(), list.iterator());
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Lists.hashCodeImpl(this);
  }

  @Override
  boolean isPartialView() {
    return true;
  }
}
