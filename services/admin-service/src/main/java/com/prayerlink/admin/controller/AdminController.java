package com.prayerlink.admin.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.prayerlink.admin.model.Admin;
import com.prayerlink.admin.model.Prayer;
import com.prayerlink.admin.model.PrayerUpdate;
import com.prayerlink.admin.repository.AdminRepository;
import com.prayerlink.admin.repository.PrayerRepository;
import com.prayerlink.admin.util.JwtUtil;
import com.prayerlink.common.dto.GroupDTO;
import com.prayerlink.common.dto.GroupMemberDTO;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

  private static final Logger log = LoggerFactory.getLogger(AdminController.class);
  private static final String COOKIE_NAME = "pl-auth-token";
  private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");

  private final AdminRepository adminRepository;
  private final PrayerRepository prayerRepository;
  private final JwtUtil jwtUtil;
  private final PasswordEncoder passwordEncoder;
  private final RestTemplate restTemplate;

  @Value("${services.group-service.url:http://localhost:8083}")
  private String groupServiceUrl;

  @Value("${services.prayer-service.url:http://localhost:8082}")
  private String prayerServiceUrl;

  @Value("${cookie.secure:true}")
  private boolean cookieSecure;

  public AdminController(
      AdminRepository adminRepository,
      PrayerRepository prayerRepository,
      JwtUtil jwtUtil,
      PasswordEncoder passwordEncoder,
      RestTemplate restTemplate) {
    this.adminRepository = adminRepository;
    this.prayerRepository = prayerRepository;
    this.jwtUtil = jwtUtil;
    this.passwordEncoder = passwordEncoder;
    this.restTemplate = restTemplate;
  }

  // === AUTHENTICATION ENDPOINTS ===

  @GetMapping("/status")
  public ResponseEntity<Map<String, Object>> getStatus(
      @CookieValue(name = COOKIE_NAME, required = false) String token) {
    boolean initialized = !adminRepository.isEmpty();
    Map<String, Object> response = new HashMap<>();
    response.put("initialized", initialized);

    if (token != null && !token.trim().isEmpty()) {
      try {
        DecodedJWT jwt = jwtUtil.verifyToken(token);
        response.put("authenticated", true);
        response.put("role", jwt.getClaim("role").asString());
        response.put("groupId", jwt.getClaim("groupId").asString());
        response.put("username", jwt.getClaim("username").asString());
        return ResponseEntity.ok(response);
      } catch (Exception e) {
        log.warn("Invalid JWT cookie provided: {}", e.getMessage());
      }
    }

    response.put("authenticated", false);
    response.put("role", null);
    response.put("groupId", null);
    response.put("username", null);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/setup")
  public ResponseEntity<?> setup(
      @RequestBody Map<String, String> body, HttpServletResponse response) {
    if (!adminRepository.isEmpty()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(Map.of("error", "An administrator already exists. Please log in."));
    }

    String username = body.get("username");
    String password = body.get("password");

    if (username == null || username.trim().length() < 3 || password == null || password.length() < 8) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", "Username must be >= 3 chars, password >= 8 chars."));
    }

    Admin admin =
        Admin.builder()
            .adminId(UUID.randomUUID().toString())
            .username(username.trim())
            .passwordHash(passwordEncoder.encode(password))
            .role("APP_ADMIN")
            .groupId(null)
            .createdAt(Instant.now())
            .build();

    adminRepository.save(admin);

    String token = jwtUtil.generateToken(admin.getAdminId(), admin.getUsername(), admin.getRole(), null);
    setJwtCookie(response, token);

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Map.of("role", admin.getRole(), "groupId", ""));
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(
      @RequestBody Map<String, String> body, HttpServletResponse response) {
    String username = body.get("username");
    String password = body.get("password");

    if (username == null || password == null) {
      return ResponseEntity.badRequest().build();
    }

    Optional<Admin> adminOpt = adminRepository.findByUsername(username.trim());
    if (adminOpt.isEmpty() || !passwordEncoder.matches(password, adminOpt.get().getPasswordHash())) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(Map.of("error", "Invalid username or password."));
    }

    Admin admin = adminOpt.get();
    String token = jwtUtil.generateToken(admin.getAdminId(), admin.getUsername(), admin.getRole(), admin.getGroupId());
    setJwtCookie(response, token);

    Map<String, Object> respBody = new HashMap<>();
    respBody.put("role", admin.getRole());
    respBody.put("groupId", admin.getGroupId() != null ? admin.getGroupId() : "");
    respBody.put("username", admin.getUsername());
    return ResponseEntity.ok(respBody);
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletResponse response) {
    clearJwtCookie(response);
    return ResponseEntity.noContent().build();
  }

  // === ADMIN ACCOUNT ENDPOINTS (APP_ADMIN ONLY) ===

  @GetMapping("/admins")
  public ResponseEntity<List<Map<String, Object>>> getAdmins(
      @CookieValue(name = COOKIE_NAME, required = false) String token) {
    checkAuth(token, "APP_ADMIN");

    List<Map<String, Object>> list =
        adminRepository.findAll().stream()
            .map(
                a -> {
                  Map<String, Object> m = new HashMap<>();
                  m.put("adminId", a.getAdminId());
                  m.put("username", a.getUsername());
                  m.put("role", a.getRole());
                  m.put("groupId", a.getGroupId() != null ? a.getGroupId() : "");
                  m.put("createdAt", a.getCreatedAt());
                  return m;
                })
            .collect(Collectors.toList());

    return ResponseEntity.ok(list);
  }

  @PostMapping("/admins")
  public ResponseEntity<?> createAdmin(
      @CookieValue(name = COOKIE_NAME, required = false) String token,
      @RequestBody Map<String, String> body) {
    checkAuth(token, "APP_ADMIN");

    String username = body.get("username");
    String password = body.get("password");
    String role = body.get("role"); // APP_ADMIN or GROUP_ADMIN
    String groupId = body.get("groupId");

    if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty() || role == null) {
      return ResponseEntity.badRequest().body(Map.of("error", "Missing fields"));
    }

    if (adminRepository.findByUsername(username.trim()).isPresent()) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Username already exists."));
    }

    Admin admin =
        Admin.builder()
            .adminId(UUID.randomUUID().toString())
            .username(username.trim())
            .passwordHash(passwordEncoder.encode(password))
            .role(role)
            .groupId("GROUP_ADMIN".equals(role) ? groupId : null)
            .createdAt(Instant.now())
            .build();

    adminRepository.save(admin);

    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("adminId", admin.getAdminId()));
  }

  @DeleteMapping("/admins/{adminId}")
  public ResponseEntity<?> deleteAdmin(
      @CookieValue(name = COOKIE_NAME, required = false) String token,
      @PathVariable("adminId") String adminId) {
    DecodedJWT auth = checkAuth(token, "APP_ADMIN");

    if (auth.getSubject().equals(adminId)) {
      return ResponseEntity.badRequest().body(Map.of("error", "You cannot delete your own account."));
    }

    if (adminRepository.countAppAdmins() <= 1) {
      // Find role of admin being deleted
      Optional<Admin> target = adminRepository.findById(adminId);
      if (target.isPresent() && "APP_ADMIN".equals(target.get().getRole())) {
        return ResponseEntity.badRequest().body(Map.of("error", "Cannot delete the last app administrator."));
      }
    }

    adminRepository.delete(adminId);
    return ResponseEntity.noContent().build();
  }

  // === PRAYER DASHBOARD ENDPOINTS ===

  @GetMapping("/prayers")
  public ResponseEntity<Map<String, Object>> getPrayers(
      @CookieValue(name = COOKIE_NAME, required = false) String token,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size,
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "groupId", required = false) String groupId,
      @RequestParam(name = "fromDate", required = false) String fromDateStr,
      @RequestParam(name = "toDate", required = false) String toDateStr) {

    DecodedJWT auth = checkAuth(token, "APP_ADMIN", "GROUP_ADMIN");
    String resolvedGroupId = groupId;

    // Enforce role-based scoping
    if ("GROUP_ADMIN".equals(auth.getClaim("role").asString())) {
      resolvedGroupId = auth.getClaim("groupId").asString();
    }

    Instant fromDate = null;
    Instant toDate = null;
    if (fromDateStr != null && !fromDateStr.trim().isEmpty()) {
      fromDate = Instant.parse(fromDateStr.trim() + "T00:00:00Z");
    }
    if (toDateStr != null && !toDateStr.trim().isEmpty()) {
      toDate = Instant.parse(toDateStr.trim() + "T23:59:59Z");
    }

    List<Prayer> prayers = prayerRepository.searchPrayers(status, resolvedGroupId, fromDate, toDate);
    int totalCount = prayers.size();

    int startIdx = Math.min(page * size, totalCount);
    int endIdx = Math.min(startIdx + size, totalCount);
    List<Prayer> pagePrayers = prayers.subList(startIdx, endIdx);

    // Populate updates for each prayer
    List<Map<String, Object>> items = new ArrayList<>();
    for (Prayer p : pagePrayers) {
      Map<String, Object> map = new HashMap<>();
      map.put("prayerId", p.getPrayerId());
      map.put("deviceId", p.getDeviceId());
      map.put("prayerText", p.getPrayerText());
      map.put("groupId", p.getGroupId());
      map.put("assignedGroupId", p.getAssignedGroupId());
      map.put("status", p.getStatus());
      map.put("prayedForCount", p.getPrayedForCount());
      map.put("createdAt", p.getCreatedAt());
      map.put("updatedAt", p.getUpdatedAt());

      List<PrayerUpdate> updates = prayerRepository.findUpdatesByPrayerId(p.getPrayerId());
      map.put("updates", updates);
      items.add(map);
    }

    Map<String, Object> result = new HashMap<>();
    result.put("items", items);
    result.put("totalCount", totalCount);
    result.put("page", page);
    result.put("size", size);

    return ResponseEntity.ok(result);
  }

  // === GROUP MANAGEMENT ENDPOINTS ===

  @GetMapping("/groups")
  public ResponseEntity<List<Map<String, Object>>> getGroups(
      @CookieValue(name = COOKIE_NAME, required = false) String token) {
    checkAuth(token, "APP_ADMIN");

    try {
      ResponseEntity<List<GroupDTO>> response =
          restTemplate.exchange(
              groupServiceUrl + "/api/groups",
              HttpMethod.GET,
              null,
              new ParameterizedTypeReference<List<GroupDTO>>() {});

      if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (GroupDTO g : response.getBody()) {
          Map<String, Object> map = new HashMap<>();
          map.put("groupId", g.getGroupId());
          map.put("name", g.getName());
          map.put("description", g.getDescription());
          map.put("passcode", g.getPasscode());
          map.put("optOutGeneral", g.getOptOutGeneral());
          map.put("createdAt", g.getCreatedAt());

          // Count members
          int memberCount = 0;
          try {
            ResponseEntity<List<GroupMemberDTO>> memRes =
                restTemplate.exchange(
                    groupServiceUrl + "/api/groups/" + g.getGroupId() + "/members",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<GroupMemberDTO>>() {});
            if (memRes.getStatusCode().is2xxSuccessful() && memRes.getBody() != null) {
              memberCount = memRes.getBody().size();
            }
          } catch (Exception e) {
            log.error("Failed to fetch member count for group {}", g.getGroupId(), e);
          }
          map.put("memberCount", memberCount);
          result.add(map);
        }
        return ResponseEntity.ok(result);
      }
    } catch (Exception e) {
      log.error("Failed to query group-service /api/groups", e);
    }
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
  }

  @PostMapping("/groups")
  public ResponseEntity<?> createGroup(
      @CookieValue(name = COOKIE_NAME, required = false) String token,
      @RequestBody GroupDTO dto) {
    checkAuth(token, "APP_ADMIN");

    try {
      ResponseEntity<GroupDTO> response =
          restTemplate.postForEntity(groupServiceUrl + "/api/groups", dto, GroupDTO.class);
      return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
    } catch (HttpStatusCodeException e) {
      return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
    } catch (Exception e) {
      log.error("Failed to call group-service to create group", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  @PutMapping("/groups/{groupId}")
  public ResponseEntity<?> updateGroup(
      @CookieValue(name = COOKIE_NAME, required = false) String token,
      @PathVariable("groupId") String groupId,
      @RequestBody GroupDTO dto) {
    DecodedJWT auth = checkAuth(token, "APP_ADMIN", "GROUP_ADMIN");

    if ("GROUP_ADMIN".equals(auth.getClaim("role").asString())) {
      if (!groupId.equals(auth.getClaim("groupId").asString())) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Map.of("error", "You do not have access to update this group."));
      }
      // Group admin cannot change group name
      dto.setName(null);
    }

    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<GroupDTO> requestEntity = new HttpEntity<>(dto, headers);
      ResponseEntity<GroupDTO> response =
          restTemplate.exchange(
              groupServiceUrl + "/api/groups/" + groupId,
              HttpMethod.PUT,
              requestEntity,
              GroupDTO.class);
      return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
    } catch (HttpStatusCodeException e) {
      return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
    } catch (Exception e) {
      log.error("Failed to call group-service to update group", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  @DeleteMapping("/groups/{groupId}")
  public ResponseEntity<?> deleteGroup(
      @CookieValue(name = COOKIE_NAME, required = false) String token,
      @PathVariable("groupId") String groupId) {
    checkAuth(token, "APP_ADMIN");

    try {
      restTemplate.delete(groupServiceUrl + "/api/groups/" + groupId);
      return ResponseEntity.noContent().build();
    } catch (HttpStatusCodeException e) {
      return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
    } catch (Exception e) {
      log.error("Failed to delete group in group-service", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  @PostMapping("/groups/{groupId}/regenerate-passcode")
  public ResponseEntity<?> regeneratePasscode(
      @CookieValue(name = COOKIE_NAME, required = false) String token,
      @PathVariable("groupId") String groupId) {
    DecodedJWT auth = checkAuth(token, "APP_ADMIN", "GROUP_ADMIN");

    if ("GROUP_ADMIN".equals(auth.getClaim("role").asString())) {
      if (!groupId.equals(auth.getClaim("groupId").asString())) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
      }
    }

    String newPasscode = generateRandomPasscode();
    GroupDTO dto = new GroupDTO();
    dto.setPasscode(newPasscode);

    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<GroupDTO> requestEntity = new HttpEntity<>(dto, headers);
      restTemplate.exchange(
          groupServiceUrl + "/api/groups/" + groupId,
          HttpMethod.PUT,
          requestEntity,
          GroupDTO.class);
      return ResponseEntity.ok(Map.of("passcode", newPasscode));
    } catch (HttpStatusCodeException e) {
      return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
    } catch (Exception e) {
      log.error("Failed to regenerate passcode in group-service", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  // === MEMBER MANAGEMENT ENDPOINTS ===

  @GetMapping("/groups/{groupId}/members")
  public ResponseEntity<?> getMembers(
      @CookieValue(name = COOKIE_NAME, required = false) String token,
      @PathVariable("groupId") String groupId) {
    DecodedJWT auth = checkAuth(token, "APP_ADMIN", "GROUP_ADMIN");

    if ("GROUP_ADMIN".equals(auth.getClaim("role").asString())) {
      if (!groupId.equals(auth.getClaim("groupId").asString())) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
      }
    }

    try {
      ResponseEntity<List<GroupMemberDTO>> response =
          restTemplate.exchange(
              groupServiceUrl + "/api/groups/" + groupId + "/members",
              HttpMethod.GET,
              null,
              new ParameterizedTypeReference<List<GroupMemberDTO>>() {});
      return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
    } catch (HttpStatusCodeException e) {
      return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
    } catch (Exception e) {
      log.error("Failed to retrieve members from group-service", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  @PostMapping("/groups/{groupId}/members")
  public ResponseEntity<?> addMember(
      @CookieValue(name = COOKIE_NAME, required = false) String token,
      @PathVariable("groupId") String groupId,
      @RequestBody GroupMemberDTO dto) {
    DecodedJWT auth = checkAuth(token, "APP_ADMIN", "GROUP_ADMIN");

    if ("GROUP_ADMIN".equals(auth.getClaim("role").asString())) {
      if (!groupId.equals(auth.getClaim("groupId").asString())) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
      }
    }

    if (dto.getEmail() == null || !EMAIL_PATTERN.matcher(dto.getEmail()).matches()) {
      return ResponseEntity.badRequest().body(Map.of("error", "Invalid email format."));
    }

    try {
      ResponseEntity<GroupMemberDTO> response =
          restTemplate.postForEntity(
              groupServiceUrl + "/api/groups/" + groupId + "/members", dto, GroupMemberDTO.class);
      return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
    } catch (HttpStatusCodeException e) {
      return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
    } catch (Exception e) {
      log.error("Failed to add member in group-service", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  @PostMapping("/groups/{groupId}/members/bulk")
  public ResponseEntity<?> bulkAddMembers(
      @CookieValue(name = COOKIE_NAME, required = false) String token,
      @PathVariable("groupId") String groupId,
      @RequestBody Map<String, List<Map<String, String>>> body) {
    DecodedJWT auth = checkAuth(token, "APP_ADMIN", "GROUP_ADMIN");

    if ("GROUP_ADMIN".equals(auth.getClaim("role").asString())) {
      if (!groupId.equals(auth.getClaim("groupId").asString())) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
      }
    }

    List<Map<String, String>> members = body.get("members");
    if (members == null) {
      return ResponseEntity.badRequest().body(Map.of("error", "Missing members list"));
    }

    int added = 0;
    List<Map<String, String>> errors = new ArrayList<>();

    for (Map<String, String> m : members) {
      String name = m.get("name");
      String email = m.get("email");

      if (name == null || name.trim().isEmpty() || email == null || email.trim().isEmpty()) {
        errors.add(Map.of("name", name != null ? name : "", "email", email != null ? email : "", "reason", "Name and email are required."));
        continue;
      }

      if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
        errors.add(Map.of("name", name, "email", email, "reason", "Invalid email format."));
        continue;
      }

      try {
        GroupMemberDTO dto = GroupMemberDTO.builder()
            .name(name.trim())
            .email(email.trim())
            .build();
        restTemplate.postForEntity(
            groupServiceUrl + "/api/groups/" + groupId + "/members", dto, GroupMemberDTO.class);
        added++;
      } catch (HttpStatusCodeException e) {
        errors.add(Map.of("name", name, "email", email, "reason", "Backend returned: " + e.getStatusCode()));
      } catch (Exception e) {
        errors.add(Map.of("name", name, "email", email, "reason", "Connection error."));
      }
    }

    Map<String, Object> result = new HashMap<>();
    result.put("added", added);
    result.put("errors", errors);
    return ResponseEntity.ok(result);
  }

  @DeleteMapping("/groups/{groupId}/members/{memberId}")
  public ResponseEntity<?> removeMember(
      @CookieValue(name = COOKIE_NAME, required = false) String token,
      @PathVariable("groupId") String groupId,
      @PathVariable("memberId") String memberId) {
    DecodedJWT auth = checkAuth(token, "APP_ADMIN", "GROUP_ADMIN");

    if ("GROUP_ADMIN".equals(auth.getClaim("role").asString())) {
      if (!groupId.equals(auth.getClaim("groupId").asString())) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
      }
    }

    try {
      restTemplate.delete(groupServiceUrl + "/api/groups/" + groupId + "/members/" + memberId);
      return ResponseEntity.noContent().build();
    } catch (HttpStatusCodeException e) {
      return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
    } catch (Exception e) {
      log.error("Failed to delete member in group-service", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  // === PRIVATE HELPERS ===

  private DecodedJWT checkAuth(String token, String... requiredRoles) {
    if (token == null || token.trim().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing token");
    }
    try {
      DecodedJWT jwt = jwtUtil.verifyToken(token);
      if (requiredRoles.length > 0) {
        String role = jwt.getClaim("role").asString();
        boolean authorized = false;
        for (String requiredRole : requiredRoles) {
          if (requiredRole.equals(role)) {
            authorized = true;
            break;
          }
        }
        if (!authorized) {
          throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized role");
        }
      }
      return jwt;
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
    }
  }

  private void setJwtCookie(HttpServletResponse response, String token) {
    String secureFlag = cookieSecure ? " Secure;" : "";
    response.addHeader(
        "Set-Cookie",
        String.format(
            "%s=%s; Max-Age=86400; Path=/; HttpOnly;%s SameSite=Strict", COOKIE_NAME, token, secureFlag));
  }

  private void clearJwtCookie(HttpServletResponse response) {
    String secureFlag = cookieSecure ? " Secure;" : "";
    response.addHeader(
        "Set-Cookie",
        String.format("%s=; Max-Age=0; Path=/; HttpOnly;%s SameSite=Strict", COOKIE_NAME, secureFlag));
  }

  private String generateRandomPasscode() {
    String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    SecureRandom random = new SecureRandom();
    StringBuilder sb = new StringBuilder(6);
    for (int i = 0; i < 6; i++) {
      sb.append(chars.charAt(random.nextInt(chars.length())));
    }
    return sb.toString();
  }
}
