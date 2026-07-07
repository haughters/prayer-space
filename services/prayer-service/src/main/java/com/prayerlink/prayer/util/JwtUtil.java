package com.prayerlink.prayer.util;

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

  public DecodedJWT verifyToken(String token) {
    Algorithm algorithm = Algorithm.HMAC256(secret);
    JWTVerifier verifier = JWT.require(algorithm).build();
    return verifier.verify(token);
  }
}
