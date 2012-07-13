package com.google.common.collect;

import com.google.common.collect.testing.MapTestSuiteBuilder;
import com.google.common.collect.testing.TestStringMapGenerator;
import com.google.common.collect.testing.features.CollectionFeature;
import com.google.common.collect.testing.features.CollectionSize;
import com.google.common.collect.testing.features.MapFeature;

import java.util.Map;
import java.util.Map.Entry;

import junit.framework.Test;
import junit.framework.TestCase;

public class CompactLinkedHashMapTest extends TestCase {
  public static Test suite() {
    return MapTestSuiteBuilder.using(new TestStringMapGenerator() {
  
        @Override
        protected Map<String, String> create(Entry<String, String>[] entries) {
          CompactLinkedHashMap<String, String> map = new CompactLinkedHashMap<String, String>();
          for (Entry<String, String> entry : entries) {
            map.put(entry.getKey(), entry.getValue());
          }
          return map;
        }
      })
      .named("CompactLinkedHashMap")
      .withFeatures(CollectionSize.ANY,
          MapFeature.GENERAL_PURPOSE,
          CollectionFeature.FAILS_FAST_ON_CONCURRENT_MODIFICATION,
          CollectionFeature.KNOWN_ORDER,
          CollectionFeature.SERIALIZABLE,
          MapFeature.ALLOWS_NULL_KEYS,
          MapFeature.ALLOWS_NULL_VALUES)
      .createTestSuite();
  }
}
