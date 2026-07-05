package com.prayerlink.admin.util;

import static org.junit.jupiter.api.Assertions.*;

import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        String testSecret = "test-secret-key-must-be-very-long-and-secure-for-hmac-sha-256-prayer-link";
        ReflectionTestUtils.setField(jwtUtil, "secret", testSecret);
    }

    @Test
    void generateTokenContainsExpectedClaims() {
        String adminId = "admin123";
        String username = "testadmin";
        String role = "APP_ADMIN";
        String groupId = "group456";

        String token = jwtUtil.generateToken(adminId, username, role, groupId);
        assertNotNull(token);

        DecodedJWT decoded = jwtUtil.verifyToken(token);
        assertEquals(adminId, decoded.getSubject());
        assertEquals(username, decoded.getClaim("username").asString());
        assertEquals(role, decoded.getClaim("role").asString());
        assertEquals(groupId, decoded.getClaim("groupId").asString());
    }

    @Test
    void validateTokenWithValidTokenReturnsClaims() {
        String email = "intercessor@example.com";
        String name = "Jane Doe";

        String token = jwtUtil.generateTokenForIntercessor(email, name);
        assertNotNull(token);

        DecodedJWT decoded = jwtUtil.verifyToken(token);
        assertEquals(email, decoded.getSubject());
        assertEquals(email, decoded.getClaim("email").asString());
        assertEquals(name, decoded.getClaim("name").asString());
        assertEquals("INTERCESSOR", decoded.getClaim("role").asString());
    }

    @Test
    void validateTokenWhenTamperedThrowsException() {
        String token = jwtUtil.generateToken("admin123", "user", "APP_ADMIN", "group456");
        String tamperedToken = token + "modified";

        assertThrows(SignatureVerificationException.class, () -> {
            jwtUtil.verifyToken(tamperedToken);
        });
    }

    @Test
    void validateTokenWithWrongSecretThrowsException() {
        String token = jwtUtil.generateToken("admin123", "user", "APP_ADMIN", "group456");
        
        JwtUtil otherJwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(otherJwtUtil, "secret", "different-secret-key-must-be-very-long-and-secure-for-hmac-sha-256-prayer-link");

        assertThrows(SignatureVerificationException.class, () -> {
            otherJwtUtil.verifyToken(token);
        });
    }
}
