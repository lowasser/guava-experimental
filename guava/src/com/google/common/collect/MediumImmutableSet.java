package com.google.common.collect;

import javax.annotation.Nullable;

final class MediumImmutableSet<E> extends RegularImmutableSet<E> {
  static final int MAX_SIZE = 0xFFFF;
  private static final int UNSIGNED_SHORT_MASK = 0xFFFF;
  private static final int BLANK_ENTRY = -1 & UNSIGNED_SHORT_MASK;
  
  private transient final short[] hashTable;
  
  MediumImmutableSet(Object[] elements, int[] hashTable, int hashCode) {
    super(elements, hashCode);
    short[] packedTable = new short[hashTable.length];
    for (int i = 0; i < hashTable.length; i++) {
      packedTable[i] = (short) hashTable[i];
    }
    this.hashTable = packedTable;
  }

  @Override
  int indexOf(@Nullable Object o) {
    if (o == null) {
      return -1;
    }
    short[] hashTable = this.hashTable;
    int mask = hashTable.length - 1;
    for (int i = Hashing.smear(o.hashCode());; i++) {
      int tableEntry = hashTable[i & mask] & UNSIGNED_SHORT_MASK;
      if (tableEntry == BLANK_ENTRY) {
        return -1;
      } else if (o.equals(elements[tableEntry])) {
        return tableEntry;
      }
    }
  }
}
