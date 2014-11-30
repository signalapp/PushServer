package org.whispersystems.pushserver.util;

import org.jivesoftware.smack.util.Base64;

public class AuthHelper {
  public static String getAuthHeader(String name, String password) {
    return "Basic " + Base64.encodeBytes((name + ":" + password).getBytes(), Base64.DONT_BREAK_LINES);
  }
}
