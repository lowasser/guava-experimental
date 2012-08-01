package com.google.common.collect;

import javax.annotation.Nullable;

final class LargeImmutableSet<E> extends RegularImmutableSet<E> {
  private final int[] hashTable;

  LargeImmutableSet(Object[] elements, int[] hashTable, int hashCode) {
    super(elements, hashCode);
    this.hashTable = hashTable;
  }

  @Override
  int indexOf(@Nullable Object o) {
    if (o == null) {
      return -1;
    }
    int hash = Hashing.smear(o.hashCode());
    int mask = hashTable.length - 1;
    for (int i = hash;; i++) {
      int tableIndex = i & mask;
      // We're iterating tableIndex cyclically around the table
      int tableEntry = hashTable[tableIndex];
      if (tableEntry == -1) {
        return -1;
      } else if (o.equals(elements[tableEntry])) {
        return tableEntry;
      }
    }
  }
}
