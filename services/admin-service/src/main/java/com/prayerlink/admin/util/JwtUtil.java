package com.prayerlink.admin.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {

  @Value("${jwt.secret:default-secret-key-must-be-very-long-and-secure-for-hmac-sha-256-prayer-link}")
  private String secret;

  public String generateToken(String adminId, String username, String role, String groupId) {
    Algorithm algorithm = Algorithm.HMAC256(secret);
    return JWT.create()
        .withSubject(adminId)
        .withClaim("username", username)
        .withClaim("role", role)
        .withClaim("groupId", groupId)
        .withIssuedAt(new Date())
        .withExpiresAt(new Date(System.currentTimeMillis() + 24 * 3600 * 1000)) // 24 hours
        .sign(algorithm);
  }

  public String generateTokenForIntercessor(String email, String name) {
    Algorithm algorithm = Algorithm.HMAC256(secret);
    return JWT.create()
        .withSubject(email)
        .withClaim("email", email)
        .withClaim("name", name)
        .withClaim("role", "INTERCESSOR")
        .withIssuedAt(new Date())
        .withExpiresAt(new Date(System.currentTimeMillis() + 30L * 24 * 3600 * 1000)) // 30 days
        .sign(algorithm);
  }

  public DecodedJWT verifyToken(String token) {
    Algorithm algorithm = Algorithm.HMAC256(secret);
    JWTVerifier verifier = JWT.require(algorithm).build();
    return verifier.verify(token);
  }
}
