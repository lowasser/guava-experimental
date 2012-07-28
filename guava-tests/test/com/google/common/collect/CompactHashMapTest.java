package com.google.common.collect;

import static org.junit.contrib.truth.Truth.ASSERT;

import com.google.common.collect.testing.MapTestSuiteBuilder;
import com.google.common.collect.testing.TestStringMapGenerator;
import com.google.common.collect.testing.features.CollectionFeature;
import com.google.common.collect.testing.features.CollectionSize;
import com.google.common.collect.testing.features.MapFeature;

import java.util.Map;
import java.util.Map.Entry;

import junit.framework.Test;
import junit.framework.TestCase;

/**
 * Tests for {@code CompactHashMap}.
 * 
 * @author Louis Wasserman
 */
public class CompactHashMapTest extends TestCase {
  public static Test suite() {
    return MapTestSuiteBuilder.using(new TestStringMapGenerator() {
        @Override
        protected Map<String, String> create(Entry<String, String>[] entries) {
          Map<String, String> map = CompactHashMap.create();
          for (Map.Entry<String, String> entry : entries) {
            map.put(entry.getKey(), entry.getValue());
          }
          return map;
        }
      })
      .named("CompactHashMap")
      .withFeatures(
          MapFeature.ALLOWS_NULL_KEYS,
          MapFeature.ALLOWS_NULL_VALUES,
          MapFeature.GENERAL_PURPOSE,
          CollectionSize.ANY,
          CollectionFeature.SERIALIZABLE,
          MapFeature.FAILS_FAST_ON_CONCURRENT_MODIFICATION)
      .createTestSuite();
  }
  
  private static final ImmutableList<String> KEYS = ImmutableList.of(
      "foo", "bar", "baz", "quux", "fred", "milk", "cereal");
  
  /**
   * Test that without removes, insertion order is preserved.
   */
  public void testInsertionOrder() {
    Map<String, String> map = CompactHashMap.createWithExpectedSize(1);
    for (String string : KEYS) {
      map.put(string, string);
    }
    ASSERT.that(map.keySet()).hasContentsInOrder(KEYS.toArray());
    ASSERT.that(map.values()).hasContentsInOrder(KEYS.toArray());
  }
}
