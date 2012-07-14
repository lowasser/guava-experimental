package com.google.common.collect;

import com.google.common.base.Objects;

import java.util.AbstractSet;

import javax.annotation.Nullable;

final class FusedHashSet<E> extends AbstractSet<E> {
  private static abstract class Bucket {
    abstract Bucket remove(@Nullable Object o, byte hash, boolean[] modified);
  }

  private static final Object BLANK_ELEMENT = new Object();
  private static final byte BLANK_HASH = (byte) 0;

  private static class Bucket1 extends Bucket {
    @Nullable
    final Object elem1;

    final byte hash1;
    final byte hash2;
    final byte hash3;
    final byte hash4;

    Bucket1(Object elem1, byte hash1) {
      this (elem1, hash1, BLANK_HASH, BLANK_HASH, BLANK_HASH);
    }

    Bucket1(Object elem1, byte hash1, byte hash2, byte hash3, byte hash4) {
      this.elem1 = elem1;
      this.hash1 = hash1;
      this.hash2 = hash2;
      this.hash3 = hash3;
      this.hash4 = hash4;
    }

    @Override
    Bucket remove(@Nullable Object o, byte hash, boolean[] modified) {
      if (hash == hash1 && Objects.equal(elem1, o)) {
        modified[0] = true;
        return null;
      } else {
        return this;
      }
    }
  }

  private static class Bucket2 extends Bucket1 {
    @Nullable
    final Object elem2;

    @Nullable
    final Object elem3;
    
    Bucket2(
        @Nullable Object elem1,
        byte hash1,
        @Nullable Object elem2,
        byte hash2) {
      this(
          elem1, hash1,
          elem2, hash2,
          BLANK_ELEMENT, BLANK_HASH);
    }

    private Bucket2(
        Object elem1,
        byte hash1,
        Object elem2,
        byte hash2,
        Object elem3,
        byte hash3) {
      this(
          elem1, hash1,
          elem2, hash2,
          elem3, hash3,
          BLANK_HASH);
    }

    Bucket2(
        @Nullable Object elem1,
        byte hash1,
        @Nullable Object elem2,
        byte hash2,
        @Nullable Object elem3,
        byte hash3,
        byte hash4) {
      super(elem1, hash1, hash2, hash3, hash4);
      this.elem2 = elem2;
      this.elem3 = elem3;
    }

    @Override
    Bucket remove(@Nullable Object o, byte hash, boolean[] modified) {
      if (hash == hash1 && Objects.equal(elem1, o)) {
        modified[0] = true;
        if (elem3 == BLANK_ELEMENT) {
          return new Bucket1(elem2, hash2);
        } else {
          return new Bucket2(elem2, hash2, elem3, hash3);
        }
      } else if (hash == hash2 && Objects.equal(elem2, o)) {
        modified[0] = true;
        if (elem3 == BLANK_ELEMENT) {
          return new Bucket1(elem1, hash1);
        } else {
          return new Bucket2(elem1, hash1, elem3, hash3);
        }
      } else if (hash == hash3 && Objects.equal(elem3, o)) {
        modified[0] = true;
        return new Bucket2(elem1, hash1, elem2, hash2);
      } else {
        return this;
      }
    }
  }

  private static class Bucket4 extends Bucket2 {
    @Nullable
    final Object elem4;

    @Nullable
    final Bucket next;

    private Bucket4(
        @Nullable Object elem1,
        byte hash1,
        @Nullable Object elem2,
        byte hash2,
        @Nullable Object elem3,
        byte hash3,
        @Nullable Object elem4,
        byte hash4,
        @Nullable Bucket next) {
      super(elem1, hash1, elem2, hash2, elem3, hash3, hash4);
      this.elem4 = elem4;
      this.next = next;
    }

    @Override
    Bucket remove(@Nullable Object o, byte hash, boolean[] modified) {
      if (hash == hash1 && Objects.equal(elem1, o)) {
        
      }
    }
  }
  
  
  private static Bucket bucketAdd(@Nullable Bucket b, @Nullable Object o, byte hash) {
    if (b == null) {
      return new Bucket1(o, hash);
    } else if (b instanceof Bucket4) {
      Bucket4 b4 = (Bucket4) b;
      return new Bucket4(
          b4.elem1, b4.hash1,
          b4.elem2, b4.hash2,
          b4.elem3, b4.hash3,
          b4.elem4, b4.hash4,
          bucketAdd(b4.next, o, hash));
    } else if (b instanceof Bucket2) {
      Bucket2 b2 = (Bucket2) b;
      if (b2.elem3 == BLANK_ELEMENT) {
        return new Bucket2(
            b2.elem1, b2.hash1,
            b2.elem2, b2.hash2,
            o, hash);
      } else {
        return new Bucket4(
            b2.elem1, b2.hash1,
            b2.elem2, b2.hash2,
            b2.elem3, b2.hash3,
            o, hash, null);
      }
    } else {
      Bucket1 b1 = (Bucket1) b;
      return new Bucket2(
          b1.elem1, b1.hash1,
          o, hash);
    }
  }
  
  private static boolean bucketContains(@Nullable Bucket b, @Nullable Object o, byte hash) {
    if (b == null) {
      return false;
    }
    Bucket1 b1 = (Bucket1) b;
    if (b1.hash1 == hash && Objects.equal(b1.elem1, o)) {
      return true;
    } else if (b1 instanceof Bucket2) {
      Bucket2 b2 = (Bucket2) b1;
      if ((b2.hash2 == hash && Objects.equal(b2.elem2, o))
          || (b2.hash3 == hash && Objects.equal(b2.elem3, o))) {
        return true;
      } else if (b1 instanceof Bucket4) {
        Bucket4 b4 = (Bucket4) b2;
        return (b4.hash4 == hash && Objects.equal(b4.elem4, o))
            || bucketContains(b4.next, o, hash);
      }
    }
    return false;
  }
  
  
}
