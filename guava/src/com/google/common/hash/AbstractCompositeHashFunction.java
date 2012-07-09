// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.common.hash;

import java.nio.charset.Charset;

/**
 * An abstract composition of multiple hash functions. {@linkplain #newHasher()} delegates to the
 * {@code Hasher} objects of the delegate hash functions, and in the end, they are used by
 * {@linkplain #makeHash(Hasher[])} that constructs the final {@code HashCode}.
 * 
 * @author andreou@google.com (Dimitris Andreou)
 */
abstract class AbstractCompositeHashFunction extends AbstractStreamingHashFunction {
  final HashFunction[] functions;
  
  AbstractCompositeHashFunction(HashFunction... functions) {
    this.functions = functions;
  }
  
  /**
   * Constructs a {@code HashCode} from the {@code Hasher} objects of the functions. Each of them
   * has consumed the entire input and they are ready to output a {@code HashCode}. The order of
   * the hashers are the same order as the functions given to the constructor.
   */
  // this could be cleaner if it passed HashCode[], but that would create yet another array...
  /* protected */ abstract HashCode makeHash(Hasher[] hashers);
  
  public Hasher newHasher() {
    final Hasher[] hashers = new Hasher[functions.length];
    for (int i = 0; i < hashers.length; i++) {
      hashers[i] = functions[i].newHasher();
    }
    return new Hasher() {
      public Hasher putByte(byte b) {
        for (Hasher hasher : hashers) {
          hasher.putByte(b);
        }
        return this;
      }

      public Hasher putBytes(byte[] bytes) {
        for (Hasher hasher : hashers) {
          hasher.putBytes(bytes);
        }
        return this;
      }

      public Hasher putBytes(byte[] bytes, int off, int len) {
        for (Hasher hasher : hashers) {
          hasher.putBytes(bytes, off, len);
        }
        return this;
      }

      public Hasher putShort(short s) {
        for (Hasher hasher : hashers) {
          hasher.putShort(s);
        }
        return this;
      }

      public Hasher putInt(int i) {
        for (Hasher hasher : hashers) {
          hasher.putInt(i);
        }
        return this;
      }

      public Hasher putLong(long l) {
        for (Hasher hasher : hashers) {
          hasher.putLong(l);
        }
        return this;
      }

      public Hasher putFloat(float f) {
        for (Hasher hasher : hashers) {
          hasher.putFloat(f);
        }
        return this;
      }

      public Hasher putDouble(double d) {
        for (Hasher hasher : hashers) {
          hasher.putDouble(d);
        }
        return this;
      }

      public Hasher putBoolean(boolean b) {
        for (Hasher hasher : hashers) {
          hasher.putBoolean(b);
        }
        return this;
      }

      public Hasher putChar(char c) {
        for (Hasher hasher : hashers) {
          hasher.putChar(c);
        }
        return this;
      }

      public Hasher putString(CharSequence chars) {
        for (Hasher hasher : hashers) {
          hasher.putString(chars);
        }
        return this;
      }

      public Hasher putString(CharSequence chars, Charset charset) {
        for (Hasher hasher : hashers) {
          hasher.putString(chars, charset);
        }
        return this;
      }

      public <T> Hasher putObject(T instance, Funnel<? super T> funnel) {
        for (Hasher hasher : hashers) {
          hasher.putObject(instance, funnel);
        }
        return this;
      }

      public HashCode hash() {
        return makeHash(hashers);
      }
    };
  }
  
  private static final long serialVersionUID = 0L;
}
