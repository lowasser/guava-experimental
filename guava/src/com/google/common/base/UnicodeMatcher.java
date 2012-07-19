package com.google.common.base;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkPositionIndex;

import java.util.Arrays;

public abstract class UnicodeMatcher implements Predicate<Integer> {
  public static final UnicodeMatcher ALL = new UnicodeMatcher() {
    @Override
    public boolean matches(int codePoint) {
      return Character.isValidCodePoint(codePoint);
    }

    @Override
    public boolean matchesAllOf(CharSequence seq) {
      return true;
    }

    @Override
    public boolean matchesNoneOf(CharSequence seq) {
      return checkNotNull(seq).length() == 0;
    }

    @Override
    public boolean matchesAnyOf(CharSequence seq) {
      return checkNotNull(seq).length() > 0;
    }

    @Override
    public int indexIn(CharSequence seq, int fromIndex) {
      int len = checkNotNull(seq).length();
      checkPositionIndex(fromIndex, len);
      return (fromIndex < len) ? fromIndex : -1;
    }

    @Override
    public int lastIndexIn(CharSequence seq) {
      int len = checkNotNull(seq).length();
      if (len > 0) {
        return len - Character.charCount(Character.codePointBefore(seq, len));
      } else {
        return -1;
      }
    }

    @Override
    public int countIn(CharSequence seq) {
      return Character.codePointCount(checkNotNull(seq), 0, seq.length());
    }

    @Override
    public String removeFrom(CharSequence seq) {
      checkNotNull(seq);
      return "";
    }

    @Override
    public String retainFrom(CharSequence seq) {
      checkNotNull(seq);
      return seq.toString();
    }

    @Override
    public String replaceFrom(CharSequence seq, int replacementCodePoint) {
      checkArgument(Character.isValidCodePoint(replacementCodePoint),
          "replacementCodePoint (%s) must be a valid code point", replacementCodePoint);
      String codePointString = String.copyValueOf(Character.toChars(replacementCodePoint));
      return Strings.repeat(codePointString, countIn(seq));
    }

    @Override
    public String replaceFrom(CharSequence seq, CharSequence replacement) {
      return Strings.repeat(checkNotNull(replacement).toString(), countIn(seq));
    }

    @Override
    public UnicodeMatcher negate() {
      return NONE;
    }
  };
  
  public static final UnicodeMatcher NONE = new UnicodeMatcher() {
    @Override
    public boolean matches(int codePoint) {
      return false;
    }

    @Override
    public boolean matchesAllOf(CharSequence seq) {
      return seq.length() == 0;
    }

    @Override
    public boolean matchesNoneOf(CharSequence seq) {
      return true;
    }

    @Override
    public boolean matchesAnyOf(CharSequence seq) {
      return false;
    }

    @Override
    public int indexIn(CharSequence seq) {
      checkNotNull(seq);
      return -1;
    }

    @Override
    public int indexIn(CharSequence seq, int fromIndex) {
      checkNotNull(seq);
      checkPositionIndex(fromIndex, seq.length());
      return -1;
    }

    @Override
    public int lastIndexIn(CharSequence seq) {
      checkNotNull(seq);
      return -1;
    }

    @Override
    public int countIn(CharSequence seq) {
      checkNotNull(seq);
      return 0;
    }

    @Override
    public String removeFrom(CharSequence seq) {
      return checkNotNull(seq).toString();
    }

    @Override
    public String retainFrom(CharSequence seq) {
      checkNotNull(seq);
      return "";
    }

    @Override
    public String replaceFrom(CharSequence seq, int replacementCodePoint) {
      return checkNotNull(seq).toString();
    }

    @Override
    public String replaceFrom(CharSequence seq, CharSequence replacement) {
      return checkNotNull(seq).toString();
    }

    @Override
    public UnicodeMatcher negate() {
      return ALL;
    }
  };
  
  public static final UnicodeMatcher WHITESPACE = new UnicodeMatcher() {
    @Override
    public boolean matches(int codePoint) {
      return Character.isWhitespace(codePoint);
    }
  };

  public static UnicodeMatcher fromCharMatcher(final CharMatcher matcher) {
    return new UnicodeMatcher() {
      @Override
      public boolean matches(int codePoint) {
        return Character.isValidCodePoint(codePoint)
            && !Character.isSupplementaryCodePoint(codePoint)
            && matcher.matches((char) codePoint);
      }

      @Override
      public boolean matchesAllOf(CharSequence string) {
        return matcher.matchesAllOf(string);
      }
    };
  }

  public abstract boolean matches(int codePoint);

  @Override
  public boolean apply(Integer input) {
    return matches(input.intValue());
  }

  public boolean matchesAllOf(CharSequence seq) {
    int len = checkNotNull(seq).length();
    for (int i = 0; i < len;) {
      int codePoint = Character.codePointAt(seq, i);
      if (!matches(codePoint)) {
        return false;
      }
      i += Character.charCount(codePoint);
    }
    return true;
  }

  public boolean matchesNoneOf(CharSequence seq) {
    return indexIn(seq) == -1;
  }

  public boolean matchesAnyOf(CharSequence seq) {
    return !matchesNoneOf(seq);
  }

  /**
   * Returns the first index {@code i} of a code point matching this {@code UnicodeMatcher}, or
   * {@code -1} if no such index exists.
   */
  public int indexIn(CharSequence seq) {
    return indexIn(seq, 0);
  }

  public int indexIn(CharSequence seq, int fromIndex) {
    int len = checkNotNull(seq).length();
    checkPositionIndex(fromIndex, len);
    for (int i = fromIndex; i < len;) {
      int codePoint = Character.codePointAt(seq, i);
      if (matches(codePoint)) {
        return i;
      }
      i += Character.charCount(codePoint);
    }
    return -1;
  }

  public int lastIndexIn(CharSequence seq) {
    for (int i = checkNotNull(seq).length(); i >= 0; i--) {
      int codePoint = Character.codePointBefore(seq, i);
      if (matches(codePoint)) {
        return i;
      }
      i -= Character.charCount(codePoint);
    }
    return -1;
  }

  public int countIn(CharSequence seq) {
    int count = 0;
    int len = checkNotNull(seq).length();
    for (int i = 0; i < len;) {
      int codePoint = Character.codePointAt(seq, i);
      if (matches(codePoint)) {
        count++;
      }
      i += Character.charCount(codePoint);
    }
    return count;
  }
  
  public String removeFrom(CharSequence seq) {
    int len = checkNotNull(seq).length();
    StringBuilder builder = new StringBuilder(len);
    for (int i = 0; i < len;) {
      int codePoint = Character.codePointAt(seq, i);
      if (!matches(codePoint)) {
        builder.appendCodePoint(codePoint);
      }
      i += Character.charCount(codePoint);
    }
    return builder.toString();
  }
  
  public String retainFrom(CharSequence seq) {
    return negate().removeFrom(seq);
  }
  
  public String replaceFrom(CharSequence seq, int replacementCodePoint) {
    checkArgument(Character.isValidCodePoint(replacementCodePoint),
        "replacementCodePoint (%s) must be a valid code point", replacementCodePoint);
    int len = checkNotNull(seq).length();
    StringBuilder builder = new StringBuilder(len);
    for (int i = 0; i < len;) {
      int codePoint = Character.codePointAt(seq, i);
      builder.appendCodePoint(matches(codePoint) ? replacementCodePoint : codePoint);
    }
    return builder.toString();
  }
  
  public String replaceFrom(CharSequence seq, CharSequence replacement) {
    int replaceLen = checkNotNull(replacement).length();
    if (replaceLen == 0) {
      return removeFrom(seq);
    } else if (replaceLen == Character.charCount(Character.codePointAt(replacement, 0))) {
      return replaceFrom(seq, Character.codePointAt(replacement, 0));
    }
    int len = checkNotNull(seq).length();
    StringBuilder result = new StringBuilder(len + (len >>> 1) + 16);
    int prevIndex = indexIn(seq);
    if (prevIndex == -1) {
      return seq.toString();
    }
    result.append(seq, 0, prevIndex);
    result.append(replacement);
    prevIndex += Character.charCount(Character.codePointAt(seq, prevIndex));
    for (int i = indexIn(seq, prevIndex); i != -1; i = indexIn(seq, prevIndex)) {
      int codePoint = Character.codePointAt(seq, prevIndex);
      result.append(seq, prevIndex, i);
      result.append(replacement);
      prevIndex = i + Character.charCount(codePoint);
    }
    result.append(seq, prevIndex, len);
    return result.toString();
  }
  
  public String trimFrom(CharSequence sequence) {
    int len = checkNotNull(sequence).length();
    int first;
    int last;

    for (first = 0; first < len;) {
      int codePoint = Character.codePointAt(sequence, first);
      if (!matches(codePoint)) {
        break;
      }
      first += Character.charCount(codePoint);
    }
    for (last = len; last > first;) {
      int codePoint = Character.codePointBefore(sequence, last);
      if (!matches(codePoint)) {
        break;
      }
      last -= Character.charCount(codePoint);
    }

    return sequence.subSequence(first, last).toString();
  }
  
  public String trimLeadingFrom(CharSequence sequence) {
    int len = checkNotNull(sequence).length();
    int first;

    for (first = 0; first < len;) {
      int codePoint = Character.codePointAt(sequence, first);
      if (!matches(codePoint)) {
        break;
      }
      first += Character.charCount(codePoint);
    }

    return sequence.subSequence(first, len).toString();
  }
  
  public String trimTrailingFrom(CharSequence sequence) {
    int len = sequence.length();
    int last;

    for (last = len; last > 0;) {
      int codePoint = Character.codePointBefore(sequence, last);
      if (!matches(codePoint)) {
        break;
      }
      last -= Character.charCount(codePoint);
    }

    return sequence.subSequence(0, last).toString();
  }
  
  public String collapseFrom(CharSequence sequence, char replacement) {
    int first = indexIn(sequence);
    if (first == -1) {
      return sequence.toString();
    }

    StringBuilder builder = new StringBuilder(sequence.length())
        .append(sequence.subSequence(0, first))
        .append(replacement);
    boolean in = true;
    for (int i = first + 1; i < sequence.length();) {
      int codePoint = Character.codePointAt(sequence, i);
      if (matches(codePoint)) {
        if (!in) {
          builder.append(replacement);
          in = true;
        }
      } else {
        builder.appendCodePoint(codePoint);
        in = false;
      }
      i += Character.charCount(codePoint);
    }
    return builder.toString();
  }
  
  public String trimAndCollapseFrom(CharSequence sequence, char replacement) {
    int first = negate().indexIn(sequence);
    if (first == -1) {
      return ""; // everything matches. nothing's left.
    }
    int len = sequence.length();
    StringBuilder builder = new StringBuilder(len);
    boolean inMatchingGroup = false;
    for (int i = first; i < len;) {
      int codePoint = Character.codePointAt(sequence, i);
      if (matches(codePoint)) {
        inMatchingGroup = true;
      } else {
        if (inMatchingGroup) {
          builder.append(replacement);
          inMatchingGroup = false;
        }
        builder.append(codePoint);
      }
      i += Character.charCount(codePoint);
    }
    return builder.toString();
  }
  
  public UnicodeMatcher negate() {
    return new NegatedMatcher(this);
  }
  
  private final class NegatedMatcher extends UnicodeMatcher {
    private final UnicodeMatcher positive;

    NegatedMatcher(UnicodeMatcher positive) {
      this.positive = positive;
    }

    @Override
    public boolean matches(int codePoint) {
      return Character.isValidCodePoint(codePoint) && !positive.matches(codePoint);
    }

    @Override
    public boolean matchesAllOf(CharSequence seq) {
      return positive.matchesNoneOf(seq);
    }

    @Override
    public boolean matchesNoneOf(CharSequence seq) {
      return positive.matchesAllOf(seq);
    }

    @Override
    public UnicodeMatcher negate() {
      return positive;
    }

    @Override
    public String toString() {
      return positive.toString() + ".negate()";
    }
  }
}
