package com.prayerlink.identity.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.prayerlink.common.dto.DeviceDTO;
import com.prayerlink.common.dto.GroupDTO;
import com.prayerlink.common.dto.GroupMemberDTO;
import com.prayerlink.identity.model.Device;
import com.prayerlink.identity.model.IntercessorAccount;
import com.prayerlink.identity.repository.DeviceRepository;
import com.prayerlink.identity.repository.IntercessorAccountRepository;
import com.prayerlink.identity.util.JwtUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
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
@RequestMapping("/api/identity")
@Validated
public class IdentityController {

  private static final Logger log = LoggerFactory.getLogger(IdentityController.class);

  private static final String COOKIE_NAME = "pl-device-id";
  private static final String INTERCESSOR_COOKIE_NAME = "pl-auth-token";
  private static final String UUID_REGEX = "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$";

  private final DeviceRepository deviceRepository;
  private final IntercessorAccountRepository intercessorAccountRepository;
  private final RestTemplate restTemplate;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtil jwtUtil;

  @Value("${services.group-service.url:http://localhost:8083}")
  private String groupServiceUrl;

  @Value("${cookie.secure:true}")
  private boolean cookieSecure;

  public IdentityController(
      DeviceRepository deviceRepository,
      IntercessorAccountRepository intercessorAccountRepository,
      RestTemplate restTemplate,
      PasswordEncoder passwordEncoder,
      JwtUtil jwtUtil) {
    this.deviceRepository = deviceRepository;
    this.intercessorAccountRepository = intercessorAccountRepository;
    this.restTemplate = restTemplate;
    this.passwordEncoder = passwordEncoder;
    this.jwtUtil = jwtUtil;
  }

  // === Device Endpoints (Intact) ===

  @PostMapping("/register")
  public ResponseEntity<DeviceDTO> register(@RequestBody Map<String, String> body, HttpServletResponse response) {
    String deviceId = body.get("deviceId");
    if (deviceId == null || !deviceId.matches(UUID_REGEX)) {
      return ResponseEntity.badRequest().build();
    }

    Optional<Device> existingDevice = deviceRepository.findById(deviceId);
    HttpStatus status;
    Device device;

    if (existingDevice.isPresent()) {
      device = existingDevice.get();
      device.setLastSeenAt(Instant.now());
      deviceRepository.save(device);
      status = HttpStatus.OK;
    } else {
      device =
          Device.builder()
              .deviceId(deviceId)
              .createdAt(Instant.now())
              .lastSeenAt(Instant.now())
              .build();
      deviceRepository.save(device);
      status = HttpStatus.CREATED;
    }

    // Set the Cookie using HTTP header to configure SameSite attribute cleanly
    response.addHeader("Set-Cookie",
            String.format("%s=%s; Max-Age=31536000; Path=/; HttpOnly; Secure; SameSite=Lax", COOKIE_NAME, deviceId));

    DeviceDTO dto =
        DeviceDTO.builder()
            .deviceId(device.getDeviceId())
            .createdAt(device.getCreatedAt())
            .lastActiveAt(device.getLastSeenAt())
            .platform("web")
            .build();

    return ResponseEntity.status(status).body(dto);
  }

  @PutMapping("/{deviceId}/seen")
  public ResponseEntity<Void> updateSeen(@PathVariable("deviceId") @Pattern(regexp = UUID_REGEX) String deviceId) {
    Optional<Device> existingDevice = deviceRepository.findById(deviceId);
    if (existingDevice.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    Device device = existingDevice.get();
    device.setLastSeenAt(Instant.now());
    deviceRepository.save(device);

    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me")
  public ResponseEntity<DeviceDTO> getMe(@CookieValue(name = COOKIE_NAME, required = false) String cookieDeviceId) {
    if (cookieDeviceId == null || !cookieDeviceId.matches(UUID_REGEX)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    Optional<Device> existingDevice = deviceRepository.findById(cookieDeviceId);
    if (existingDevice.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    Device device = existingDevice.get();
    DeviceDTO dto =
        DeviceDTO.builder()
            .deviceId(device.getDeviceId())
            .createdAt(device.getCreatedAt())
            .lastActiveAt(device.getLastSeenAt())
            .platform("web")
            .build();

    return ResponseEntity.ok(dto);
  }

  // === Intercessor Endpoints (New) ===

  @PostMapping("/intercessor/register")
  public ResponseEntity<?> registerIntercessor(
      @RequestBody Map<String, String> body,
      HttpServletResponse response) {
    
    String email = body.get("email");
    String name = body.get("name");
    String password = body.get("password");
    String inviteCode = body.get("inviteCode");

    if (email == null || email.trim().isEmpty() ||
        name == null || name.trim().isEmpty() ||
        password == null || password.trim().isEmpty() ||
        inviteCode == null || inviteCode.trim().isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("error", "All fields are required"));
    }

    String cleanEmail = email.trim().toLowerCase();
    String cleanInviteCode = inviteCode.trim().toUpperCase();

    // 1. Get group by invite code (passcode)
    GroupDTO group = null;
    try {
      ResponseEntity<GroupDTO> groupRes = restTemplate.getForEntity(
          groupServiceUrl + "/api/groups/validate?passcode=" + cleanInviteCode,
          GroupDTO.class);
      if (groupRes.getStatusCode() == HttpStatus.OK) {
        group = groupRes.getBody();
      }
    } catch (Exception e) {
      log.error("Failed to validate invite code " + cleanInviteCode, e);
    }

    if (group == null) {
      return ResponseEntity.badRequest().body(Map.of("error", "Invalid invite code"));
    }

    // 2. Verify that email exists as a group member
    List<GroupMemberDTO> members = null;
    try {
      ResponseEntity<List<GroupMemberDTO>> searchRes = restTemplate.exchange(
          groupServiceUrl + "/api/groups/members/search?email=" + cleanEmail,
          HttpMethod.GET,
          null,
          new ParameterizedTypeReference<List<GroupMemberDTO>>() {});
      members = searchRes.getBody();
    } catch (Exception e) {
      log.error("Failed to verify group membership for " + cleanEmail, e);
    }

    final String targetGroupId = group.getGroupId();
    boolean isMember = members != null && members.stream()
        .anyMatch(m -> m.getGroupId().equals(targetGroupId));

    if (!isMember) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Email is not pre-authorized for this invite code"));
    }

    // 2. Check if account already exists
    if (intercessorAccountRepository.findById(cleanEmail).isPresent()) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Account already exists"));
    }

    // 3. Save new account
    IntercessorAccount account = IntercessorAccount.builder()
        .email(cleanEmail)
        .name(name.trim())
        .passwordHash(passwordEncoder.encode(password))
        .createdAt(Instant.now())
        .build();
    
    intercessorAccountRepository.save(account);

    // 4. Generate token and set cookie
    String token = jwtUtil.generateToken(cleanEmail, account.getName());
    String secureFlag = cookieSecure ? "; Secure" : "";
    response.addHeader("Set-Cookie",
        String.format("%s=%s; Max-Age=2592000; Path=/; HttpOnly; SameSite=Strict%s", INTERCESSOR_COOKIE_NAME, token, secureFlag));

    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
        "email", account.getEmail(),
        "name", account.getName()
    ));
  }

  @PostMapping("/intercessor/login")
  public ResponseEntity<?> loginIntercessor(
      @RequestBody Map<String, String> body,
      HttpServletResponse response) {
    
    String email = body.get("email");
    String password = body.get("password");

    if (email == null || email.trim().isEmpty() ||
        password == null || password.trim().isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("error", "Email and password are required"));
    }

    String cleanEmail = email.trim().toLowerCase();
    Optional<IntercessorAccount> optAccount = intercessorAccountRepository.findById(cleanEmail);

    if (optAccount.isEmpty() || !passwordEncoder.matches(password, optAccount.get().getPasswordHash())) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid email or password"));
    }

    IntercessorAccount account = optAccount.get();

    // Generate token and set cookie
    String token = jwtUtil.generateToken(cleanEmail, account.getName());
    String secureFlag = cookieSecure ? "; Secure" : "";
    response.addHeader("Set-Cookie",
        String.format("%s=%s; Max-Age=2592000; Path=/; HttpOnly; SameSite=Strict%s", INTERCESSOR_COOKIE_NAME, token, secureFlag));

    return ResponseEntity.ok(Map.of(
        "email", account.getEmail(),
        "name", account.getName()
    ));
  }

  @GetMapping("/intercessor/me")
  public ResponseEntity<?> getIntercessorMe(
      @CookieValue(name = INTERCESSOR_COOKIE_NAME, required = false) String token) {
    
    if (token == null || token.trim().isEmpty()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
    }

    try {
      DecodedJWT decoded = jwtUtil.verifyToken(token);
      String email = decoded.getClaim("email").asString();
      String name = decoded.getClaim("name").asString();

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
        log.error("Failed to fetch groups for logged in intercessor " + email, e);
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

      return ResponseEntity.ok(Map.of(
          "email", email,
          "name", name,
          "groups", groups
      ));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid or expired token"));
    }
  }

  @PostMapping("/intercessor/logout")
  public ResponseEntity<?> logoutIntercessor(HttpServletResponse response) {
    String secureFlag = cookieSecure ? "; Secure" : "";
    response.addHeader("Set-Cookie",
        String.format("%s=; Max-Age=0; Path=/; HttpOnly; SameSite=Strict%s", INTERCESSOR_COOKIE_NAME, secureFlag));
    
    return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
  }
}
