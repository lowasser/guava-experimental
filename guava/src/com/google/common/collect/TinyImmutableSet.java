package com.google.common.collect;

import static com.google.common.base.Preconditions.checkArgument;

import javax.annotation.Nullable;

/**
 * A {@code ImmutableSet} implementation that packs the hash table into the bits of a long.
 * Only usable for very small collections, but extremely efficient within that capacity.
 * 
 * @author Louis Wasserman
 */
final class TinyImmutableSet<E> extends RegularImmutableSet<E> {
  /**
   * Width, in bits, of an entry in the hash table.
   */
  private static final int ENTRY_WIDTH = 4; 
  
  /**
   * The maximum size of the hash table.
   */
  static final int TABLE_SIZE = Long.SIZE / ENTRY_WIDTH;
  private static final long ENTRY_MASK = (1L << ENTRY_WIDTH) - 1;
  private static final int BLANK_ENTRY = (int) (-1L & ENTRY_MASK);
  
  private transient final long hashTable;
  
  TinyImmutableSet(Object[] elements, int[] hashTable, int hashCode) {
    super(elements, hashCode);
    checkArgument(hashTable.length == TABLE_SIZE);
    long packedTable = 0L;
    for (int i = TABLE_SIZE - 1; i >= 0; i--) {
      packedTable <<= ENTRY_WIDTH;
      packedTable |= hashTable[i] & ENTRY_MASK;
    }
    this.hashTable = packedTable;
  }

  @Override
  int indexOf(@Nullable Object o) {
    if (o == null) {
      return -1;
    }
    long hashTable = Long.rotateRight(this.hashTable, Hashing.smear(o.hashCode()) * ENTRY_WIDTH);
    while (true) {
      int tableEntry = (int) (hashTable & ENTRY_MASK);
      if (tableEntry == BLANK_ENTRY) {
        return -1;
      } else if (o.equals(elements[tableEntry])) {
        return tableEntry;
      }
      hashTable >>>= ENTRY_WIDTH;
    }
  }
}
