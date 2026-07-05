package com.prayerlink.prayer.util;

import static org.junit.jupiter.api.Assertions.*;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class JwtUtilTest {

    private JwtUtil jwtUtil;
    private final String secret = "my-test-secret-key-must-be-very-long-and-secure-for-hmac-sha-256-prayer-link";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", secret);
    }

    @Test
    void verifyTokenReturnsDecodedJwt() {
        Algorithm algorithm = Algorithm.HMAC256(secret);
        String token = JWT.create()
                .withSubject("user@example.com")
                .sign(algorithm);

        DecodedJWT decoded = jwtUtil.verifyToken(token);
        assertEquals("user@example.com", decoded.getSubject());
    }

    @Test
    void verifyTokenWithTamperedSignatureThrowsException() {
        Algorithm algorithm = Algorithm.HMAC256(secret);
        String token = JWT.create()
                .withSubject("user@example.com")
                .sign(algorithm);
        
        assertThrows(Exception.class, () -> jwtUtil.verifyToken(token + "x"));
    }
}
