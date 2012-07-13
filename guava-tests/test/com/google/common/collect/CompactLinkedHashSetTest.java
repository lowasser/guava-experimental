// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.common.collect;

import com.google.common.annotations.GwtCompatible;
import com.google.common.collect.testing.SetTestSuiteBuilder;
import com.google.common.collect.testing.TestStringSetGenerator;
import com.google.common.collect.testing.features.CollectionFeature;
import com.google.common.collect.testing.features.CollectionSize;
import com.google.common.collect.testing.features.Feature;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Tests for CompactHashSet.
 *
 * @author andreou@google.com (Dimitris Andreou)
 */
@GwtCompatible
public class CompactLinkedHashSetTest extends TestCase {
  public static Test suite() {
    List<Feature<?>> allFeatures = Arrays.<Feature<?>>asList(
        CollectionSize.ANY,
        CollectionFeature.ALLOWS_NULL_VALUES,
        CollectionFeature.FAILS_FAST_ON_CONCURRENT_MODIFICATION,
        CollectionFeature.GENERAL_PURPOSE,
        CollectionFeature.SERIALIZABLE,
        CollectionFeature.KNOWN_ORDER);

    TestSuite suite = new TestSuite();
    suite.addTestSuite(SetsTest.class);
    suite.addTest(SetTestSuiteBuilder.using(new TestStringSetGenerator() {
      @Override protected Set<String> create(String[] elements) {
        return CompactLinkedHashSet.create(Arrays.asList(elements));
      }
    }).named("CompactLinkedHashSet")
      .withFeatures(allFeatures)
      .createTestSuite());
    suite.addTest(SetTestSuiteBuilder.using(new TestStringSetGenerator() {
      @Override protected Set<String> create(String[] elements) {
        CompactLinkedHashSet set = CompactLinkedHashSet.create(Arrays.asList(elements));
        for (int i = 0; i < 100; i++) {
          set.add(i);
        }
        for (int i = 0; i < 100; i++) {
          set.remove(i);
        }
        set.trimToSize();
        return set;
      }
    }).named("CompactLinkedHashSet#TrimToSize")
      .withFeatures(allFeatures)
      .createTestSuite());
    return suite;
  }

  public void testEmpty_trimToSize() throws Exception {
    Field entriesField = CompactHashSet.class.getDeclaredField("entries");
    Field elementsField = CompactHashSet.class.getDeclaredField("elements");
    Field predecessorField = CompactLinkedHashSet.class.getDeclaredField("predecessor");
    Field successorField = CompactLinkedHashSet.class.getDeclaredField("successor");
    for (int size = 0; size < 20; size++) {
      CompactHashSet<String> set = CompactHashSet.create();
      set.add("" + size);
      set.trimToSize();
      assertEquals(size, ((long[]) entriesField.get(set)).length);
      assertEquals(size, ((Object[]) elementsField.get(set)).length);
      assertEquals(size, ((int[]) predecessorField.get(set)).length);
      assertEquals(size, ((int[]) successorField.get(set)).length);
    }
  }
}