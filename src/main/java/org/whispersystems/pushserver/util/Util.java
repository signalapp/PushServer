package org.whispersystems.pushserver.util;


public class Util {

  public static void sleep(long timeMillis) {
    try {
      Thread.sleep(timeMillis);
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
  }

  public static boolean isEmpty(String value) {
    return value == null || value.isEmpty();
  }

}
