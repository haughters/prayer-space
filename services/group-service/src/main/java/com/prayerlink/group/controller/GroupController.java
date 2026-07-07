package com.prayerlink.group.controller;

import tools.jackson.databind.ObjectMapper;
import com.prayerlink.group.model.Group;
import com.prayerlink.group.model.GroupMember;
import com.prayerlink.group.repository.GroupMemberRepository;
import com.prayerlink.group.repository.GroupRepository;
import com.prayerlink.common.dto.GroupDTO;
import com.prayerlink.common.dto.GroupMemberDTO;
import com.prayerlink.common.event.MemberAddedEvent;
import com.prayerlink.common.exception.ResourceNotFoundException;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

@RestController
@RequestMapping("/api/groups")
@Validated
public class GroupController {

  private static final Logger log = LoggerFactory.getLogger(GroupController.class);

  private final GroupRepository groupRepository;
  private final GroupMemberRepository groupMemberRepository;
  private final EventBridgeClient eventBridgeClient;
  private final ObjectMapper objectMapper;

  @Value("${aws.eventbridge.bus:prayer-link-bus}")
  private String eventBusName;

  public GroupController(
      GroupRepository groupRepository,
      GroupMemberRepository groupMemberRepository,
      EventBridgeClient eventBridgeClient,
      ObjectMapper objectMapper) {
    this.groupRepository = groupRepository;
    this.groupMemberRepository = groupMemberRepository;
    this.eventBridgeClient = eventBridgeClient;
    this.objectMapper = objectMapper;
  }

  // 1. Group CRUD
  @PostMapping
  public ResponseEntity<GroupDTO> createGroup(@RequestBody GroupDTO dto) {
    if (dto.getPasscode() != null && !dto.getPasscode().trim().isEmpty()) {
      if (groupRepository.findByPasscode(dto.getPasscode().trim().toUpperCase()).isPresent()) {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
      }
    }

    String groupId = UUID.randomUUID().toString();
    Group group =
        Group.builder()
            .groupId(groupId)
            .name(dto.getName())
            .description(dto.getDescription())
            .passcode(dto.getPasscode() != null ? dto.getPasscode().trim().toUpperCase() : null)
            .optOutGeneral(dto.getOptOutGeneral() != null ? dto.getOptOutGeneral() : false)
            .createdAt(Instant.now())
            .build();

    groupRepository.save(group);
    return ResponseEntity.status(HttpStatus.CREATED).body(convertToDTO(group));
  }

  @GetMapping("/{groupId}")
  public ResponseEntity<GroupDTO> getGroup(@PathVariable("groupId") String groupId) {
    Group group =
        groupRepository
            .findById(groupId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Group not found with id: " + groupId));
    return ResponseEntity.ok(convertToDTO(group));
  }

  @GetMapping
  public ResponseEntity<List<GroupDTO>> getAllGroups() {
    List<GroupDTO> dtos =
        groupRepository.findAll().stream().map(this::convertToDTO).collect(Collectors.toList());
    return ResponseEntity.ok(dtos);
  }

  @PutMapping("/{groupId}")
  public ResponseEntity<GroupDTO> updateGroup(
      @PathVariable("groupId") String groupId, @RequestBody GroupDTO dto) {
    Group group =
        groupRepository
            .findById(groupId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Group not found with id: " + groupId));

    if (dto.getPasscode() != null && !dto.getPasscode().trim().isEmpty() && !dto.getPasscode().trim().toUpperCase().equals(group.getPasscode())) {
      if (groupRepository.findByPasscode(dto.getPasscode().trim().toUpperCase()).isPresent()) {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
      }
      group.setPasscode(dto.getPasscode().trim().toUpperCase());
    }

    if (dto.getName() != null) {
      group.setName(dto.getName());
    }
    if (dto.getDescription() != null) {
      group.setDescription(dto.getDescription());
    }
    group.setOptOutGeneral(dto.getOptOutGeneral() != null ? dto.getOptOutGeneral() : false);
    groupRepository.save(group);

    return ResponseEntity.ok(convertToDTO(group));
  }

  @DeleteMapping("/{groupId}")
  public ResponseEntity<Void> deleteGroup(@PathVariable("groupId") String groupId) {
    groupRepository
        .findById(groupId)
        .orElseThrow(() -> new ResourceNotFoundException("Group not found with id: " + groupId));

    // Cascade delete group members
    List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
    for (GroupMember member : members) {
      groupMemberRepository.delete(groupId, member.getMemberId());
    }

    groupRepository.delete(groupId);
    return ResponseEntity.noContent().build();
  }

  // 2. Validate passcode
  @GetMapping("/validate")
  public ResponseEntity<GroupDTO> validatePasscode(
      @RequestParam("passcode") @Size(min = 3, max = 12) String passcode) {
    Group group =
        groupRepository
            .findByPasscode(passcode)
            .orElseThrow(
                () -> new ResourceNotFoundException("Group not found with passcode: " + passcode));
    return ResponseEntity.ok(convertToDTO(group));
  }

  // 3. GroupMember CRUD
  @PostMapping("/{groupId}/members")
  public ResponseEntity<GroupMemberDTO> addMember(
      @PathVariable("groupId") String groupId, @RequestBody GroupMemberDTO dto) {
    groupRepository
        .findById(groupId)
        .orElseThrow(() -> new ResourceNotFoundException("Group not found with id: " + groupId));

    List<GroupMember> existingMembers = groupMemberRepository.findByGroupId(groupId);
    boolean emailExists = existingMembers.stream()
        .anyMatch(m -> m.getEmail().equalsIgnoreCase(dto.getEmail().trim()));
    if (emailExists) {
      return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    String memberId = UUID.randomUUID().toString();
    GroupMember member =
        GroupMember.builder()
            .groupId(groupId)
            .memberId(memberId)
            .name(dto.getName() != null && !dto.getName().trim().isEmpty() ? dto.getName() : dto.getEmail().split("@")[0])
            .email(dto.getEmail())
            .bounced(false)
            .addedAt(Instant.now())
            .build();

    groupMemberRepository.save(member);

    publishEvent("MemberAdded", MemberAddedEvent.builder()
        .groupId(member.getGroupId())
        .memberId(member.getMemberId())
        .email(member.getEmail())
        .name(member.getName())
        .addedAt(member.getAddedAt())
        .build());

    return ResponseEntity.status(HttpStatus.CREATED).body(convertToMemberDTO(member));
  }

  @GetMapping("/{groupId}/members")
  public ResponseEntity<List<GroupMemberDTO>> getMembers(@PathVariable("groupId") String groupId) {
    groupRepository
        .findById(groupId)
        .orElseThrow(() -> new ResourceNotFoundException("Group not found with id: " + groupId));

    List<GroupMemberDTO> dtos =
        groupMemberRepository.findByGroupId(groupId).stream()
            .map(this::convertToMemberDTO)
            .collect(Collectors.toList());
    return ResponseEntity.ok(dtos);
  }

  @DeleteMapping("/{groupId}/members/{memberId}")
  public ResponseEntity<Void> removeMember(
      @PathVariable("groupId") String groupId, @PathVariable("memberId") String memberId) {
    groupMemberRepository
        .findById(groupId, memberId)
        .orElseThrow(() -> new ResourceNotFoundException("Member not found in group"));
    groupMemberRepository.delete(groupId, memberId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/members/search")
  public ResponseEntity<List<GroupMemberDTO>> searchMembersByEmail(@RequestParam("email") String email) {
    if (email == null || email.trim().isEmpty()) {
      return ResponseEntity.badRequest().build();
    }
    List<GroupMemberDTO> dtos = groupMemberRepository.findByEmail(email.trim()).stream()
        .map(this::convertToMemberDTO)
        .collect(Collectors.toList());
    return ResponseEntity.ok(dtos);
  }

  @PutMapping("/members/bounce")
  public ResponseEntity<Void> markBounce(@RequestBody Map<String, String> payload) {
    String email = payload.get("email");
    if (email == null || email.trim().isEmpty()) {
      return ResponseEntity.badRequest().build();
    }
    List<GroupMember> members = groupMemberRepository.findByEmail(email);
    for (GroupMember member : members) {
      member.setBounced(true);
      groupMemberRepository.save(member);
    }
    return ResponseEntity.noContent().build();
  }

  private GroupDTO convertToDTO(Group group) {
    return GroupDTO.builder()
        .groupId(group.getGroupId())
        .name(group.getName())
        .description(group.getDescription())
        .passcode(group.getPasscode())
        .optOutGeneral(group.getOptOutGeneral())
        .createdAt(group.getCreatedAt())
        .build();
  }

  private GroupMemberDTO convertToMemberDTO(GroupMember member) {
    return GroupMemberDTO.builder()
        .groupId(member.getGroupId())
        .memberId(member.getMemberId())
        .name(member.getName())
        .email(member.getEmail())
        .role("member")
        .bounced(member.getBounced())
        .joinedAt(member.getAddedAt())
        .build();
  }

  private void publishEvent(String detailType, Object detail) {
    try {
      String detailJson = objectMapper.writeValueAsString(detail);
      PutEventsRequestEntry entry = PutEventsRequestEntry.builder()
          .source("com.prayerlink.group-service")
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
}
