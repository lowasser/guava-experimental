package com.google.common.io;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkPositionIndexes;
import static java.math.RoundingMode.CEILING;
import static java.math.RoundingMode.UNNECESSARY;

import com.google.common.math.IntMath;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Arrays;

public enum BaseEncoding {
  BASE64("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/", 3, 4),
  BASE64_URL("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_", 3, 4),
  BASE32("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567", 5, 8),
  BASE32_HEX("0123456789ABCDEFGHIJKLMNOPQRSTUV", 5, 8),
  BASE16("0123456789ABCDEF", 1, 2);

  final String table;
  final int[] lookupTable;
  final int bytesPerChunk;
  final int charsPerChunk;
  final int bitsPerChar;

  private BaseEncoding(String table, int bytesPerChunk, int charsPerChunk) {
    this.table = table;
    this.bytesPerChunk = bytesPerChunk;
    this.charsPerChunk = charsPerChunk;
    this.bitsPerChar = IntMath.log2(table.length(), UNNECESSARY);

    this.lookupTable = new int[256];
    Arrays.fill(lookupTable, -1);
    for (int i = 0; i < table.length(); i++) {
      lookupTable[table.charAt(i)] = i;
    }
  }

  // Returns true if more encodings are required or expected.
  private boolean encode(ByteBuffer src, CharBuffer dst, boolean isEOF) {
    int mask = (1 << bitsPerChar) - 1;

    while (src.remaining() >= bytesPerChunk && dst.remaining() >= charsPerChunk) {
      long chunk = 0;
      for (int i = 0; i < bytesPerChunk; i++) {
        chunk <<= Byte.SIZE;
        chunk |= src.get() & 0xff;
      }
      for (int i = (charsPerChunk - 1) * bitsPerChar; i >= 0; i -= bitsPerChar) {
        int x = (int) ((chunk >>> i) & mask);
        dst.put(table.charAt(x));
      }
    }

    if (!isEOF || dst.remaining() < charsPerChunk) {
      return true;
    }

    int left = src.remaining();
    if (left == 0) {
      return false;
    }
    long lastChunk = 0;
    for (int i = bytesPerChunk - 1; src.hasRemaining(); i--) {
      lastChunk |= (src.get() & 0xffL) << (Byte.SIZE * i);
    }

    int outChars = IntMath.divide(left * Byte.SIZE, bitsPerChar, CEILING);
    for (int i = 0; i < outChars; i++) {
      int x = (int) ((lastChunk >>> ((charsPerChunk - 1 - i) * bitsPerChar)) & mask);
      dst.put(table.charAt(x));
    }
    for (int i = outChars; i < charsPerChunk; i++) {
      dst.put('=');
    }
    return false;
  }

  private long index(char c) {
    if (!(c < lookupTable.length && lookupTable[c] != -1)) {
      throw new IllegalArgumentException(c + " is not a valid character in this encoding");
    }
    return lookupTable[c];
  }

  private boolean decode(CharBuffer src, ByteBuffer dst, boolean isEOF) {
    // If isEOF, make sure there's at least one full chunk left in src. 
    while ((src.remaining() - (isEOF ? 1 : 0)) >= charsPerChunk
        && dst.remaining() >= bytesPerChunk) {
      long chunk = 0;
      for (int i = 0; i < charsPerChunk; i++) {
        chunk <<= bitsPerChar;
        chunk |= index(src.get());
      }
      for (int i = (bytesPerChunk - 1) * Byte.SIZE; i >= 0; i -= Byte.SIZE) {
        dst.put((byte) (chunk >>> i));
      }
    }
    int position = src.position();
    if (!isEOF) {
      return true;
    }
    if (src.remaining() < charsPerChunk) {
      throw new IllegalArgumentException("Incomplete chunk in decoding " + toString());
    }
    long chunk = 0;
    int paddingChars = charsPerChunk;
    for (paddingChars--; paddingChars >= 0; paddingChars--) {
      char c = src.get();
      if (c == '=') {
        paddingChars++;
        break;
      }
      chunk |= index(c) << (paddingChars * bitsPerChar);
    }
    while (src.hasRemaining()) {
      char c = src.get();
      if (c != '=') {
        throw new IllegalArgumentException("Illegal character " + c + " after start of padding");
      }
    }
    int leftoverBytes = IntMath.divide(paddingChars * bitsPerChar, Byte.SIZE, CEILING);
    if (dst.remaining() < bytesPerChunk - leftoverBytes) {
      src.position(position);
      return true;
    }
    for (paddingChars = bytesPerChunk - 1; paddingChars >= leftoverBytes; paddingChars--) {
      dst.put((byte) (chunk >>> (paddingChars * Byte.SIZE)));
    }
    return false;
  }

  public String encode(ByteBuffer bytes) {
    CharBuffer buf = CharBuffer.allocate(charsPerChunk * 8);
    StringBuilder builder = new StringBuilder(bytes.remaining() * charsPerChunk / bytesPerChunk);
    while (encode(bytes, buf, true)) {
      buf.flip();
      builder.append(buf.array(), 0, buf.limit());
      buf.clear();
    }
    buf.flip();
    builder.append(buf.array(), 0, buf.limit());
    buf.clear();
    return builder.toString();
  }

  public byte[] decode(CharSequence s) {
    int n = s.length();
    if (n == 0) {
      return new byte[0];
    }
    checkArgument(
        n % charsPerChunk == 0,
        "s.length() (%s) must be a multiple of %s",
        n, charsPerChunk);
    CharBuffer charBuf = CharBuffer.wrap(s);
    ByteBuffer output = ByteBuffer.allocate((n / charsPerChunk) * bytesPerChunk);
    boolean cont = true;
    while (cont) {
      cont = decode(charBuf, output, true);
    }
    output.flip();
    byte[] result = new byte[output.limit()];
    output.get(result);
    return result;
  }

  /**
   * Returns an {@code OutputStream} that that writes bytes as base-encoded text to the specified
   * {@code Writer}.
   * 
   * <p>This can be used for "streaming" base encoding.
   */
  public OutputStream encodingStream(final Writer writer) {
    checkNotNull(writer);
    return new OutputStream() {
      private final ByteBuffer byteBuf = ByteBuffer.allocate(8 * bytesPerChunk);
      private final CharBuffer charBuf = CharBuffer.allocate(8 * charsPerChunk);
      private boolean isClosed = false;

      private void checkNotClosed() throws IOException {
        if (isClosed) {
          throw new IOException("Cannot write to closed output stream");
        }
      }

      private void munch(boolean isEOF) throws IOException {
        byteBuf.flip(); // prepare to read from byteBuf
        while (byteBuf.remaining() > bytesPerChunk) {
          // charBuf is in write mode
          encode(byteBuf, charBuf, isEOF);
          if (!charBuf.hasRemaining()) {
            // charBuf is full; pump it into the writer
            charBuf.flip(); // read from charBuf
            writer.write(charBuf.array(), charBuf.arrayOffset(), charBuf.limit());
            charBuf.clear(); // write to charBuf
          }
        }
        byteBuf.compact(); // prepare to write to byteBuf
      }

      @Override
      public void write(int b) throws IOException {
        if (isClosed) {
          throw new IOException("Cannot write to closed output stream");
        } else if (!byteBuf.hasRemaining()) {
          munch(false);
        }
        
        byteBuf.put((byte) b);
      }

      @Override
      public void write(byte[] b, int off, int len) throws IOException {
        checkNotClosed();
        checkPositionIndexes(off, off + len, b.length);
        while (len > 0) {
          if (!byteBuf.hasRemaining()) {
            munch(false);
          }
          int nBytes = Math.min(len, byteBuf.remaining());
          byteBuf.put(b, off, nBytes);
          off += nBytes;
          len -= nBytes;
        }
      }

      @Override
      public void flush() throws IOException {
        checkNotClosed();
        munch(false);
        charBuf.flip(); // charBuf is in read mode
        if (charBuf.hasRemaining()) {
          writer.write(charBuf.array(), charBuf.arrayOffset(), charBuf.limit()); 
        }
        charBuf.clear(); // charBuf is in write mode
        writer.flush();
      }

      @Override
      public void close() throws IOException {
        flush();
        munch(true);
        charBuf.flip(); // charBuf is in read mode
        if (charBuf.hasRemaining()) {
          writer.write(charBuf.array(), charBuf.arrayOffset(), charBuf.limit()); 
        }
        charBuf.clear(); // charBuf is in write mode
        writer.close();
        isClosed = true;
      }
    };
  }

  /**
   * Given a {@code Reader} of encoded characters from this encoding, returns a {@code InputStream}
   * of the decoded bytes.
   * 
   * <p>
   * This can be used for "streaming" base decoding.
   */
  public InputStream decode(final Reader reader) {
    checkNotNull(reader);
    return new InputStream() {
      private final CharBuffer charBuf = CharBuffer.allocate(8 * charsPerChunk);
      private final ByteBuffer byteBuf = ByteBuffer.allocate(8 * bytesPerChunk);
      private boolean seenEOF = false;

      @Override
      public int available() throws IOException {
        return byteBuf.remaining();
      }

      @Override
      public int read() throws IOException {
        byte[] tmp = new byte[1];
        if (read(tmp) == -1) {
          return -1;
        } else {
          return tmp[0];
        }
      }

      private void munch() throws IOException {
        charBuf.compact(); // prepare charBuf for writing
        while (!seenEOF && charBuf.remaining() < charsPerChunk) {
          int charsRead = reader.read(charBuf);
          if (charsRead == -1) {
            seenEOF = true;
          }
        }
        charBuf.flip(); // prepare charBuf for reading
        byteBuf.clear(); // prepare byteBuf for writing
        decode(charBuf, byteBuf, seenEOF);
        byteBuf.flip(); // prepare byteBuf for reading
      }

      @Override
      public int read(byte[] b, int off, int len) throws IOException {
        if (len == 0) {
          return 0;
        } else if (!byteBuf.hasRemaining()) {
          munch();
          if (!byteBuf.hasRemaining() && seenEOF) {
            return -1;
          }
        }
        int numBytes = Math.min(len, byteBuf.remaining());
        byteBuf.get(b, off, numBytes);
        return numBytes;
      }

      @Override
      public void close() throws IOException {
        reader.close();
        seenEOF = true;
      }

      @Override
      public synchronized void reset() throws IOException {
        reader.reset();
      }
    };
  }
}
