package com.google.common.io;

import com.google.common.base.Charsets;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

import junit.framework.TestCase;

public class BaseEncodingTest extends TestCase {
  public void testBase64WikipediaExample() {
    String s = "Man is distinguished, not only by his reason, but by this singular passion from other animals,"
        + " which is a lust of the mind, that by a perseverance of delight in the continued and indefatigable"
        + " generation of knowledge, exceeds the short vehemence of any carnal pleasure.";
    byte[] bytes = s.getBytes(Charsets.US_ASCII);
    String expected = "TWFuIGlzIGRpc3Rpbmd1aXNoZWQsIG5vdCBvbmx5IGJ5IGhpcyByZWFzb24sIGJ1dCBieSB0aGlz"
        + "IHNpbmd1bGFyIHBhc3Npb24gZnJvbSBvdGhlciBhbmltYWxzLCB3aGljaCBpcyBhIGx1c3Qgb2Yg"
        + "dGhlIG1pbmQsIHRoYXQgYnkgYSBwZXJzZXZlcmFuY2Ugb2YgZGVsaWdodCBpbiB0aGUgY29udGlu"
        + "dWVkIGFuZCBpbmRlZmF0aWdhYmxlIGdlbmVyYXRpb24gb2Yga25vd2xlZGdlLCBleGNlZWRzIHRo"
        + "ZSBzaG9ydCB2ZWhlbWVuY2Ugb2YgYW55IGNhcm5hbCBwbGVhc3VyZS4=";
    String encoded = BaseEncoding.BASE64.encode(ByteBuffer.wrap(bytes));
    assertEquals(expected, encoded);
  }

  public void testEncodingDecoding() {
    Random rng = new Random(31415926);
    for (BaseEncoding enc : BaseEncoding.values()) {
      for (int i = 0; i < 1000; i++) {
        byte[] input = new byte[rng.nextInt(1024)];
        rng.nextBytes(input);
        String encoded = enc.encode(ByteBuffer.wrap(input));
        byte[] output = enc.decode(encoded);
        assertTrue(Arrays.equals(output, input));
      }
    }
  }
}
