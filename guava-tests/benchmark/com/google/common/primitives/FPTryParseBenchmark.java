/*
 * Copyright (C) 2012 The Guava Authors
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
package com.google.common.primitives;

import com.google.caliper.Param;
import com.google.caliper.Runner;
import com.google.caliper.SimpleBenchmark;

import java.util.Random;

/**
 * A benchmark for {@link Floats#tryParse} and {@link Doubles#tryParse}.
 * 
 * @author Louis Wasserman
 */
public class FPTryParseBenchmark extends SimpleBenchmark {
  @Param({ "0", "1", "3" })
  int nIPartDigits;

  @Param({ "0", "1", "3" })
  int nFPartDigits;

  @Param({ "0", "1" })
  int exponentDigits;

  private static final String[] SIGNS = { "+", "-", "" };
  private static final String[] SUFFIXES = { "f", "F", "d", "D", "" };

  @Param({ "DEC", "HEX" })
  Base base;

  enum Base {
    DEC {
      @Override
      String generate(int iPartDigits, int fPartDigits, int expDigits, Random rng) {
        StringBuilder result = new StringBuilder();
        result.append(SIGNS[rng.nextInt(SIGNS.length)]);
        if (iPartDigits > 0) {
          result.append(rng.nextInt(9) + 1);
          for (int i = 1; i < iPartDigits; i++) {
            result.append(rng.nextInt(10));
          }
        }
        if (fPartDigits > 0) {
          result.append('.');
          for (int i = 0; i < fPartDigits; i++) {
            result.append(rng.nextInt(10));
          }
        }
        if (expDigits > 0) {
          result
              .append(rng.nextBoolean() ? 'e' : 'E')
              .append(SIGNS[rng.nextInt(SIGNS.length)]);
          result.append(rng.nextInt(9) + 1);
          for (int i = 1; i < expDigits; i++) {
            result.append(rng.nextInt(10));
          }
        }
        result.append(SUFFIXES[rng.nextInt(SUFFIXES.length)]);
        return result.toString();
      }
    },
    HEX {
      @Override
      String generate(int iPartDigits, int fPartDigits, int expDigits, Random rng) {
        StringBuilder result = new StringBuilder();
        result.append(SIGNS[rng.nextInt(SIGNS.length)]);
        result.append('0')
            .append(rng.nextBoolean() ? 'x' : 'X');
        if (iPartDigits > 0) {
          result.append(Character.forDigit(rng.nextInt(15) + 1, 16));
          for (int i = 1; i < iPartDigits; i++) {
            result.append(Character.forDigit(rng.nextInt(16), 16));
          }
        }
        if (fPartDigits > 0) {
          result.append('.');
          for (int i = 0; i < fPartDigits; i++) {
            result.append(Character.forDigit(rng.nextInt(16), 16));
          }
        }
        if (expDigits > 0) {
          result
              .append(rng.nextBoolean() ? 'p' : 'P')
              .append(SIGNS[rng.nextInt(SIGNS.length)]);
          result.append(rng.nextInt(9) + 1);
          for (int i = 1; i < expDigits; i++) {
            result.append(rng.nextInt(10));
          }
        }
        result.append(SUFFIXES[rng.nextInt(SUFFIXES.length)]);
        return result.toString();
      }
    };

    abstract String generate(int iPartDigits, int fPartDigits, int expDigits, Random rng);
  }

  private final String[] inputs = new String[0x100];

  @Override
  protected void setUp() throws Exception {
    Random rng = new Random();
    for (int i = 0; i < 0x100; i++) {
      inputs[i] = base.generate(nIPartDigits, nFPartDigits, exponentDigits, rng);
    }
  }

  public int timeTryParseFloat(int reps) {
    int tmp = 0;
    for (int i = 0; i < reps; i++) {
      Float f = Floats.tryParse(inputs[i & 0xFF]);
      tmp += (f == null) ? 0 : f.hashCode();
    }
    return tmp;
  }

  public int timeTryParseDouble(int reps) {
    int tmp = 0;
    for (int i = 0; i < reps; i++) {
      Double d = Doubles.tryParse(inputs[i & 0xFF]);
      tmp += (d == null) ? 0 : d.hashCode();
    }
    return tmp;
  }

  public static void main(String[] args) {
    Runner.main(FPTryParseBenchmark.class, args);
  }
}
