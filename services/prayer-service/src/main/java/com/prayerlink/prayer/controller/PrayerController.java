package com.prayerlink.prayer.controller;

import tools.jackson.databind.ObjectMapper;
import com.prayerlink.prayer.model.Prayer;
import com.prayerlink.prayer.model.PrayerUpdate;
import com.prayerlink.prayer.repository.PrayerRepository;
import com.prayerlink.prayer.repository.PrayerUpdateRepository;
import com.prayerlink.common.dto.GroupDTO;
import com.prayerlink.common.dto.GroupMemberDTO;
import com.prayerlink.common.dto.PrayerDTO;
import com.prayerlink.common.dto.PrayerUpdateDTO;
import com.prayerlink.common.event.PrayerCreatedEvent;
import com.prayerlink.common.event.PrayerUpdatedEvent;
import com.prayerlink.common.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

@RestController
@RequestMapping("/api/prayers")
@Validated
public class PrayerController {

  private static final Logger log = LoggerFactory.getLogger(PrayerController.class);

  private final PrayerRepository prayerRepository;
  private final PrayerUpdateRepository prayerUpdateRepository;
  private final RestTemplate restTemplate;
  private final EventBridgeClient eventBridgeClient;
  private final ObjectMapper objectMapper;
  private final com.prayerlink.prayer.util.JwtUtil jwtUtil;
  private final AtomicInteger roundRobinCounter = new AtomicInteger(0);

  @Value("${services.group-service.url:http://localhost:8083}")
  private String groupServiceUrl;

  @Value("${aws.eventbridge.bus:prayer-link-bus}")
  private String eventBusName;

  @Value("${hmac.secret-key:default-secret-key-change-me-in-production}")
  private String hmacSecretKey;

  public PrayerController(
      PrayerRepository prayerRepository,
      PrayerUpdateRepository prayerUpdateRepository,
      RestTemplate restTemplate,
      EventBridgeClient eventBridgeClient,
      ObjectMapper objectMapper,
      com.prayerlink.prayer.util.JwtUtil jwtUtil) {
    this.prayerRepository = prayerRepository;
    this.prayerUpdateRepository = prayerUpdateRepository;
    this.restTemplate = restTemplate;
    this.eventBridgeClient = eventBridgeClient;
    this.objectMapper = objectMapper;
    this.jwtUtil = jwtUtil;
  }

  @PostMapping
  public ResponseEntity<PrayerDTO> createPrayer(@jakarta.validation.Valid @RequestBody PrayerDTO dto) {
    String prayerId = UUID.randomUUID().toString();
    String assignedGroupId = null;

    if (dto.getGroupId() != null && !dto.getGroupId().trim().isEmpty()) {
      try {
        ResponseEntity<GroupDTO> response =
                restTemplate.getForEntity(
                        groupServiceUrl + "/api/groups/" + dto.getGroupId(), GroupDTO.class);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
          assignedGroupId = response.getBody().getGroupId();
        }
      } catch (Exception e) {
        log.error("Failed to fetch group " + dto.getGroupId() + " from group-service", e);
        return ResponseEntity.badRequest().build();
      }
    } else {
      // Round-robin assignment
      try {
        ResponseEntity<List<GroupDTO>> response =
                restTemplate.exchange(
                        groupServiceUrl + "/api/groups",
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<List<GroupDTO>>() {});
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
          List<GroupDTO> eligibleGroups =
                  response.getBody().stream()
                          .filter(g -> g.getOptOutGeneral() == null || !g.getOptOutGeneral())
                          .toList();

          if (eligibleGroups.isEmpty()) {
            log.warn("No eligible groups for round-robin assignment. Prayer {} is unassigned.", prayerId);
          } else {
            int index = Math.abs(roundRobinCounter.getAndIncrement() % eligibleGroups.size());
            assignedGroupId = eligibleGroups.get(index).getGroupId();
          }
        }
      } catch (Exception e) {
        log.error("Failed to query groups for round-robin assignment", e);
      }
    }

    Prayer prayer =
            Prayer.builder()
                    .prayerId(prayerId)
                    .deviceId(dto.getDeviceId())
                    .prayerText(dto.getPrayerText())
                    .groupId(dto.getGroupId())
                    .assignedGroupId(assignedGroupId)
                    .status("OPEN")
                    .prayedForCount(0)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

    prayerRepository.save(prayer);

    // Publish event to EventBridge
    PrayerCreatedEvent event =
            PrayerCreatedEvent.builder()
                    .prayerId(prayer.getPrayerId())
                    .deviceId(prayer.getDeviceId())
                    .prayerText(prayer.getPrayerText())
                    .assignedGroupId(prayer.getAssignedGroupId())
                    .createdAt(prayer.getCreatedAt())
                    .build();

    publishEvent("PrayerCreated", event);

    return ResponseEntity.status(HttpStatus.CREATED).body(convertToDTO(prayer));
  }

  @GetMapping("/{prayerId}")
  public ResponseEntity<PrayerDTO> getPrayer(@PathVariable("prayerId") String prayerId) {
    Prayer prayer = prayerRepository.findById(prayerId)
            .orElseThrow(() -> new ResourceNotFoundException("Prayer not found with id: " + prayerId));
    List<PrayerUpdate> updates = prayerUpdateRepository.findByPrayerId(prayerId);
    return ResponseEntity.ok(convertToDTO(prayer, updates));
  }

  @GetMapping
  public ResponseEntity<List<PrayerDTO>> getPrayers(@RequestParam("deviceId") String deviceId) {
    List<PrayerDTO> dtos = prayerRepository.findByDeviceId(deviceId).stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
    return ResponseEntity.ok(dtos);
  }

  @PostMapping("/{prayerId}/updates")
  public ResponseEntity<?> createUpdate(
      @PathVariable("prayerId") String prayerId,
      @RequestHeader(value = "X-Device-ID", required = false) String deviceIdHeader,
      @RequestBody Map<String, String> requestBody) {
    
    if (deviceIdHeader == null || deviceIdHeader.trim().isEmpty()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Missing device ID"));
    }
    
    Prayer prayer = prayerRepository.findById(prayerId)
        .orElseThrow(() -> new ResourceNotFoundException("Prayer not found"));
        
    if ("CLOSED".equals(prayer.getStatus())) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Prayer is already closed"));
    }
        
    if (!deviceIdHeader.equals(prayer.getDeviceId())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You don't have permission to update this prayer"));
    }
    
    String updateText = requestBody.get("updateText");
    if (updateText == null || updateText.trim().isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("error", "Update text is required"));
    }
    
    Instant now = Instant.now();
    PrayerUpdate prayerUpdate = PrayerUpdate.builder()
        .prayerId(prayerId)
        .updatedAt(now)
        .updateText(updateText)
        .updatedByDeviceId(deviceIdHeader)
        .build();
        
    prayerUpdateRepository.save(prayerUpdate);
    
    prayer.setStatus("CLOSED");
    prayer.setUpdatedAt(now);
    prayerRepository.save(prayer);
    
    // Publish PRAYER_UPDATED event to EventBridge
    PrayerUpdatedEvent event = PrayerUpdatedEvent.builder()
        .prayerId(prayerId)
        .updateText(updateText)
        .status("CLOSED")
        .updatedAt(now)
        .build();
        
    publishEvent("PrayerUpdated", event);
    
    return ResponseEntity.ok(Map.of("message", "Prayer updated and closed successfully"));
  }

  @PostMapping("/{prayerId}/prayed")
  public ResponseEntity<?> markPrayed(
      @PathVariable("prayerId") String prayerId,
      @RequestBody Map<String, String> requestBody) {
    
    String token = requestBody.get("intercessorToken");
    if (token == null || !token.contains("|")) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid or expired token"));
    }
    
    String[] parts = token.split("\\|");
    if (parts.length != 2 && parts.length != 3) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid or expired token"));
    }
    
    String encodedSignature = parts[0];
    String groupId = null;
    String expiryStr = null;
    
    if (parts.length == 3) {
      groupId = parts[1];
      expiryStr = parts[2];
    } else {
      expiryStr = parts[1];
    }
    
    try {
      long expiryTimestamp = Long.parseLong(expiryStr);
      if (expiryTimestamp < Instant.now().getEpochSecond()) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid or expired token"));
      }
    } catch (NumberFormatException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid or expired token"));
    }
    
    Prayer prayer = prayerRepository.findById(prayerId)
        .orElseThrow(() -> new ResourceNotFoundException("Prayer not found"));
    
    String assignedGroupId = prayer.getAssignedGroupId();
    if (assignedGroupId == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid or expired token"));
    }
    
    // Verify groupId in token matches assignedGroupId if parts.length == 3
    if (parts.length == 3 && !assignedGroupId.equals(groupId)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid or expired token"));
    }
    
    // Fetch group members
    List<GroupMemberDTO> members;
    try {
      ResponseEntity<List<GroupMemberDTO>> response = restTemplate.exchange(
          groupServiceUrl + "/api/groups/" + assignedGroupId + "/members",
          HttpMethod.GET,
          null,
          new ParameterizedTypeReference<List<GroupMemberDTO>>() {});
      members = response.getBody();
    } catch (Exception e) {
      log.error("Failed to fetch group members for validation", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
    
    if (members == null || members.isEmpty()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid or expired token"));
    }
    
    String matchedEmail = null;
    if (parts.length == 3) {
      // 3-part token: signature signs "{groupId}:{email}:{expiryTimestamp}"
      for (GroupMemberDTO member : members) {
        String payloadStr = assignedGroupId + ":" + member.getEmail() + ":" + expiryStr;
        String expectedSig = computeHmac(payloadStr, hmacSecretKey);
        if (expectedSig.equals(encodedSignature)) {
          matchedEmail = member.getEmail();
          break;
        }
      }
    } else {
      // 2-part token: signature signs "{prayerId}:{email}:{expiryTimestamp}"
      for (GroupMemberDTO member : members) {
        String payloadStr = prayerId + ":" + member.getEmail() + ":" + expiryStr;
        String expectedSig = computeHmac(payloadStr, hmacSecretKey);
        if (expectedSig.equals(encodedSignature)) {
          matchedEmail = member.getEmail();
          break;
        }
      }
    }
    
    if (matchedEmail == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid or expired token"));
    }
    
    try {
      prayerRepository.recordPrayer(prayerId, matchedEmail);
    } catch (software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "You have already prayed for this request"));
    }
    
    Prayer updatedPrayer = prayerRepository.findById(prayerId)
        .orElseThrow(() -> new ResourceNotFoundException("Prayer not found"));
    
    return ResponseEntity.ok(Map.of(
        "message", "Thank you for praying",
        "prayedForCount", updatedPrayer.getPrayedForCount() == null ? 0 : updatedPrayer.getPrayedForCount()
    ));
  }

  @GetMapping("/group/{groupId}")
  public ResponseEntity<?> getGroupPrayers(
      @PathVariable("groupId") String groupId,
      @RequestParam("token") String token) {
    
    if (token == null || !token.contains("|")) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid or expired token"));
    }
    
    String[] parts = token.split("\\|");
    if (parts.length != 2 && parts.length != 3) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid or expired token"));
    }
    
    String encodedSignature = parts[0];
    String tokenGroupId = null;
    String expiryStr = null;
    
    if (parts.length == 3) {
      tokenGroupId = parts[1];
      expiryStr = parts[2];
      
      if (!groupId.equals(tokenGroupId)) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid or expired token"));
      }
    } else {
      expiryStr = parts[1];
    }
    
    try {
      long expiryTimestamp = Long.parseLong(expiryStr);
      if (expiryTimestamp < Instant.now().getEpochSecond()) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid or expired token"));
      }
    } catch (NumberFormatException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid or expired token"));
    }
    
    // Fetch group members from group-service
    List<GroupMemberDTO> members;
    try {
      ResponseEntity<List<GroupMemberDTO>> response = restTemplate.exchange(
          groupServiceUrl + "/api/groups/" + groupId + "/members",
          HttpMethod.GET,
          null,
          new ParameterizedTypeReference<List<GroupMemberDTO>>() {});
      members = response.getBody();
    } catch (Exception e) {
      log.error("Failed to fetch group members for validation", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
    
    if (members == null || members.isEmpty()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid or expired token"));
    }
    
    boolean valid = false;
    if (parts.length == 3) {
      // New format: sign "{groupId}:{email}:{expiryTimestamp}"
      for (GroupMemberDTO member : members) {
        String payloadStr = groupId + ":" + member.getEmail() + ":" + expiryStr;
        String expectedSig = computeHmac(payloadStr, hmacSecretKey);
        if (expectedSig.equals(encodedSignature)) {
          valid = true;
          break;
        }
      }
    } else {
      // Legacy format: requires finding a prayer in this group that matches the signature.
      List<Prayer> groupPrayers = prayerRepository.findByGroupId(groupId);
      for (Prayer prayer : groupPrayers) {
        for (GroupMemberDTO member : members) {
          String payloadStr = prayer.getPrayerId() + ":" + member.getEmail() + ":" + expiryStr;
          String expectedSig = computeHmac(payloadStr, hmacSecretKey);
          if (expectedSig.equals(encodedSignature)) {
            valid = true;
            break;
          }
        }
        if (valid) break;
      }
    }
    
    if (!valid) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid or expired token"));
    }
    
    // Return all OPEN prayers for this group
    List<Prayer> activePrayers = prayerRepository.findByGroupId(groupId).stream()
        .filter(p -> "OPEN".equals(p.getStatus()))
        .collect(Collectors.toList());
    
    List<PrayerDTO> dtos = activePrayers.stream()
        .map(this::convertToDTO)
        .collect(Collectors.toList());
    
    return ResponseEntity.ok(dtos);
  }

  @PostMapping("/{prayerId}/prayed/auth")
  public ResponseEntity<?> markPrayedAuth(
      @PathVariable("prayerId") String prayerId,
      @CookieValue(name = "pl-auth-token", required = false) String token) {
    
    if (token == null || token.trim().isEmpty()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
    }
    
    String email;
    try {
      com.auth0.jwt.interfaces.DecodedJWT decoded = jwtUtil.verifyToken(token);
      email = decoded.getClaim("email").asString();
      if (email == null || email.trim().isEmpty()) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid token claim"));
      }
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid or expired token"));
    }
    
    try {
      prayerRepository.recordPrayer(prayerId, email);
    } catch (software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "You have already prayed for this request"));
    }
    
    Prayer updatedPrayer = prayerRepository.findById(prayerId)
        .orElseThrow(() -> new ResourceNotFoundException("Prayer not found"));
    
    PrayerDTO updatedDto = convertToDTO(updatedPrayer, List.of(), email);
    
    return ResponseEntity.ok(Map.of(
        "message", "Thank you for praying",
        "prayedForCount", updatedDto.getPrayedForCount(),
        "hasPrayed", updatedDto.getHasPrayed()
    ));
  }

  @GetMapping("/group/{groupId}/auth")
  public ResponseEntity<?> getGroupPrayersAuth(
      @PathVariable("groupId") String groupId,
      @CookieValue(name = "pl-auth-token", required = false) String token) {
    
    if (token == null || token.trim().isEmpty()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
    }
    
    String email;
    try {
      com.auth0.jwt.interfaces.DecodedJWT decoded = jwtUtil.verifyToken(token);
      email = decoded.getClaim("email").asString();
      if (email == null || email.trim().isEmpty()) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid token claim"));
      }
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid or expired token"));
    }
    
    // Verify intercessor is a member of the group
    List<GroupMemberDTO> members;
    try {
      ResponseEntity<List<GroupMemberDTO>> response = restTemplate.exchange(
          groupServiceUrl + "/api/groups/" + groupId + "/members",
          HttpMethod.GET,
          null,
          new ParameterizedTypeReference<List<GroupMemberDTO>>() {});
      members = response.getBody();
    } catch (Exception e) {
      log.error("Failed to fetch group members for authorization", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
    
    if (members == null || members.isEmpty()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
    }
    
    boolean isMember = false;
    for (GroupMemberDTO member : members) {
      if (email.equalsIgnoreCase(member.getEmail())) {
        isMember = true;
        break;
      }
    }
    
    if (!isMember) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You do not belong to this group"));
    }
    
    // Fetch all prayers for this group
    List<Prayer> prayers = prayerRepository.findByGroupId(groupId);
    List<PrayerDTO> dtos = prayers.stream()
        .map(p -> convertToDTO(p, List.of(), email))
        .collect(Collectors.toList());
    
    return ResponseEntity.ok(dtos);
  }




  private void publishEvent(String detailType, Object detail) {
    try {
      String detailJson = objectMapper.writeValueAsString(detail);
      PutEventsRequestEntry entry = PutEventsRequestEntry.builder()
          .source("com.prayerlink.prayer-service")
          .detailType(detailType)
          .detail(detailJson)
          .eventBusName(eventBusName)
          .build();

      PutEventsRequest request = PutEventsRequest.builder()
          .entries(entry)
          .build();

      PutEventsResponse response = eventBridgeClient.putEvents(request);
      log.info("Published event {} to EventBridge. Response status: {}", detailType, response.sdkHttpResponse().statusCode());
    } catch (Exception e) {
      log.error("Failed to publish event to EventBridge: {}", detailType, e);
    }
  }

  private String computeHmac(String data, String secretKey) {
    try {
      javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
      mac.init(new javax.crypto.spec.SecretKeySpec(secretKey.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] rawHmac = mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
    } catch (Exception e) {
      throw new RuntimeException("Failed to compute HMAC", e);
    }
  }

  private PrayerDTO convertToDTO(Prayer prayer) {
    return convertToDTO(prayer, List.of());
  }

  private PrayerDTO convertToDTO(Prayer prayer, List<PrayerUpdate> updates, String intercessorEmail) {
    PrayerDTO dto = convertToDTO(prayer, updates);
    if (intercessorEmail != null && prayer.getPrayedByEmails() != null) {
      dto.setHasPrayed(prayer.getPrayedByEmails().contains(intercessorEmail));
    }
    return dto;
  }

  private PrayerDTO convertToDTO(Prayer prayer, List<PrayerUpdate> updates) {
    List<PrayerUpdateDTO> updateDTOs = updates.stream()
        .map(u -> PrayerUpdateDTO.builder()
            .updateText(u.getUpdateText())
            .updatedAt(u.getUpdatedAt())
            .build())
        .collect(Collectors.toList());

    return PrayerDTO.builder()
        .prayerId(prayer.getPrayerId())
        .deviceId(prayer.getDeviceId())
        .prayerText(prayer.getPrayerText())
        .groupId(prayer.getGroupId())
        .assignedGroupId(prayer.getAssignedGroupId())
        .status(prayer.getStatus())
        .prayedForCount(prayer.getPrayedForCount() == null ? 0 : prayer.getPrayedForCount())
        .createdAt(prayer.getCreatedAt())
        .updatedAt(prayer.getUpdatedAt())
        .updates(updateDTOs)
        .build();
  }
}
