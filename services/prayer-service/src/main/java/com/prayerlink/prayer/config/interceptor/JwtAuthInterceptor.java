package com.prayerlink.prayer.config.interceptor;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.prayerlink.prayer.service.security.JwtUtil;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    public JwtAuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(
            @Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull Object handler) {
        String authToken = extractCookie(request);
        if (authToken == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authentication token");
        }

        try {
            DecodedJWT decodedJWT = jwtUtil.verifyToken(authToken);
            String email = decodedJWT.getClaim("email").asString();
            if (email == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token format");
            }
            request.setAttribute("userEmail", email);
            return true;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid or expired token");
        }
    }

    private String extractCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(c -> "pl-auth-token".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
