package com.prayerlink.common.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class HmacUtilTest {

    private final String secret = "my-secure-hmac-sha-256-test-secret-key-123456789";

    @Test
    void generateTokenProducesValidSignature() {
        String data = "group123:intercessor@example.com:1774829399";
        String token = HmacUtil.generateToken(data, secret);
        assertNotNull(token);
        assertFalse(token.isEmpty());
        
        // Verification round-trip
        assertTrue(HmacUtil.verifyToken(data, token, secret));
    }

    @Test
    void verifyTokenWithValidTokenReturnsTrue() {
        String data = "some-payload-data";
        String token = HmacUtil.generateToken(data, secret);
        assertTrue(HmacUtil.verifyToken(data, token, secret));
    }

    @Test
    void verifyTokenWithTamperedPayloadReturnsFalse() {
        String data = "original-payload-data";
        String token = HmacUtil.generateToken(data, secret);
        
        // Attempting to verify with modified data should fail
        assertFalse(HmacUtil.verifyToken("modified-payload-data", token, secret));
    }

    @Test
    void verifyTokenWithDifferentSecretReturnsFalse() {
        String data = "payload-data";
        String token = HmacUtil.generateToken(data, secret);
        
        String wrongSecret = "wrong-hmac-sha-256-secret-key";
        assertFalse(HmacUtil.verifyToken(data, token, wrongSecret));
    }

    @Test
    void verifyTokenWithNullTokenReturnsFalse() {
        assertFalse(HmacUtil.verifyToken("data", null, secret));
    }
}
