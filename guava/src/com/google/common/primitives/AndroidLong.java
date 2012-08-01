/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.common.primitives;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import javax.annotation.CheckForNull;

/**
 * Static utility methods derived from Android's {@code Long.java}.
 */
final class AndroidLong {
  /**
   * See {@link Longs#tryParse(String)} for the public interface.
   */
  @CheckForNull
  static Long tryParse(String string) {
    return tryParse(string, 10);
  }
  
  static void checkRadix(int radix) {
    checkArgument(radix >= Character.MIN_RADIX,
        "Invalid radix %s, min radix is %s", radix, Character.MIN_RADIX);
    checkArgument(radix <= Character.MAX_RADIX,
        "Invalid radix %s, max radix is %s", radix, Character.MAX_RADIX);
  }

  /**
   * See {@link Longs#tryParse(String, int)} for the public interface.
   */
  @CheckForNull
  static Long tryParse(String string, int radix) {
    checkNotNull(string);
    checkRadix(radix);
    int length = string.length(), i = 0;
    if (length == 0) {
      return null;
    }
    boolean negative = string.charAt(i) == '-';
    if (negative && ++i == length) {
      return null;
    }
    return tryParse(string, i, radix, negative);
  }

  @CheckForNull
  private static Long tryParse(String string, int offset, int radix,
      boolean negative) {
    long max = Long.MIN_VALUE / radix;
    int length = string.length();
    
    long result = 0;
    // We compute the negative value to avoid overflow conditions on e.g. MIN_VALUE.
    while (offset < length) {
      int digit = Character.digit(string.charAt(offset++), radix);
      if (digit == -1) {
        return null;
      }
      if (max > result) {
        return null;
      }
      long next = result * radix - digit;
      if (next > result) {
        return null;
      }
      result = next;
    }
    if (!negative) {
      result = -result;
      if (result < 0) {
        return null;
      }
    }
    return result;
  }

  @CheckForNull
  static UnsignedLong tryParseUnsigned(String string, int radix) {
    checkNotNull(string);
    checkRadix(radix);
    int length = string.length();
    if (length == 0) {
      return null;
    }
    long result = 0;
    for (int offset = 0; offset < length; offset++) {
      int digit = Character.digit(string.charAt(offset), radix);
      if (digit == -1) {
        return null;
      }
      if (UnsignedLongs.overflowInParse(result, digit, radix)) {
        return null;
      }
      result = result * radix + digit;
    }
    return UnsignedLong.asUnsigned(result);
  }

  private AndroidLong() {}
}
