package com.google.common.collect;

import static org.junit.contrib.truth.Truth.ASSERT;

import com.google.common.collect.testing.MapTestSuiteBuilder;
import com.google.common.collect.testing.TestStringMapGenerator;
import com.google.common.collect.testing.features.CollectionFeature;
import com.google.common.collect.testing.features.CollectionSize;
import com.google.common.collect.testing.features.MapFeature;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import junit.framework.Test;
import junit.framework.TestCase;

/**
 * Tests for {@code CompactLinkedHashMap}.
 * 
 * @author Louis Wasserman
 */
public class CompactLinkedHashMapTest extends TestCase {
  public static Test suite() {
    return MapTestSuiteBuilder.using(new TestStringMapGenerator() {
        @Override
        protected Map<String, String> create(Entry<String, String>[] entries) {
          Map<String, String> map = CompactLinkedHashMap.create();
          for (Map.Entry<String, String> entry : entries) {
            map.put(entry.getKey(), entry.getValue());
          }
          return map;
        }
      })
      .named("CompactLinkedHashMap")
      .withFeatures(
          MapFeature.ALLOWS_NULL_KEYS,
          MapFeature.ALLOWS_NULL_VALUES,
          MapFeature.GENERAL_PURPOSE,
          CollectionFeature.KNOWN_ORDER,
          CollectionSize.ANY,
          CollectionFeature.SERIALIZABLE,
          MapFeature.FAILS_FAST_ON_CONCURRENT_MODIFICATION)
      .createTestSuite();
  }
  
  private static final ImmutableList<String> KEYS = ImmutableList.of(
      "foo", "bar", "baz", "quux", "fred", "milk", "cereal");
  
  private static final ImmutableSet<String> KEYS_TO_REMOVE =
      ImmutableSet.of("foo", "cereal", "quux");
  
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
  
  /**
   * Test that without removes, insertion order is preserved.
   */
  public void testInsertionOrderWithRemoves() {
    Map<String, String> map = CompactHashMap.createWithExpectedSize(1);
    for (String string : KEYS) {
      map.put(string, string);
    }
    List<String> removedKeys = Lists.newArrayList(KEYS);
    removedKeys.removeAll(KEYS_TO_REMOVE);
    map.keySet().removeAll(KEYS_TO_REMOVE);
    ASSERT.that(map.keySet()).hasContentsInOrder(removedKeys.toArray());
    ASSERT.that(map.values()).hasContentsInOrder(removedKeys.toArray());
  }
}
