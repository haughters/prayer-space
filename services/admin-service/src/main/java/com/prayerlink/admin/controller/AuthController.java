package com.prayerlink.admin.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.prayerlink.admin.model.Admin;
import com.prayerlink.admin.model.IntercessorAccount;
import com.prayerlink.admin.repository.AdminRepository;
import com.prayerlink.admin.repository.IntercessorAccountRepository;
import com.prayerlink.admin.util.JwtUtil;
import com.prayerlink.common.dto.GroupDTO;
import com.prayerlink.common.dto.GroupMemberDTO;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {

  private static final Logger log = LoggerFactory.getLogger(AuthController.class);
  private static final String COOKIE_NAME = "pl-auth-token";

  private final AdminRepository adminRepository;
  private final IntercessorAccountRepository intercessorAccountRepository;
  private final JwtUtil jwtUtil;
  private final PasswordEncoder passwordEncoder;
  private final RestTemplate restTemplate;

  @Value("${services.group-service.url:http://localhost:8083}")
  private String groupServiceUrl;

  @Value("${cookie.secure:true}")
  private boolean cookieSecure;

  public AuthController(
      AdminRepository adminRepository,
      IntercessorAccountRepository intercessorAccountRepository,
      JwtUtil jwtUtil,
      PasswordEncoder passwordEncoder,
      RestTemplate restTemplate) {
    this.adminRepository = adminRepository;
    this.intercessorAccountRepository = intercessorAccountRepository;
    this.jwtUtil = jwtUtil;
    this.passwordEncoder = passwordEncoder;
    this.restTemplate = restTemplate;
  }

  @GetMapping("/status")
  public ResponseEntity<Map<String, Object>> getStatus(
      @CookieValue(name = COOKIE_NAME, required = false) String token) {
    boolean initialized = !adminRepository.isEmpty();
    Map<String, Object> response = new HashMap<>();
    response.put("initialized", initialized);

    if (token != null && !token.trim().isEmpty()) {
      try {
        DecodedJWT jwt = jwtUtil.verifyToken(token);
        String role = jwt.getClaim("role").asString();
        response.put("authenticated", true);
        response.put("role", role);

        if ("APP_ADMIN".equals(role) || "GROUP_ADMIN".equals(role)) {
          response.put("username", jwt.getClaim("username").asString());
          response.put("groupId", jwt.getClaim("groupId").asString());
          response.put("email", null);
          response.put("name", null);
        } else if ("INTERCESSOR".equals(role)) {
          String email = jwt.getClaim("email").asString();
          String name = jwt.getClaim("name").asString();
          response.put("email", email);
          response.put("name", name);
          response.put("username", null);
          response.put("groupId", null);

          // Fetch group memberships
          List<GroupMemberDTO> members = List.of();
          try {
            ResponseEntity<List<GroupMemberDTO>> searchRes = restTemplate.exchange(
                groupServiceUrl + "/api/groups/members/search?email=" + email,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<GroupMemberDTO>>() {});
            if (searchRes.getBody() != null) {
              members = searchRes.getBody();
            }
          } catch (Exception e) {
            log.error("Failed to fetch groups for status intercessor " + email, e);
          }

          List<Map<String, String>> groups = new ArrayList<>();
          for (GroupMemberDTO member : members) {
            try {
              GroupDTO group = restTemplate.getForObject(
                  groupServiceUrl + "/api/groups/" + member.getGroupId(), GroupDTO.class);
              if (group != null) {
                groups.add(Map.of(
                    "groupId", group.getGroupId(),
                    "name", group.getName()
                ));
              }
            } catch (Exception e) {
              log.error("Failed to fetch group details for group ID: " + member.getGroupId(), e);
            }
          }
          response.put("groups", groups);
        }
        return ResponseEntity.ok(response);
      } catch (Exception e) {
        log.warn("Invalid JWT cookie provided to AuthController: {}", e.getMessage());
      }
    }

    response.put("authenticated", false);
    response.put("role", null);
    response.put("groupId", null);
    response.put("username", null);
    response.put("email", null);
    response.put("name", null);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(
      @RequestBody Map<String, String> body,
      HttpServletResponse response) {
    String identifier = body.get("identifier");
    String password = body.get("password");

    if (identifier == null || password == null || identifier.trim().isEmpty() || password.trim().isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("error", "Username/Email and password are required."));
    }

    String cleanIdentifier = identifier.trim();

    // 1. Try to find in Admins table first (username-based)
    Optional<Admin> adminOpt = adminRepository.findByUsername(cleanIdentifier);
    if (adminOpt.isPresent()) {
      Admin admin = adminOpt.get();
      if (passwordEncoder.matches(password, admin.getPasswordHash())) {
        String token = jwtUtil.generateToken(admin.getAdminId(), admin.getUsername(), admin.getRole(), admin.getGroupId());
        setJwtCookie(response, token);

        Map<String, Object> respBody = new HashMap<>();
        respBody.put("role", admin.getRole());
        respBody.put("groupId", admin.getGroupId() != null ? admin.getGroupId() : "");
        respBody.put("username", admin.getUsername());
        return ResponseEntity.ok(respBody);
      }
    }

    // 2. Try to find in IntercessorAccounts table (email-based)
    Optional<IntercessorAccount> intercessorOpt = intercessorAccountRepository.findById(cleanIdentifier.toLowerCase());
    if (intercessorOpt.isPresent()) {
      IntercessorAccount intercessor = intercessorOpt.get();
      if (passwordEncoder.matches(password, intercessor.getPasswordHash())) {
        String token = jwtUtil.generateTokenForIntercessor(intercessor.getEmail(), intercessor.getName());
        setJwtCookie(response, token);

        Map<String, Object> respBody = new HashMap<>();
        respBody.put("role", "INTERCESSOR");
        respBody.put("email", intercessor.getEmail());
        respBody.put("name", intercessor.getName());
        return ResponseEntity.ok(respBody);
      }
    }

    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(Map.of("error", "Invalid username/email or password."));
  }

  @PostMapping("/logout")
  public ResponseEntity<?> logout(HttpServletResponse response) {
    clearJwtCookie(response);
    return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
  }

  private void setJwtCookie(HttpServletResponse response, String token) {
    String secureFlag = cookieSecure ? " Secure;" : "";
    response.addHeader(
        "Set-Cookie",
        String.format(
            "%s=%s; Max-Age=2592000; Path=/; HttpOnly;%s SameSite=Lax", COOKIE_NAME, token, secureFlag));
  }

  private void clearJwtCookie(HttpServletResponse response) {
    String secureFlag = cookieSecure ? " Secure;" : "";
    response.addHeader(
        "Set-Cookie",
        String.format("%s=; Max-Age=0; Path=/; HttpOnly;%s SameSite=Lax", COOKIE_NAME, secureFlag));
  }
}
