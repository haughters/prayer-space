package com.prayerlink.identity.util;

import static org.junit.jupiter.api.Assertions.*;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "my-test-secret-key-must-be-very-long-and-secure-for-hmac-sha-256-prayer-link");
    }

    @Test
    void generateTokenContainsClaims() {
        String token = jwtUtil.generateToken("user@example.com", "User Name");
        assertNotNull(token);

        DecodedJWT decoded = jwtUtil.verifyToken(token);
        assertEquals("user@example.com", decoded.getSubject());
        assertEquals("user@example.com", decoded.getClaim("email").asString());
        assertEquals("User Name", decoded.getClaim("name").asString());
        assertEquals("INTERCESSOR", decoded.getClaim("role").asString());
    }

    @Test
    void verifyTokenWithTamperedSignatureThrowsException() {
        String token = jwtUtil.generateToken("user@example.com", "User Name");
        String tampered = token + "x";
        assertThrows(Exception.class, () -> jwtUtil.verifyToken(tampered));
    }
}
