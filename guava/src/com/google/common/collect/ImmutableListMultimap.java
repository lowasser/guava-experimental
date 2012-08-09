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

import com.google.common.annotations.Beta;
import com.google.common.annotations.GwtCompatible;
import com.google.common.annotations.GwtIncompatible;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;

import javax.annotation.Nullable;

/**
 * An immutable {@link ListMultimap} with reliable user-specified key and value
 * iteration order. Does not permit null keys or values.
 *
 * <p>Unlike {@link Multimaps#unmodifiableListMultimap(ListMultimap)}, which is
 * a <i>view</i> of a separate multimap which can still change, an instance of
 * {@code ImmutableListMultimap} contains its own data and will <i>never</i>
 * change. {@code ImmutableListMultimap} is convenient for
 * {@code public static final} multimaps ("constant multimaps") and also lets
 * you easily make a "defensive copy" of a multimap provided to your class by
 * a caller.
 *
 * <p><b>Note:</b> Although this class is not final, it cannot be subclassed as
 * it has no public or protected constructors. Thus, instances of this class
 * are guaranteed to be immutable.
 *
 * <p>See the Guava User Guide article on <a href=
 * "http://code.google.com/p/guava-libraries/wiki/ImmutableCollectionsExplained">
 * immutable collections</a>.
 *
 * @author Jared Levy
 * @since 2.0 (imported from Google Collections Library)
 */
@GwtCompatible(serializable = true, emulated = true)
public class ImmutableListMultimap<K, V>
    extends ImmutableMultimap<K, V>
    implements ListMultimap<K, V> {
  
  private static final ImmutableListMultimap<Object, Object> EMPTY =
      new ImmutableListMultimap<Object, Object>(
          ImmutableSet.of(), ImmutableList.of(), new int[] {0});

  /** Returns the empty multimap. */
  // Casting is safe because the multimap will never hold any elements.
  @SuppressWarnings("unchecked")
  public static <K, V> ImmutableListMultimap<K, V> of() {
    return (ImmutableListMultimap<K, V>) EMPTY;
  }

  /**
   * Returns an immutable multimap containing a single entry.
   */
  public static <K, V> ImmutableListMultimap<K, V> of(K k1, V v1) {
    ImmutableListMultimap.Builder<K, V> builder
        = ImmutableListMultimap.builder();
    builder.put(k1, v1);
    return builder.build();
  }

  /**
   * Returns an immutable multimap containing the given entries, in order.
   */
  public static <K, V> ImmutableListMultimap<K, V> of(K k1, V v1, K k2, V v2) {
    ImmutableListMultimap.Builder<K, V> builder
        = ImmutableListMultimap.builder();
    builder.put(k1, v1);
    builder.put(k2, v2);
    return builder.build();
  }

  /**
   * Returns an immutable multimap containing the given entries, in order.
   */
  public static <K, V> ImmutableListMultimap<K, V> of(
      K k1, V v1, K k2, V v2, K k3, V v3) {
    ImmutableListMultimap.Builder<K, V> builder
        = ImmutableListMultimap.builder();
    builder.put(k1, v1);
    builder.put(k2, v2);
    builder.put(k3, v3);
    return builder.build();
  }

  /**
   * Returns an immutable multimap containing the given entries, in order.
   */
  public static <K, V> ImmutableListMultimap<K, V> of(
      K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
    ImmutableListMultimap.Builder<K, V> builder
        = ImmutableListMultimap.builder();
    builder.put(k1, v1);
    builder.put(k2, v2);
    builder.put(k3, v3);
    builder.put(k4, v4);
    return builder.build();
  }

  /**
   * Returns an immutable multimap containing the given entries, in order.
   */
  public static <K, V> ImmutableListMultimap<K, V> of(
      K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
    ImmutableListMultimap.Builder<K, V> builder
        = ImmutableListMultimap.builder();
    builder.put(k1, v1);
    builder.put(k2, v2);
    builder.put(k3, v3);
    builder.put(k4, v4);
    builder.put(k5, v5);
    return builder.build();
  }

  // looking for of() with > 5 entries? Use the builder instead.

  /**
   * Returns a new builder. The generated builder is equivalent to the builder
   * created by the {@link Builder} constructor.
   */
  public static <K, V> Builder<K, V> builder() {
    return new Builder<K, V>();
  }

  /**
   * A builder for creating immutable {@code ListMultimap} instances, especially
   * {@code public static final} multimaps ("constant multimaps"). Example:
   * <pre>   {@code
   *
   *   static final Multimap<String, Integer> STRING_TO_INTEGER_MULTIMAP =
   *       new ImmutableListMultimap.Builder<String, Integer>()
   *           .put("one", 1)
   *           .putAll("several", 1, 2, 3)
   *           .putAll("many", 1, 2, 3, 4, 5)
   *           .build();}</pre>
   *
   * Builder instances can be reused; it is safe to call {@link #build} multiple
   * times to build multiple multimaps in series. Each multimap contains the
   * key-value mappings in the previously created multimaps.
   *
   * @since 2.0 (imported from Google Collections Library)
   */
  public static final class Builder<K, V>
      extends ImmutableMultimap.Builder<K, V> {
    /**
     * Creates a new builder. The returned builder is equivalent to the builder
     * generated by {@link ImmutableListMultimap#builder}.
     */
    public Builder() {}

    @Override public Builder<K, V> put(K key, V value) {
      super.put(key, value);
      return this;
    }

    /**
     * {@inheritDoc}
     *
     * @since 11.0
     */
    @Override public Builder<K, V> put(
        Entry<? extends K, ? extends V> entry) {
      super.put(entry);
      return this;
    }

    @Override public Builder<K, V> putAll(K key, Iterable<? extends V> values) {
      super.putAll(key, values);
      return this;
    }

    @Override public Builder<K, V> putAll(K key, V... values) {
      super.putAll(key, values);
      return this;
    }

    @Override public Builder<K, V> putAll(
        Multimap<? extends K, ? extends V> multimap) {
      super.putAll(multimap);
      return this;
    }

    /**
     * {@inheritDoc}
     *
     * @since 8.0
     */
    @Beta @Override
    public Builder<K, V> orderKeysBy(Comparator<? super K> keyComparator) {
      super.orderKeysBy(keyComparator);
      return this;
    }

    /**
     * {@inheritDoc}
     *
     * @since 8.0
     */
    @Beta @Override
    public Builder<K, V> orderValuesBy(Comparator<? super V> valueComparator) {
      super.orderValuesBy(valueComparator);
      return this;
    }

    /**
     * Returns a newly-created immutable list multimap.
     */
    @Override public ImmutableListMultimap<K, V> build() {
      return (ImmutableListMultimap<K, V>) super.build();
    }
  }
  
  // These constants allow the deserialization code to set final fields. This
  // holder class makes sure they are not initialized unless an instance is
  // deserialized.
  @GwtIncompatible("java serialization is not supported")
  static class FieldSettersHolder {
    static final Serialization.FieldSetter<ImmutableListMultimap>
        KEY_SET_FIELD_SETTER = Serialization.getFieldSetter(
        ImmutableListMultimap.class, "keySet");
    static final Serialization.FieldSetter<ImmutableListMultimap>
        VALUES_FIELD_SETTER = Serialization.getFieldSetter(
        ImmutableListMultimap.class, "values");
    static final Serialization.FieldSetter<ImmutableListMultimap>
        KEY_OFFSETS_FIELD_SETTER = Serialization.getFieldSetter(
        ImmutableListMultimap.class, "keyOffsets");
  }
  
  /**
   * We pack all the value lists into one big list.  The values associated with
   * keySet.asList().get(i) are stored in values.subList(keyOffsets[i], keyOffsets[i+1]).
   * This reduces the per-key overhead by approximately half, and limits the number of
   * objects created in the ImmutableListMultimap implementation to a constant.
   */
  private transient final ImmutableSet<K> keySet;
  private transient final ImmutableList<V> values;
  private transient final int[] keyOffsets;

  /**
   * Returns an immutable multimap containing the same mappings as {@code
   * multimap}. The generated multimap's key and value orderings correspond to
   * the iteration ordering of the {@code multimap.asMap()} view.
   *
   * <p>Despite the method name, this method attempts to avoid actually copying
   * the data when it is safe to do so. The exact circumstances under which a
   * copy will or will not be performed are undocumented and subject to change.
   *
   * @throws NullPointerException if any key or value in {@code multimap} is
   *         null
   */
  public static <K, V> ImmutableListMultimap<K, V> copyOf(
      Multimap<? extends K, ? extends V> multimap) {
    if (multimap.isEmpty()) {
      return of();
    }

    if (multimap instanceof ImmutableListMultimap) {
      @SuppressWarnings("unchecked") // safe since multimap is not writable
      ImmutableListMultimap<K, V> kvMultimap
          = (ImmutableListMultimap<K, V>) multimap;
      if (!kvMultimap.isPartialView()) {
        return kvMultimap;
      }
    }

    int nKeys = multimap.asMap().size();
    ImmutableSet.Builder<K> keySetBuilder = new ImmutableSet.Builder<K>(nKeys);
    int[] keyOffsets = new int[nKeys + 1];
    List<V> values = Lists.newArrayListWithCapacity(multimap.size());
    int keyIndex = 0;
    for (Entry<? extends K, ? extends Collection<? extends V>> entry
        : multimap.asMap().entrySet()) {
      keySetBuilder.add(entry.getKey());
      keyOffsets[keyIndex++] = values.size();
      values.addAll(entry.getValue());
    }
    keyOffsets[nKeys] = values.size();
    return new ImmutableListMultimap<K, V>(
        keySetBuilder.build(), ImmutableList.copyOf(values), keyOffsets);
  }

  ImmutableListMultimap(
      ImmutableSet<K> keySet,
      ImmutableList<V> values,
      int[] keyOffsets) {
    super(values.size());
    this.keySet = keySet;
    this.values = values;
    this.keyOffsets = keyOffsets;
  }
  
  // views

  /**
   * Returns an immutable list of the values for the given key.  If no mappings
   * in the multimap have the provided key, an empty immutable list is
   * returned. The values are in the same order as the parameters used to build
   * this multimap.
   */
  @Override public ImmutableList<V> get(@Nullable K key) {
    int keyIndex = keySet.indexOf(key);
    if (keyIndex == -1) {
      return ImmutableList.of();
    } else {
      return values.subList(keyOffsets[keyIndex], keyOffsets[keyIndex + 1]);
    }
  }

  @Override
  boolean isPartialView() {
    return keySet.isPartialView() || values.isPartialView();
  }

  @Override
  public boolean containsKey(@Nullable Object key) {
    return keySet.contains(key);
  }

  @Override
  public boolean containsValue(@Nullable Object value) {
    return values.contains(value);
  }
  
  @Override
  public ImmutableSet<K> keySet() {
    return keySet;
  }

  @Override
  public ImmutableCollection<V> values() {
    return values;
  }
  
  // TODO(lowasser): consider writing specialized keys() multiset

  private transient ImmutableMap<K, Collection<V>> asMap;

  @Override
  public ImmutableMap<K, Collection<V>> asMap() {
    ImmutableMap<K, Collection<V>> result = asMap;
    return (result == null) ? asMap = new AsMap() : result;
  }
  
  private final class AsMap extends ImmutableMap<K, Collection<V>> {
    @Override
    public int size() {
      return keySet.size();
    }

    @Override
    public Collection<V> get(@Nullable Object key) {
      int keyIndex = keySet.indexOf(key);
      if (keyIndex == -1) {
        return null;
      } else {
        return values.subList(keyOffsets[keyIndex], keyOffsets[keyIndex + 1]);
      }
    }

    @Override
    ImmutableSet<Entry<K, Collection<V>>> createEntrySet() {
      return new ImmutableMapEntrySet<K, Collection<V>>() {
        @Override
        ImmutableMap<K, Collection<V>> map() {
          return AsMap.this;
        }

        @Override
        public UnmodifiableIterator<Entry<K, Collection<V>>> iterator() {
          final ImmutableList<K> keyList = keySet.asList();
          return new AbstractIndexedListIterator<Entry<K, Collection<V>>>(size()) {
            @Override
            protected Entry<K, Collection<V>> get(int index) {
              return Maps.immutableEntry(
                  keyList.get(index),
                  (Collection<V>) values.subList(keyOffsets[index], keyOffsets[index + 1]));
            }
          };
        }
      };
    }

    @Override
    public ImmutableSet<K> keySet() {
      return keySet;
    }

    @Override
    boolean isPartialView() {
      return ImmutableListMultimap.this.isPartialView();
    }
    
    Object writeReplace() {
      return new AsMapSerializedForm(ImmutableListMultimap.this);
    }
  }
  
  private static final class AsMapSerializedForm implements Serializable {
    private final ImmutableListMultimap<?, ?> multimap;
    
    AsMapSerializedForm(ImmutableListMultimap<?, ?> multimap) {
      this.multimap = multimap;
    }

    Object readResolve() {
      return multimap.asMap();
    }
  }

  private transient ImmutableListMultimap<V, K> inverse;

  /**
   * {@inheritDoc}
   *
   * <p>Because an inverse of a list multimap can contain multiple pairs with
   * the same key and value, this method returns an {@code
   * ImmutableListMultimap} rather than the {@code ImmutableMultimap} specified
   * in the {@code ImmutableMultimap} class.
   *
   * @since 11
   */
  @Beta
  @Override
  public ImmutableListMultimap<V, K> inverse() {
    ImmutableListMultimap<V, K> result = inverse;
    return (result == null) ? (inverse = invert()) : result;
  }

  private ImmutableListMultimap<V, K> invert() {
    Builder<V, K> builder = builder();
    for (Entry<K, V> entry : entries()) {
      builder.put(entry.getValue(), entry.getKey());
    }
    ImmutableListMultimap<V, K> invertedMultimap = builder.build();
    invertedMultimap.inverse = this;
    return invertedMultimap;
  }

  /**
   * Guaranteed to throw an exception and leave the multimap unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated @Override public ImmutableList<V> removeAll(Object key) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the multimap unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated @Override public ImmutableList<V> replaceValues(
      K key, Iterable<? extends V> values) {
    throw new UnsupportedOperationException();
  }

  /**
   * @serialData number of distinct keys, and then for each distinct key: the
   *     key, the number of values for that key, and the key's values
   */
  @GwtIncompatible("java.io.ObjectOutputStream")
  private void writeObject(ObjectOutputStream stream) throws IOException {
    stream.defaultWriteObject();
    Serialization.writeMultimap(this, stream);
  }

  @GwtIncompatible("java.io.ObjectInputStream")
  private void readObject(ObjectInputStream stream)
      throws IOException, ClassNotFoundException {
    stream.defaultReadObject();
    int keyCount = stream.readInt();
    if (keyCount < 0) {
      throw new InvalidObjectException("Invalid key count " + keyCount);
    }
    ImmutableSet.Builder<Object> keySetBuilder =
        new ImmutableSet.Builder<Object>(keyCount);
    int[] keyOffsets = new int[keyCount + 1];
    ImmutableList.Builder<Object> valuesBuilder = ImmutableList.builder();
    int offset = 0;

    for (int i = 0; i < keyCount; i++) {
      Object key = stream.readObject();
      int valueCount = stream.readInt();
      if (valueCount <= 0) {
        throw new InvalidObjectException("Invalid value count " + valueCount);
      }

      keySetBuilder.add(key);
      keyOffsets[i] = offset;
      for (int j = 0; j < valueCount; j++) {
        valuesBuilder.add(stream.readObject());
      }
      offset += valueCount;
    }
    keyOffsets[keyCount] = offset;
    FieldSettersHolder.KEY_SET_FIELD_SETTER.set(this, keySetBuilder.build());
    FieldSettersHolder.KEY_OFFSETS_FIELD_SETTER.set(this, keyOffsets);
    FieldSettersHolder.VALUES_FIELD_SETTER.set(this, valuesBuilder.build());
    ImmutableMultimap.FieldSettersHolder.SIZE_FIELD_SETTER.set(this, offset);
  }
  
  Object readResolve() {
    if (keySet.isEmpty()) {
      return of();
    } else {
      return this;
    }
  }

  @GwtIncompatible("Not needed in emulated source")
  private static final long serialVersionUID = 0;
}
