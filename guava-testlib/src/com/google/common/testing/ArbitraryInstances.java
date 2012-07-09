/*
 * Copyright (C) 2012 The Guava Authors
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

package com.google.common.testing;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.Beta;
import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.base.Defaults;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalences;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.base.Ticker;
import com.google.common.collect.BiMap;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.Constraint;
import com.google.common.collect.Constraints;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Iterators;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MapConstraint;
import com.google.common.collect.MapConstraints;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.google.common.collect.Ordering;
import com.google.common.collect.PeekingIterator;
import com.google.common.collect.Range;
import com.google.common.collect.Ranges;
import com.google.common.collect.RowSortedTable;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.SortedMapDifference;
import com.google.common.collect.SortedMultiset;
import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import com.google.common.collect.TreeBasedTable;
import com.google.common.collect.TreeMultimap;
import com.google.common.collect.TreeMultiset;
import com.google.common.primitives.Primitives;
import com.google.common.primitives.UnsignedInteger;
import com.google.common.primitives.UnsignedLong;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Comparator;
import java.util.Currency;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * Supplies an arbitrary "default" instance for a wide range of types, often useful in testing
 * utilities.
 * 
 * <p>Covers common types defined in {@code java.lang}, {@code java.lang.reflect}, {@code java.io},
 * {@code java.nio}, {@code java.math}, {@code java.util}, {@code java.util.concurrent},
 * {@code java.util.regex}, {@code com.google.common.base}, {@code com.google.common.collect}
 * and {@code com.google.common.primitives}. In addition, any public class that exposes a public
 * parameter-less constructor will be "new"d and returned.
 * 
 * <p>All default instances returned by {@link #get} are generics-safe. Clients won't get type
 * errors for using {@code get(Comparator.class)} as a {@code Comparator<Foo>}, for example.
 * Immutable empty instances are returned for collection types; {@code ""} for string;
 * {@code 0} for number types; reasonable default instance for other stateless types. For mutable
 * types, a fresh instance is created each time {@code get()} is called.
 *
 * @author Kevin Bourrillion
 * @author Ben Yu
 * @since 12.0
 */
@Beta
public final class ArbitraryInstances {

  // Compare by toString() to satisfy 2 properties:
  // 1. compareTo(null) should throw NullPointerException
  // 2. the order is deterministic and easy to understand, for debugging purpose.
  private static final Comparable<Object> BY_TO_STRING = new Comparable<Object>() {
    @Override public int compareTo(Object o) {
      return toString().compareTo(o.toString());
    }
    @Override public String toString() {
      return "BY_TO_STRING";
    }
  };

  // Always equal is a valid total ordering. And it works for any Object.
  private static final Ordering<Object> ALWAYS_EQUAL = new Ordering<Object>() {
    @Override public int compare(Object o1, Object o2) {
      return 0;
    }
    @Override public String toString() {
      return "ALWAYS_EQUAL";
    }
  };

  private static final ClassToInstanceMap<Object> DEFAULTS = ImmutableClassToInstanceMap.builder()
      // primitives
      .put(Number.class, 0)
      .put(UnsignedInteger.class, UnsignedInteger.ZERO)
      .put(UnsignedLong.class, UnsignedLong.ZERO)
      .put(BigInteger.class, BigInteger.ZERO)
      .put(BigDecimal.class, BigDecimal.ZERO)
      .put(CharSequence.class, "")
      .put(String.class, "")
      .put(Pattern.class, Pattern.compile(""))
      .put(MatchResult.class, Pattern.compile("").matcher("").toMatchResult())
      .put(TimeUnit.class, TimeUnit.SECONDS)
      .put(Charset.class, Charsets.UTF_8)
      .put(Currency.class, Currency.getInstance(Locale.US))
      .put(Locale.class, Locale.US)
      // common.base
      .put(CharMatcher.class, CharMatcher.NONE)
      .put(Joiner.class, Joiner.on(','))
      .put(Splitter.class, Splitter.on(','))
      .put(Optional.class, Optional.absent())
      .put(Predicate.class, Predicates.alwaysTrue())
      .put(Equivalence.class, Equivalences.equals())
      .put(Ticker.class, Ticker.systemTicker())
      // io types
      .put(InputStream.class, new ByteArrayInputStream(new byte[0]))
      .put(ByteArrayInputStream.class, new ByteArrayInputStream(new byte[0]))
      .put(Readable.class, new StringReader(""))
      .put(Reader.class, new StringReader(""))
      .put(StringReader.class, new StringReader(""))
      .put(Buffer.class, ByteBuffer.allocate(0))
      .put(CharBuffer.class, CharBuffer.allocate(0))
      .put(ByteBuffer.class, ByteBuffer.allocate(0))
      .put(ShortBuffer.class, ShortBuffer.allocate(0))
      .put(IntBuffer.class, IntBuffer.allocate(0))
      .put(LongBuffer.class, LongBuffer.allocate(0))
      .put(FloatBuffer.class, FloatBuffer.allocate(0))
      .put(DoubleBuffer.class, DoubleBuffer.allocate(0))
      .put(File.class, new File(""))
      // All collections are immutable empty. So safe for any type parameter.
      .put(Iterator.class, Iterators.emptyIterator())
      .put(PeekingIterator.class, Iterators.peekingIterator(Iterators.emptyIterator()))
      .put(ListIterator.class, ImmutableList.of().listIterator())
      .put(Iterable.class, ImmutableSet.of())
      .put(Collection.class, ImmutableList.of())
      .put(ImmutableCollection.class, ImmutableList.of())
      .put(List.class, ImmutableList.of())
      .put(ImmutableList.class, ImmutableList.of())
      .put(Set.class, ImmutableSet.of())
      .put(ImmutableSet.class, ImmutableSet.of())
      .put(SortedSet.class, ImmutableSortedSet.of())
      .put(ImmutableSortedSet.class, ImmutableSortedSet.of())
      .put(Map.class, ImmutableMap.of())
      .put(ImmutableMap.class, ImmutableMap.of())
      .put(SortedMap.class, ImmutableSortedMap.of())
      .put(ImmutableSortedMap.class, ImmutableSortedMap.of())
      .put(Multimap.class, ImmutableMultimap.of())
      .put(ImmutableMultimap.class, ImmutableMultimap.of())
      .put(ListMultimap.class, ImmutableListMultimap.of())
      .put(ImmutableListMultimap.class, ImmutableListMultimap.of())
      .put(SetMultimap.class, ImmutableSetMultimap.of())
      .put(ImmutableSetMultimap.class, ImmutableSetMultimap.of())
      .put(SortedSetMultimap.class, Multimaps.unmodifiableSortedSetMultimap(TreeMultimap.create()))
      .put(Multiset.class, ImmutableMultiset.of())
      .put(ImmutableMultiset.class, ImmutableMultiset.of())
      .put(SortedMultiset.class, Multisets.unmodifiableSortedMultiset(TreeMultiset.create()))
      .put(BiMap.class, ImmutableBiMap.of())
      .put(ImmutableBiMap.class, ImmutableBiMap.of())
      .put(Table.class, ImmutableTable.of())
      .put(ImmutableTable.class, ImmutableTable.of())
      .put(RowSortedTable.class, Tables.unmodifiableRowSortedTable(TreeBasedTable.create()))
      .put(ClassToInstanceMap.class, ImmutableClassToInstanceMap.builder().build())
      .put(ImmutableClassToInstanceMap.class, ImmutableClassToInstanceMap.builder().build())
      .put(Comparable.class, BY_TO_STRING)
      .put(Comparator.class, ALWAYS_EQUAL)
      .put(Ordering.class, ALWAYS_EQUAL)
      .put(Range.class, Ranges.all())
      .put(Constraint.class, Constraints.notNull())
      .put(MapConstraint.class, MapConstraints.notNull())
      .put(MapDifference.class, Maps.difference(ImmutableMap.of(), ImmutableMap.of()))
      .put(SortedMapDifference.class,
          Maps.difference(ImmutableSortedMap.of(), ImmutableSortedMap.of()))
      // reflect
      .put(AnnotatedElement.class, Object.class)
      .put(GenericDeclaration.class, Object.class)
      .put(Type.class, Object.class)
      // concurrent
      .put(Runnable.class, new Runnable() {
        @Override public void run() {}
      })
      .put(ThreadFactory.class, new ThreadFactory() {
        @Override public Thread newThread(Runnable r) {
          return new Thread(r);
        }
      })
      .put(Executor.class, new Executor() {
        @Override public void execute(Runnable command) {}
      })
      .build();

  /**
   * type -> implementation. Inherently mutable interfaces and abstract classes are mapped to their
   * default implementations and are "new"d upon get().
   */
  private static final ConcurrentMap<Class<?>, Class<?>> implementations = Maps.newConcurrentMap();

  private static <T> void setImplementation(Class<T> type, Class<? extends T> implementation) {
    checkArgument(type != implementation, "Don't register %s to itself!", type);
    checkArgument(!DEFAULTS.containsKey(type),
        "A default value was already registered for %s", type);
    checkArgument(implementations.put(type, implementation) == null,
        "Implementation for %s was already registered", type);
  }

  static {
    setImplementation(Appendable.class, StringBuilder.class);
    setImplementation(Queue.class, LinkedList.class);
    setImplementation(BlockingQueue.class, LinkedBlockingQueue.class);
    setImplementation(ConcurrentMap.class, ConcurrentHashMap.class);
    setImplementation(OutputStream.class, ByteArrayOutputStream.class);
    setImplementation(Writer.class, StringWriter.class);
    setImplementation(PrintStream.class, Mutable.InMemoryPrintStream.class);
    setImplementation(PrintWriter.class, Mutable.InMemoryPrintWriter.class);
    setImplementation(Random.class, Mutable.DeterministicRandom.class);
  }

  @SuppressWarnings("unchecked") // it's a subtype map
  @Nullable
  private static <T> Class<? extends T> getImplementation(Class<T> type) {
    return (Class<? extends T>) implementations.get(type);
  }

  private static final Logger logger = Logger.getLogger(ArbitraryInstances.class.getName());

  /**
   * Returns an arbitrary value for {@code type} as the null value, or {@code null} if empty-ness is
   * unknown for the type.
   */
  @Nullable public static <T> T get(Class<T> type) {
    T defaultValue = DEFAULTS.getInstance(type);
    if (defaultValue != null) {
      return defaultValue;
    }
    Class<? extends T> implementation = getImplementation(type);
    if (implementation != null) {
      return get(implementation);
    }
    if (type.isEnum()) {
      T[] enumConstants = type.getEnumConstants();
      return (enumConstants.length == 0)
          ? null
          : enumConstants[0];
    }
    if (type.isArray()) {
      return createEmptyArray(type);
    }
    T jvmDefault = Defaults.defaultValue(Primitives.unwrap(type));
    if (jvmDefault != null) {
      return jvmDefault;
    }
    if (Modifier.isAbstract(type.getModifiers()) || !Modifier.isPublic(type.getModifiers())) {
      return null;
    }
    final Constructor<T> constructor;
    try {
      constructor = type.getConstructor();
    } catch (NoSuchMethodException e) {
      return null;
    }
    constructor.setAccessible(true); // accessibility check is too slow
    try {
      return constructor.newInstance();
    } catch (InstantiationException impossible) {
      throw new AssertionError(impossible);
    } catch (IllegalAccessException impossible) {
      throw new AssertionError(impossible);
    } catch (InvocationTargetException e) {
      logger.log(Level.WARNING, "Exception while invoking default constructor.", e.getCause());
      return null;
    }
  }

  @SuppressWarnings("unchecked") // same component type means same array type
  private static <T> T createEmptyArray(Class<T> arrayType) {
    return (T) Array.newInstance(arrayType.getComponentType(), 0);
  }

  // Internal implementations for mutable types, with public default constructor that get() needs.
  private static final class Mutable {

    public static final class InMemoryPrintStream extends PrintStream {
      public InMemoryPrintStream() {
        super(new ByteArrayOutputStream());
      }
    }

    public static final class InMemoryPrintWriter extends PrintWriter {
      public InMemoryPrintWriter() {
        super(new StringWriter());
      }
    }

    public static final class DeterministicRandom extends Random {
      @SuppressWarnings("unused") // invoked by reflection
      public DeterministicRandom() {
        super(0);
      }
    }
  }

  private ArbitraryInstances() {}
}
