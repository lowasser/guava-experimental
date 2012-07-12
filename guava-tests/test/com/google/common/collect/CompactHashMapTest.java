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

public class CompactHashMapTest extends TestCase {
  public static Test suite() {
    return MapTestSuiteBuilder.using(new TestStringMapGenerator() {
  
        @Override
        protected Map<String, String> create(Entry<String, String>[] entries) {
          CompactHashMap<String, String> map = new CompactHashMap<String, String>();
          for (Entry<String, String> entry : entries) {
            map.put(entry.getKey(), entry.getValue());
          }
          return map;
        }
      })
      .named("CompactHashMap")
      .withFeatures(CollectionSize.ANY,
          MapFeature.GENERAL_PURPOSE,
          CollectionFeature.SERIALIZABLE,
          MapFeature.ALLOWS_NULL_KEYS,
          MapFeature.ALLOWS_NULL_VALUES)
      .createTestSuite();
  }
}
