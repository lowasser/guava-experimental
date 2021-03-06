/*
 * Copyright (C) 2011 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.math;

import java.math.BigInteger;
import java.util.Random;

/**
 * Utilities for benchmarks.
 *
 * @author Louis Wasserman
 */
final class MathBenchmarking {
  static final int ARRAY_SIZE = 0x10000;
  static final int ARRAY_MASK = 0x0ffff;
  static final Random RANDOM_SOURCE = new Random(314159265358979L);
  static final int MAX_EXPONENT = 100;

  /*
   * Duplicated from LongMath.
   * binomial(BIGGEST_BINOMIALS[k], k) fits in a long, but not
   * binomial(BIGGEST_BINOMIALS[k] + 1, k).
   */
  static final int[] BIGGEST_BINOMIALS =
      {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 3810779, 121977, 16175, 4337, 1733,
          887, 534, 361, 265, 206, 169, 143, 125, 111, 101, 94, 88, 83, 79, 76, 74, 72, 70, 69, 68,
          67, 67, 66, 66, 66, 66};

  static BigInteger randomPositiveBigInteger(int numBits) {
    int digits = RANDOM_SOURCE.nextInt(numBits) + 1;
    return new BigInteger(digits, RANDOM_SOURCE).add(BigInteger.ONE);
  }

  static BigInteger randomNonNegativeBigInteger(int numBits) {
    int digits = RANDOM_SOURCE.nextInt(numBits) + 1;
    return new BigInteger(digits, RANDOM_SOURCE);
  }

  static BigInteger randomNonZeroBigInteger(int numBits) {
    BigInteger result = randomPositiveBigInteger(numBits);
    return RANDOM_SOURCE.nextBoolean() ? result : result.negate();
  }

  static BigInteger randomBigInteger(int numBits) {
    BigInteger result = randomNonNegativeBigInteger(numBits);
    return RANDOM_SOURCE.nextBoolean() ? result : result.negate();
  }

  static double randomDouble(int maxExponent) {
    double result = RANDOM_SOURCE.nextDouble();
    result = Math.scalb(result, RANDOM_SOURCE.nextInt(maxExponent + 1));
    return RANDOM_SOURCE.nextBoolean() ? result : -result;
  }

  /**
   * Returns a random integer between zero and {@code MAX_EXPONENT}.
   */
  static int randomExponent() {
    return RANDOM_SOURCE.nextInt(MAX_EXPONENT + 1);
  }

  static double randomPositiveDouble() {
    return Math.exp(randomDouble(6));
  }
}
