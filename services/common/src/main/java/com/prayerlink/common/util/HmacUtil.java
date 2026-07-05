package com.prayerlink.common.util;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class HmacUtil {
  private static final String HMAC_SHA256 = "HmacSHA256";

  public static String generateToken(String data, String secret) {
    try {
      byte[] hash =
          generateHmacSha256(
              data.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate HMAC token", e);
    }
  }

  public static boolean verifyToken(String data, String token, String secret) {
    if (token == null) return false;
    String expectedToken = generateToken(data, secret);
    return expectedToken.equals(token);
  }

  private static byte[] generateHmacSha256(byte[] data, byte[] key)
      throws NoSuchAlgorithmException, InvalidKeyException {
    Mac sha256Hmac = Mac.getInstance(HMAC_SHA256);
    SecretKeySpec secretKey = new SecretKeySpec(key, HMAC_SHA256);
    sha256Hmac.init(secretKey);
    return sha256Hmac.doFinal(data);
  }
}
