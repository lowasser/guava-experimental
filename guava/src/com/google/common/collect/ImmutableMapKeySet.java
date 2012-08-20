/*
 * Copyright (C) 2008 The Guava Authors
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

import java.io.Serializable;
import java.util.Map.Entry;

import javax.annotation.Nullable;

/**
 * {@code keySet()} implementation for {@link ImmutableMap}.
 *
 * @author Jesse Wilson
 * @author Kevin Bourrillion
 */
@GwtCompatible(emulated = true)
abstract class ImmutableMapKeySet<K, V> extends TransformedImmutableSet<Entry<K, V>, K> {
  ImmutableMapKeySet(ImmutableSet<Entry<K, V>> entrySet) {
    super(entrySet);
  }

  ImmutableMapKeySet(ImmutableSet<Entry<K, V>> entrySet, int hashCode) {
    super(entrySet, hashCode);
  }

  abstract ImmutableMap<K, V> map();

  
  @Override
  K transform(Entry<K, V> entry) {
    return entry.getKey();
  }

  
  @Override
  public boolean contains(@Nullable Object object) {
    return map().containsKey(object);
  }

  
  @Override
  boolean isPartialView() {
    return true;
  }

  
  @Override
  ImmutableList<K> createAsList() {
    final ImmutableList<Entry<K, V>> entryList = map().entrySet().asList();
    return new ImmutableAsList<K>() {
      
      public K get(int index) {
        return entryList.get(index).getKey();
      }

      
      @Override
      ImmutableCollection<K> delegateCollection() {
        return ImmutableMapKeySet.this;
      }
    };
  }

  @Override
  @GwtIncompatible("serialization")
  Object writeReplace() {
    return new KeySetSerializedForm<K>(map());
  }

  @GwtIncompatible("serialization")
  private static class KeySetSerializedForm<K> implements Serializable {
    final ImmutableMap<K, ?> map;
    KeySetSerializedForm(ImmutableMap<K, ?> map) {
      this.map = map;
    }
    Object readResolve() {
      return map.keySet();
    }
    private static final long serialVersionUID = 0;
  }
}
