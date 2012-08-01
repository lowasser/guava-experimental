package com.google.common.collect;

import static com.google.common.base.Preconditions.checkArgument;

import javax.annotation.Nullable;

final class SmallImmutableSet<E> extends RegularImmutableSet<E> {
  private static final int UNSIGNED_BYTE_MASK = 0xFF;
  private static final int BLANK_ENTRY = -1 & UNSIGNED_BYTE_MASK;
  static final int MAX_SIZE = 0xFF;
  
  private transient final byte[] hashTable;

  SmallImmutableSet(Object[] elements, int[] hashTable, int hashCode) {
    super(elements, hashCode);
    checkArgument(elements.length <= MAX_SIZE);
    byte[] packedTable = new byte[hashTable.length];
    for (int i = 0; i < hashTable.length; i++) {
      packedTable[i] = (byte) hashTable[i]; 
    }
    this.hashTable = packedTable;
  }

  @Override
  int indexOf(@Nullable Object o) {
    if (o == null) {
      return -1;
    }
    byte[] hashTable = this.hashTable;
    int mask = hashTable.length - 1;
    for (int i = Hashing.smear(o.hashCode());; i++) {
      int tableIndex = i & mask;
      // We're cyclically iterating tableIndex around 0..hashTable.length - 1.
      int tableEntry = hashTable[tableIndex] & UNSIGNED_BYTE_MASK;
      if (tableEntry == BLANK_ENTRY) {
        return -1;
      } else if (o.equals(elements[tableEntry])) {
        return tableEntry;
      }
    }
  }
}
