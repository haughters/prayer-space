package com.prayerlink.prayer.service;

import com.prayerlink.common.dto.PrayerDTO;
import com.prayerlink.common.enums.PrayerStatus;
import com.prayerlink.common.event.PrayerCreatedEvent;
import com.prayerlink.common.event.PrayerUpdatedEvent;
import com.prayerlink.common.exception.BadRequestException;
import com.prayerlink.common.exception.ResourceNotFoundException;
import com.prayerlink.common.exception.UnauthorizedException;
import com.prayerlink.prayer.model.Prayer;
import com.prayerlink.prayer.model.PrayerUpdate;
import com.prayerlink.prayer.model.mapper.PrayerMapper;
import com.prayerlink.prayer.repository.PrayerRepository;
import com.prayerlink.prayer.service.event.PrayerEventPublisher;
import com.prayerlink.prayer.service.security.GroupAuthorizationService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

@Service
public class PrayerCommandService {

    private final PrayerRepository prayerRepository;
    private final GroupAssignmentStrategy groupAssignment;
    private final GroupAuthorizationService groupAuthorizationService;
    private final PrayerEventPublisher eventPublisher;
    private final PrayerMapper prayerMapper;

    public PrayerCommandService(
            PrayerRepository prayerRepository,
            GroupAssignmentStrategy groupAssignment,
            GroupAuthorizationService groupAuthorizationService,
            PrayerEventPublisher eventPublisher,
            PrayerMapper prayerMapper) {
        this.prayerRepository = prayerRepository;
        this.groupAssignment = groupAssignment;
        this.groupAuthorizationService = groupAuthorizationService;
        this.eventPublisher = eventPublisher;
        this.prayerMapper = prayerMapper;
    }

    public PrayerDTO createPrayer(PrayerDTO dto) {
        UUID prayerId = UUID.randomUUID();
        UUID assignedGroupId = groupAssignment.resolve(dto.groupId());
        Instant now = Instant.now();

        Prayer prayer = buildNewPrayer(dto, prayerId, assignedGroupId, now);
        prayerRepository.save(prayer);
        publishPrayerCreatedEvent(prayer);

        return prayerMapper.convertToDTO(prayer);
    }

    public void createUpdate(UUID prayerId, UUID deviceId, String updateText) {
        if (deviceId == null) {
            throw new BadRequestException("Missing device ID");
        }
        if (updateText == null || updateText.trim().isEmpty()) {
            throw new BadRequestException("Update text is required");
        }

        Prayer prayer = findPrayerOrThrow(prayerId);

        if (PrayerStatus.CLOSED.equals(prayer.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Prayer is already closed");
        }
        if (!deviceId.equals(prayer.getDeviceId())) {
            throw new UnauthorizedException("You don't have permission to update this prayer");
        }

        Instant now = Instant.now();
        PrayerUpdate update = buildPrayerUpdate(prayerId, deviceId, updateText, now);

        prayer.setStatus(PrayerStatus.CLOSED);
        prayer.setUpdatedAt(now);

        prayerRepository.savePrayerAndUpdate(prayer, update);

        publishPrayerUpdatedEvent(prayerId, updateText, now);
    }

    public Map<String, Object> markPrayed(UUID prayerId, String intercessorToken) {
        Prayer prayer = findPrayerOrThrow(prayerId);
        UUID assignedGroupId = prayer.getAssignedGroupId();
        if (assignedGroupId == null) {
            throw new UnauthorizedException("Invalid or expired token");
        }

        String email = groupAuthorizationService.validateTokenForPrayer(intercessorToken, assignedGroupId);
        recordPrayerOrConflict(prayerId, email);

        Prayer updated = findPrayerOrThrow(prayerId);
        return buildPrayedResponse(prayerMapper.convertToDTO(updated, List.of(), email));
    }

    public Map<String, Object> markPrayedAuth(UUID prayerId, String email) {
        recordPrayerOrConflict(prayerId, email);
        Prayer updated = findPrayerOrThrow(prayerId);
        return buildPrayedResponse(prayerMapper.convertToDTO(updated, List.of(), email));
    }

    private Prayer buildNewPrayer(PrayerDTO dto, UUID prayerId, UUID assignedGroupId, Instant now) {
        return Prayer.builder()
                .prayerId(prayerId)
                .deviceId(dto.deviceId())
                .prayerText(dto.prayerText())
                .groupId(dto.groupId())
                .assignedGroupId(assignedGroupId)
                .status(PrayerStatus.OPEN)
                .prayedForCount(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private PrayerUpdate buildPrayerUpdate(UUID prayerId, UUID deviceId, String updateText, Instant now) {
        return PrayerUpdate.builder()
                .prayerId(prayerId)
                .updatedAt(now)
                .updateText(updateText)
                .updatedByDeviceId(deviceId)
                .build();
    }

    private Map<String, Object> buildPrayedResponse(PrayerDTO dto) {
        return Map.of(
                "message", "Thank you for praying",
                "prayedForCount", dto.prayedForCount(),
                "hasPrayed", Boolean.TRUE.equals(dto.hasPrayed()));
    }

    private void publishPrayerCreatedEvent(Prayer prayer) {
        eventPublisher.publish(
                "PrayerCreated",
                PrayerCreatedEvent.builder()
                        .prayerId(prayer.getPrayerId())
                        .deviceId(prayer.getDeviceId())
                        .prayerText(prayer.getPrayerText())
                        .assignedGroupId(prayer.getAssignedGroupId())
                        .createdAt(prayer.getCreatedAt())
                        .build());
    }

    private void publishPrayerUpdatedEvent(UUID prayerId, String updateText, Instant now) {
        eventPublisher.publish(
                "PrayerUpdated",
                PrayerUpdatedEvent.builder()
                        .prayerId(prayerId)
                        .updateText(updateText)
                        .status(PrayerStatus.CLOSED)
                        .updatedAt(now)
                        .build());
    }

    private Prayer findPrayerOrThrow(UUID prayerId) {
        return prayerRepository
                .findById(prayerId)
                .orElseThrow(() -> new ResourceNotFoundException("Prayer not found with id: " + prayerId));
    }

    private void recordPrayerOrConflict(UUID prayerId, String email) {
        try {
            prayerRepository.recordPrayer(prayerId, email);
        } catch (ConditionalCheckFailedException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already prayed for this request");
        }
    }
}
