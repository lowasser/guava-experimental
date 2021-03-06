/*
 * Copyright (C) 2009 The Guava Authors
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
import static com.google.common.collect.Iterables.getOnlyElement;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * GWT emulation of {@link ImmutableMap}.  For non sorted maps, it is a thin
 * wrapper around {@link java.util.Collections#emptyMap()}, {@link
 * Collections#singletonMap(Object, Object)} and {@link java.util.LinkedHashMap}
 * for empty, singleton and regular maps respectively.  For sorted maps, it's
 * a thin wrapper around {@link java.util.TreeMap}.
 *
 * @see ImmutableSortedMap
 *
 * @author Hayward Chan
 */
public abstract class ImmutableMap<K, V> implements Map<K, V>, Serializable {

  ImmutableMap() {}

  // Casting to any type is safe because the set will never hold any elements.
  @SuppressWarnings("unchecked")
  public static <K, V> ImmutableMap<K, V> of() {
    return (ImmutableMap<K, V>) EmptyImmutableMap.INSTANCE;
  }

  public static <K, V> ImmutableMap<K, V> of(K k1, V v1) {
    return new SingletonImmutableMap<K, V>(
        checkNotNull(k1), checkNotNull(v1));
  }

  public static <K, V> ImmutableMap<K, V> of(K k1, V v1, K k2, V v2) {
    return new RegularImmutableMap<K, V>(entryOf(k1, v1), entryOf(k2, v2));
  }

  public static <K, V> ImmutableMap<K, V> of(
      K k1, V v1, K k2, V v2, K k3, V v3) {
    return new RegularImmutableMap<K, V>(
        entryOf(k1, v1), entryOf(k2, v2), entryOf(k3, v3));
  }

  public static <K, V> ImmutableMap<K, V> of(
      K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
    return new RegularImmutableMap<K, V>(
        entryOf(k1, v1), entryOf(k2, v2), entryOf(k3, v3), entryOf(k4, v4));
  }

  public static <K, V> ImmutableMap<K, V> of(
      K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
    return new RegularImmutableMap<K, V>(entryOf(k1, v1),
        entryOf(k2, v2), entryOf(k3, v3), entryOf(k4, v4), entryOf(k5, v5));
  }

  // looking for of() with > 5 entries? Use the builder instead.

  public static <K, V> Builder<K, V> builder() {
    return new Builder<K, V>();
  }

  static <K, V> Entry<K, V> entryOf(K key, V value) {
    return Maps.immutableEntry(checkNotNull(key), checkNotNull(value));
  }

  public static class Builder<K, V> {
    final List<Entry<K, V>> entries = Lists.newArrayList();

    public Builder() {}

    public Builder<K, V> put(K key, V value) {
      entries.add(entryOf(key, value));
      return this;
    }

    public Builder<K, V> put(Entry<? extends K, ? extends V> entry) {
      if (entry instanceof ImmutableEntry) {
        checkNotNull(entry.getKey());
        checkNotNull(entry.getValue());
        @SuppressWarnings("unchecked") // all supported methods are covariant
        Entry<K, V> immutableEntry = (Entry<K, V>) entry;
        entries.add(immutableEntry);
      } else {
        entries.add(entryOf((K) entry.getKey(), (V) entry.getValue()));
      }
      return this;
    }

    public Builder<K, V> putAll(Map<? extends K, ? extends V> map) {
      for (Entry<? extends K, ? extends V> entry : map.entrySet()) {
        put(entry.getKey(), entry.getValue());
      }
      return this;
    }

    public ImmutableMap<K, V> build() {
      return fromEntryList(entries);
    }

    private static <K, V> ImmutableMap<K, V> fromEntryList(
        List<Entry<K, V>> entries) {
      int size = entries.size();
      switch (size) {
        case 0:
          return of();
        case 1:
          Entry<K, V> entry = getOnlyElement(entries);
          return new SingletonImmutableMap<K, V>(
              entry.getKey(), entry.getValue());
        default:
          @SuppressWarnings("unchecked")
          Entry<K, V>[] entryArray
              = entries.toArray(new Entry[entries.size()]);
          return new RegularImmutableMap<K, V>(entryArray);
      }
    }
  }

  public static <K, V> ImmutableMap<K, V> copyOf(
      Map<? extends K, ? extends V> map) {
    if ((map instanceof ImmutableMap) && !(map instanceof ImmutableSortedMap)) {
      @SuppressWarnings("unchecked") // safe since map is not writable
      ImmutableMap<K, V> kvMap = (ImmutableMap<K, V>) map;
      return kvMap;
    }

    int size = map.size();
    switch (size) {
      case 0:
        return of();
      case 1:
        Entry<? extends K, ? extends V> entry
            = getOnlyElement(map.entrySet());
        return ImmutableMap.<K, V>of(entry.getKey(), entry.getValue());
      default:
        Map<K, V> orderPreservingCopy = Maps.newLinkedHashMap();
        for (Entry<? extends K, ? extends V> e : map.entrySet()) {
          orderPreservingCopy.put(
              checkNotNull(e.getKey()), checkNotNull(e.getValue()));
        }
        return new RegularImmutableMap<K, V>(orderPreservingCopy);
    }
  }

  abstract boolean isPartialView();

  public final V put(K k, V v) {
    throw new UnsupportedOperationException();
  }

  public final V remove(Object o) {
    throw new UnsupportedOperationException();
  }

  public final void putAll(Map<? extends K, ? extends V> map) {
    throw new UnsupportedOperationException();
  }

  public final void clear() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isEmpty() {
    return size() == 0;
  }

  @Override
  public boolean containsKey(@Nullable Object key) {
    return get(key) != null;
  }

  @Override
  public boolean containsValue(@Nullable Object value) {
    return value != null && Maps.containsValueImpl(this, value);
  }

  private transient ImmutableSet<Entry<K, V>> cachedEntrySet = null;

  public final ImmutableSet<Entry<K, V>> entrySet() {
    if (cachedEntrySet != null) {
      return cachedEntrySet;
    }
    return cachedEntrySet = createEntrySet();
  }

  abstract ImmutableSet<Entry<K, V>> createEntrySet();

  private transient ImmutableSet<K> cachedKeySet = null;

  public ImmutableSet<K> keySet() {
    if (cachedKeySet != null) {
      return cachedKeySet;
    }
    return cachedKeySet = createKeySet();
  }

  ImmutableSet<K> createKeySet() {
    return new ImmutableMapKeySet<K, V>(entrySet()) {
      @Override ImmutableMap<K, V> map() {
        return ImmutableMap.this;
      }
    };
  }

  private transient ImmutableCollection<V> cachedValues = null;

  public ImmutableCollection<V> values() {
    if (cachedValues != null) {
      return cachedValues;
    }
    return cachedValues = createValues();
  }

  ImmutableCollection<V> createValues() {
    return new ImmutableMapValues<K, V>() {
      @Override ImmutableMap<K, V> map() {
        return ImmutableMap.this;
      }
    };
  }

  @Override public boolean equals(@Nullable Object object) {
    return Maps.equalsImpl(this, object);
  }

  @Override public int hashCode() {
    // not caching hash code since it could change if map values are mutable
    // in a way that modifies their hash codes
    return entrySet().hashCode();
  }

  @Override public String toString() {
    return Maps.toStringImpl(this);
  }
}
