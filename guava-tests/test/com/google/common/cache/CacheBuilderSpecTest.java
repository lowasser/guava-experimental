/*
 * Copyright (C) 2011 The Guava Authors
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

package com.google.common.cache;

import static com.google.common.cache.CacheBuilderSpec.parse;
import static com.google.common.cache.TestingWeighers.constantWeigher;

import com.google.common.base.Suppliers;
import com.google.common.cache.LocalCache.Strength;
import com.google.common.testing.EqualsTester;

import junit.framework.TestCase;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

/**
 * Tests CacheBuilderSpec.
 * TODO(user): tests of a few invalid input conditions, boundary conditions.
 *
 * @author Adam Winer
 */
public class CacheBuilderSpecTest extends TestCase {
  public void testParse_empty() {
    CacheBuilderSpec spec = parse("");
    assertNull(spec.initialCapacity);
    assertNull(spec.maximumSize);
    assertNull(spec.maximumWeight);
    assertNull(spec.concurrencyLevel);
    assertNull(spec.keyStrength);
    assertNull(spec.valueStrength);
    assertNull(spec.writeExpirationTimeUnit);
    assertNull(spec.accessExpirationTimeUnit);
    assertCacheBuilderEquivalence(CacheBuilder.newBuilder(), CacheBuilder.from(spec));
  }

  public void testParse_initialCapacity() {
    CacheBuilderSpec spec = parse("initialCapacity=10");
    assertEquals(10, spec.initialCapacity.intValue());
    assertNull(spec.maximumSize);
    assertNull(spec.maximumWeight);
    assertNull(spec.concurrencyLevel);
    assertNull(spec.keyStrength);
    assertNull(spec.valueStrength);
    assertNull(spec.writeExpirationTimeUnit);
    assertNull(spec.accessExpirationTimeUnit);
    assertCacheBuilderEquivalence(
        CacheBuilder.newBuilder().initialCapacity(10), CacheBuilder.from(spec));
  }

  public void testParse_initialCapacityRepeated() {
    try {
      parse("initialCapacity=10, initialCapacity=20");
      fail("Expected exception");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  public void testParse_maximumSize() {
    CacheBuilderSpec spec = parse("maximumSize=9000");
    assertNull(spec.initialCapacity);
    assertEquals(9000, spec.maximumSize.longValue());
    assertNull(spec.concurrencyLevel);
    assertNull(spec.keyStrength);
    assertNull(spec.valueStrength);
    assertNull(spec.writeExpirationTimeUnit);
    assertNull(spec.accessExpirationTimeUnit);
    assertCacheBuilderEquivalence(
        CacheBuilder.newBuilder().maximumSize(9000), CacheBuilder.from(spec));
  }

  public void testParse_maximumSizeRepeated() {
    try {
      parse("maximumSize=10, maximumSize=20");
      fail("Expected exception");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  public void testParse_maximumWeight() {
    CacheBuilderSpec spec = parse("maximumWeight=9000");
    assertNull(spec.initialCapacity);
    assertEquals(9000, spec.maximumWeight.longValue());
    assertNull(spec.concurrencyLevel);
    assertNull(spec.keyStrength);
    assertNull(spec.valueStrength);
    assertNull(spec.writeExpirationTimeUnit);
    assertNull(spec.accessExpirationTimeUnit);
    assertCacheBuilderEquivalence(
        CacheBuilder.newBuilder().maximumWeight(9000), CacheBuilder.from(spec));
  }

  public void testParse_maximumWeightRepeated() {
    try {
      parse("maximumWeight=10, maximumWeight=20");
      fail("Expected exception");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  public void testParse_maximumSizeAndMaximumWeight() {
    try {
      parse("maximumSize=10, maximumWeight=20");
      fail("Expected exception");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  public void testParse_concurrencyLevel() {
    CacheBuilderSpec spec = parse("concurrencyLevel=32");
    assertNull(spec.initialCapacity);
    assertNull(spec.maximumSize);
    assertNull(spec.maximumWeight);
    assertEquals(32, spec.concurrencyLevel.intValue());
    assertNull(spec.keyStrength);
    assertNull(spec.valueStrength);
    assertNull(spec.writeExpirationTimeUnit);
    assertNull(spec.accessExpirationTimeUnit);
    assertCacheBuilderEquivalence(
        CacheBuilder.newBuilder().concurrencyLevel(32), CacheBuilder.from(spec));
  }

  public void testParse_concurrencyLevelRepeated() {
    try {
      parse("concurrencyLevel=10, concurrencyLevel=20");
      fail("Expected exception");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  public void testParse_weakKeys() {
    CacheBuilderSpec spec = parse("weakKeys");
    assertNull(spec.initialCapacity);
    assertNull(spec.maximumSize);
    assertNull(spec.maximumWeight);
    assertNull(spec.concurrencyLevel);
    assertEquals(Strength.WEAK, spec.keyStrength);
    assertNull(spec.valueStrength);
    assertNull(spec.writeExpirationTimeUnit);
    assertNull(spec.accessExpirationTimeUnit);
    assertCacheBuilderEquivalence(
        CacheBuilder.newBuilder().weakKeys(), CacheBuilder.from(spec));
  }

  public void testParse_weakKeysCannotHaveValue() {
    try {
      parse("weakKeys=true");
      fail("Expected exception");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  public void testParse_repeatedKeyStrength() {
    try {
      parse("weakKeys, weakKeys");
      fail("Expected exception");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  public void testParse_softValues() {
    CacheBuilderSpec spec = parse("softValues");
    assertNull(spec.initialCapacity);
    assertNull(spec.maximumSize);
    assertNull(spec.maximumWeight);
    assertNull(spec.concurrencyLevel);
    assertNull(spec.keyStrength);
    assertEquals(Strength.SOFT, spec.valueStrength);
    assertNull(spec.writeExpirationTimeUnit);
    assertNull(spec.accessExpirationTimeUnit);
    assertCacheBuilderEquivalence(
        CacheBuilder.newBuilder().softValues(), CacheBuilder.from(spec));
  }

  public void testParse_softValuesCannotHaveValue() {
    try {
      parse("softValues=true");
      fail("Expected exception");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  public void testParse_weakValues() {
    CacheBuilderSpec spec = parse("weakValues");
    assertNull(spec.initialCapacity);
    assertNull(spec.maximumSize);
    assertNull(spec.maximumWeight);
    assertNull(spec.concurrencyLevel);
    assertNull(spec.keyStrength);
    assertEquals(Strength.WEAK, spec.valueStrength);
    assertNull(spec.writeExpirationTimeUnit);
    assertNull(spec.accessExpirationTimeUnit);
    assertCacheBuilderEquivalence(
        CacheBuilder.newBuilder().weakValues(), CacheBuilder.from(spec));
  }

  public void testParse_weakValuesCannotHaveValue() {
    try {
      parse("weakValues=true");
      fail("Expected exception");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  public void testParse_repeatedValueStrength() {
    try {
      parse("softValues, softValues");
      fail("Expected exception");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    try {
      parse("softValues, weakValues");
      fail("Expected exception");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    try {
      parse("weakValues, softValues");
      fail("Expected exception");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    try {
      parse("weakValues, weakValues");
      fail("Expected exception");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  public void testParse_writeExpirationDays() {
    CacheBuilderSpec spec = parse("expireAfterWrite=10d");
    assertNull(spec.initialCapacity);
    assertNull(spec.maximumSize);
    assertNull(spec.maximumWeight);
    assertNull(spec.concurrencyLevel);
    assertNull(spec.keyStrength);
    assertNull(spec.valueStrength);
    assertEquals(TimeUnit.DAYS.toMillis(10),
        spec.writeExpirationTimeUnit.toMillis(spec.writeExpirationDuration));
    assertNull(spec.accessExpirationTimeUnit);
    assertCacheBuilderEquivalence(
        CacheBuilder.newBuilder().expireAfterWrite(10L, TimeUnit.DAYS), CacheBuilder.from(spec));
  }

  public void testParse_writeExpirationHours() {
    CacheBuilderSpec spec = parse("expireAfterWrite=150h");
    assertEquals(TimeUnit.HOURS.toMillis(150),
                 spec.writeExpirationTimeUnit.toMillis(spec.writeExpirationDuration));
    assertCacheBuilderEquivalence(
        CacheBuilder.newBuilder().expireAfterWrite(150L, TimeUnit.HOURS), CacheBuilder.from(spec));
  }

  public void testParse_writeExpirationMinutes() {
    CacheBuilderSpec spec = parse("expireAfterWrite=10m");
    assertEquals(TimeUnit.MINUTES.toMillis(10),
        spec.writeExpirationTimeUnit.toMillis(spec.writeExpirationDuration));
    assertCacheBuilderEquivalence(
        CacheBuilder.newBuilder().expireAfterWrite(10L, TimeUnit.MINUTES), CacheBuilder.from(spec));
  }

  public void testParse_writeExpirationSeconds() {
    CacheBuilderSpec spec = parse("expireAfterWrite=10s");
    assertEquals(TimeUnit.SECONDS.toMillis(10),
        spec.writeExpirationTimeUnit.toMillis(spec.writeExpirationDuration));
    assertCacheBuilderEquivalence(
        CacheBuilder.newBuilder().expireAfterWrite(10L, TimeUnit.SECONDS), CacheBuilder.from(spec));
  }

  public void testParse_writeExpirationRepeated() {
    try {
      parse(
          "expireAfterWrite=10s,expireAfterWrite=10m");
      fail("Expected exception");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  public void testParse_accessExpirationDays() {
    CacheBuilderSpec spec = parse("expireAfterAccess=10d");
    assertNull(spec.initialCapacity);
    assertNull(spec.maximumSize);
    assertNull(spec.maximumWeight);
    assertNull(spec.concurrencyLevel);
    assertNull(spec.keyStrength);
    assertNull(spec.valueStrength);
    assertNull(spec.writeExpirationTimeUnit);
    assertEquals(TimeUnit.DAYS.toMillis(10),
        spec.accessExpirationTimeUnit.toMillis(spec.accessExpirationDuration));
    assertCacheBuilderEquivalence(
        CacheBuilder.newBuilder().expireAfterAccess(10L, TimeUnit.DAYS), CacheBuilder.from(spec));
  }

  public void testParse_accessExpirationHours() {
    CacheBuilderSpec spec = parse("expireAfterAccess=150h");
    assertEquals(TimeUnit.HOURS.toMillis(150),
        spec.accessExpirationTimeUnit.toMillis(spec.accessExpirationDuration));
    assertCacheBuilderEquivalence(
        CacheBuilder.newBuilder().expireAfterAccess(150L, TimeUnit.HOURS), CacheBuilder.from(spec));
  }

  public void testParse_accessExpirationMinutes() {
    CacheBuilderSpec spec = parse("expireAfterAccess=10m");
    assertEquals(TimeUnit.MINUTES.toMillis(10),
        spec.accessExpirationTimeUnit.toMillis(spec.accessExpirationDuration));
    assertCacheBuilderEquivalence(
        CacheBuilder.newBuilder().expireAfterAccess(10L, TimeUnit.MINUTES),
        CacheBuilder.from(spec));
  }

  public void testParse_accessExpirationSeconds() {
    CacheBuilderSpec spec = parse("expireAfterAccess=10s");
    assertEquals(TimeUnit.SECONDS.toMillis(10),
        spec.accessExpirationTimeUnit.toMillis(spec.accessExpirationDuration));
    assertCacheBuilderEquivalence(
        CacheBuilder.newBuilder().expireAfterAccess(10L, TimeUnit.SECONDS),
        CacheBuilder.from(spec));
  }

  public void testParse_accessExpirationRepeated() {
    try {
      parse(
          "expireAfterAccess=10s,expireAfterAccess=10m");
      fail("Expected exception");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  public void testParse_accessExpirationAndWriteExpiration() {
    CacheBuilderSpec spec = parse("expireAfterAccess=10s,expireAfterWrite=9m");
    assertEquals(TimeUnit.SECONDS.toMillis(10),
        spec.accessExpirationTimeUnit.toMillis(spec.accessExpirationDuration));
    assertEquals(TimeUnit.MINUTES.toMillis(9),
        spec.writeExpirationTimeUnit.toMillis(spec.writeExpirationDuration));
    assertCacheBuilderEquivalence(
        CacheBuilder.newBuilder().expireAfterAccess(10L, TimeUnit.SECONDS),
        CacheBuilder.from(spec));
  }

  public void testParse_multipleKeys() {
    CacheBuilderSpec spec = parse("initialCapacity=10,maximumSize=20,concurrencyLevel=30,"
        + "weakKeys,weakValues,expireAfterAccess=10m,expireAfterWrite=1h");
    assertEquals(10, spec.initialCapacity.intValue());
    assertEquals(20, spec.maximumSize.intValue());
    assertNull(spec.maximumWeight);
    assertEquals(30, spec.concurrencyLevel.intValue());
    assertEquals(Strength.WEAK, spec.keyStrength);
    assertEquals(Strength.WEAK, spec.valueStrength);

    assertEquals(TimeUnit.MINUTES.toMillis(10),
        spec.accessExpirationTimeUnit.toMillis(spec.accessExpirationDuration));
    assertEquals(TimeUnit.HOURS.toMillis(1),
        spec.writeExpirationTimeUnit.toMillis(spec.writeExpirationDuration));
    
    CacheBuilder expected = CacheBuilder.newBuilder()
        .initialCapacity(10)
        .maximumSize(20)
        .concurrencyLevel(30)
        .weakKeys()
        .weakValues()
        .expireAfterAccess(10L, TimeUnit.MINUTES);
    assertCacheBuilderEquivalence(expected, CacheBuilder.from(spec));
  }

  public void testParse_whitespaceAllowed() {
    CacheBuilderSpec spec = parse(" initialCapacity=10,\nmaximumSize=20,\t\r"
        + "weakKeys \t ,softValues \n , \r  expireAfterWrite \t =  15s\n\n");
    assertEquals(10, spec.initialCapacity.intValue());
    assertEquals(20, spec.maximumSize.intValue());
    assertNull(spec.maximumWeight);
    assertNull(spec.concurrencyLevel);
    assertEquals(Strength.WEAK, spec.keyStrength);
    assertEquals(Strength.SOFT, spec.valueStrength);
    assertEquals(TimeUnit.SECONDS, spec.writeExpirationTimeUnit);
    assertEquals(15L, spec.writeExpirationDuration);
    assertNull(spec.accessExpirationTimeUnit);
    CacheBuilder expected = CacheBuilder.newBuilder()
        .initialCapacity(10)
        .maximumSize(20)
        .weakKeys()
        .softValues()
        .expireAfterWrite(15L, TimeUnit.SECONDS);
    assertCacheBuilderEquivalence(expected, CacheBuilder.from(spec));
  }

  public void testParse_unknownKey() {
    try {
      parse("foo=17");
      fail("Expected exception");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  public void testParse_extraCommaIsInvalid() {
    try {
      parse("weakKeys,");
      fail("Expected exception");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    try {
      parse(",weakKeys");
      fail("Expected exception");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    try {
      parse("weakKeys,,softValues");
      fail("Expected exception");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  public void testEqualsAndHashCode() {
    new EqualsTester()
        .addEqualityGroup(parse(""), parse(""))
        .addEqualityGroup(parse("concurrencyLevel=7"), parse("concurrencyLevel=7"))
        .addEqualityGroup(parse("concurrencyLevel=15"), parse("concurrencyLevel=15"))
        .addEqualityGroup(parse("initialCapacity=7"), parse("initialCapacity=7"))
        .addEqualityGroup(parse("initialCapacity=15"), parse("initialCapacity=15"))
        .addEqualityGroup(parse("maximumSize=7"), parse("maximumSize=7"))
        .addEqualityGroup(parse("maximumSize=15"), parse("maximumSize=15"))
        .addEqualityGroup(parse("maximumWeight=7"), parse("maximumWeight=7"))
        .addEqualityGroup(parse("maximumWeight=15"), parse("maximumWeight=15"))
        .addEqualityGroup(parse("expireAfterAccess=60s"), parse("expireAfterAccess=1m"))
        .addEqualityGroup(parse("expireAfterAccess=60m"), parse("expireAfterAccess=1h"))
        .addEqualityGroup(parse("expireAfterWrite=60s"), parse("expireAfterWrite=1m"))
        .addEqualityGroup(parse("expireAfterWrite=60m"), parse("expireAfterWrite=1h"))
        .addEqualityGroup(parse("weakKeys"), parse("weakKeys"))
        .addEqualityGroup(parse("softValues"), parse("softValues"))
        .addEqualityGroup(parse("weakValues"), parse("weakValues"))
        .testEquals();
  }

  public void testMaximumWeight_withWeigher() {
    CacheBuilder<Object, Object> builder = CacheBuilder.from(parse("maximumWeight=9000"));
    builder
        .weigher(constantWeigher(42))
        .build(CacheLoader.from(Suppliers.ofInstance(null)));
  }

  public void testMaximumWeight_withoutWeigher() {
    CacheBuilder<Object, Object> builder = CacheBuilder.from(parse("maximumWeight=9000"));
    try {
      builder.build(CacheLoader.from(Suppliers.ofInstance(null)));
      fail();
    } catch (IllegalStateException expected) {}
  }

  public void testMaximumSize_withWeigher() {
    CacheBuilder<Object, Object> builder = CacheBuilder.from(parse("maximumSize=9000"));
    builder
        .weigher(constantWeigher(42))
        .build(CacheLoader.from(Suppliers.ofInstance(null)));
  }

  public void testMaximumSize_withoutWeigher() {
    CacheBuilder<Object, Object> builder = CacheBuilder.from(parse("maximumSize=9000"));
    builder.build(CacheLoader.from(Suppliers.ofInstance(null)));
  }

  public void testDisableCaching() {
    // Functional test: assert that CacheBuilderSpec.disableCaching()
    // disables caching.  It's irrelevant how it does so.
    CacheBuilder<Object, Object> builder = CacheBuilder.from(CacheBuilderSpec.disableCaching());
    Object key = new Object();
    Object value = new Object();
    LoadingCache<Object, Object> cache = builder.build(
        CacheLoader.from(Suppliers.ofInstance(value)));
    assertSame(value, cache.getUnchecked(key));
    assertEquals(0, cache.size());
    assertFalse(cache.asMap().containsKey(key));
  }

  public void testCacheBuilderFrom_string() {
    CacheBuilder fromString = CacheBuilder.from(
        "initialCapacity=10,maximumSize=20,concurrencyLevel=30,"
        + "weakKeys,weakValues,expireAfterAccess=10m");
    CacheBuilder expected = CacheBuilder.newBuilder()
        .initialCapacity(10)
        .maximumSize(20)
        .concurrencyLevel(30)
        .weakKeys()
        .weakValues()
        .expireAfterAccess(10L, TimeUnit.MINUTES);
    assertCacheBuilderEquivalence(expected, fromString);
  }

  private void assertCacheBuilderEquivalence(CacheBuilder a, CacheBuilder b) {
    // Labs hack:  dig into the CacheBuilder instances, verifying all fields are equal.
    for (Field f : CacheBuilder.class.getFields()) {
      f.setAccessible(true);
      try {
        assertEquals("Field " + f.getName() + " not equal", f.get(a), f.get(b));
      } catch (IllegalArgumentException e) {
        throw new AssertionError(e.getMessage());
      } catch (IllegalAccessException e) {
        throw new AssertionError(e.getMessage());
      }
    }
  }
}
