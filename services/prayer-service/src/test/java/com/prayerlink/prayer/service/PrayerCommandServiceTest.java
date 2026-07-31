package com.prayerlink.prayer.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.prayerlink.common.dto.PrayerDTO;
import com.prayerlink.common.enums.PrayerStatus;
import com.prayerlink.common.exception.BadRequestException;
import com.prayerlink.common.exception.UnauthorizedException;
import com.prayerlink.prayer.model.Prayer;
import com.prayerlink.prayer.model.mapper.PrayerMapper;
import com.prayerlink.prayer.repository.PrayerRepository;
import com.prayerlink.prayer.service.event.PrayerEventPublisher;
import com.prayerlink.prayer.service.security.GroupAuthorizationService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

@ExtendWith(MockitoExtension.class)
class PrayerCommandServiceTest {

    @Mock
    private PrayerRepository prayerRepository;

    @Mock
    private GroupAssignmentStrategy groupAssignment;

    @Mock
    private GroupAuthorizationService groupAuthorizationService;

    @Mock
    private PrayerEventPublisher eventPublisher;

    @Mock
    private PrayerMapper prayerMapper;

    @InjectMocks
    private PrayerCommandService commandService;

    private final UUID P1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID DEV1 = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private final UUID G1 = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private final UUID MISSING = UUID.fromString("00000000-0000-0000-0000-000000000005");

    // --- createPrayer ---

    @Test
    void createPrayerSavesAndPublishesEvent() {
        PrayerDTO input = PrayerDTO.builder()
                .deviceId(DEV1)
                .prayerText("Please pray for me")
                .groupId(G1)
                .build();
        PrayerDTO expected =
                PrayerDTO.builder().prayerId(P1).status(PrayerStatus.OPEN).build();

        when(groupAssignment.resolve(G1)).thenReturn(G1);
        when(prayerMapper.convertToDTO(any(Prayer.class))).thenReturn(expected);

        PrayerDTO result = commandService.createPrayer(input);

        assertEquals(PrayerStatus.OPEN, result.status());
        verify(prayerRepository).save(any(Prayer.class));
        verify(eventPublisher).publish(eq("PrayerCreated"), any());
    }

    // --- createUpdate ---

    @Test
    void createUpdateClosesAndPublishesEvent() {
        Prayer prayer = Prayer.builder()
                .prayerId(P1)
                .deviceId(DEV1)
                .status(PrayerStatus.OPEN)
                .build();

        when(prayerRepository.findById(P1)).thenReturn(Optional.of(prayer));

        commandService.createUpdate(P1, DEV1, "Answered!");

        verify(prayerRepository).savePrayerAndUpdate(argThat(p -> PrayerStatus.CLOSED == p.getStatus()), any());
        verify(eventPublisher).publish(eq("PrayerUpdated"), any());
    }

    @Test
    void createUpdateWithNullDeviceIdThrows() {
        assertThrows(BadRequestException.class, () -> commandService.createUpdate(P1, null, "text"));
    }

    @Test
    void createUpdateWhenClosedThrowsConflict() {
        Prayer prayer = Prayer.builder()
                .prayerId(P1)
                .deviceId(DEV1)
                .status(PrayerStatus.CLOSED)
                .build();
        when(prayerRepository.findById(P1)).thenReturn(Optional.of(prayer));

        ResponseStatusException ex =
                assertThrows(ResponseStatusException.class, () -> commandService.createUpdate(P1, DEV1, "text"));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void createUpdateWithWrongDeviceThrows() {
        Prayer prayer = Prayer.builder()
                .prayerId(P1)
                .deviceId(DEV1)
                .status(PrayerStatus.OPEN)
                .build();
        when(prayerRepository.findById(P1)).thenReturn(Optional.of(prayer));

        assertThrows(UnauthorizedException.class, () -> commandService.createUpdate(P1, UUID.randomUUID(), "text"));
    }

    @Test
    void createUpdateWithEmptyTextThrows() {
        assertThrows(BadRequestException.class, () -> commandService.createUpdate(P1, DEV1, ""));
    }

    @Test
    void createUpdateWithBlankTextThrows() {
        assertThrows(BadRequestException.class, () -> commandService.createUpdate(P1, DEV1, "   "));
    }

    @Test
    void createUpdateWithNullTextThrows() {
        assertThrows(BadRequestException.class, () -> commandService.createUpdate(P1, DEV1, null));
    }

    // --- markPrayed ---

    @Test
    void markPrayedValidatesAndRecords() {
        Prayer prayer = Prayer.builder()
                .prayerId(P1)
                .assignedGroupId(G1)
                .prayedForCount(5)
                .build();
        Prayer updated = Prayer.builder()
                .prayerId(P1)
                .assignedGroupId(G1)
                .prayedForCount(6)
                .build();

        PrayerDTO dto = PrayerDTO.builder().prayedForCount(6).hasPrayed(false).build();

        when(prayerRepository.findById(P1)).thenReturn(Optional.of(prayer)).thenReturn(Optional.of(updated));
        when(groupAuthorizationService.validateTokenForPrayer("token", G1)).thenReturn("user@test.com");
        when(prayerMapper.convertToDTO(updated, List.of(), "user@test.com")).thenReturn(dto);

        Map<String, Object> result = commandService.markPrayed(P1, "token");

        assertEquals("Thank you for praying", result.get("message"));
        assertEquals(6, result.get("prayedForCount"));
        assertNotNull(result.get("hasPrayed"));
        verify(prayerRepository).recordPrayer(P1, "user@test.com");
    }

    @Test
    void markPrayedWithNullAssignedGroupThrows() {
        Prayer prayer = Prayer.builder().prayerId(P1).assignedGroupId(null).build();
        when(prayerRepository.findById(P1)).thenReturn(Optional.of(prayer));

        assertThrows(UnauthorizedException.class, () -> commandService.markPrayed(P1, "token"));
    }

    @Test
    void markPrayedAlreadyPrayedThrowsConflict() {
        Prayer prayer = Prayer.builder().prayerId(P1).assignedGroupId(G1).build();

        when(prayerRepository.findById(P1)).thenReturn(Optional.of(prayer));
        when(groupAuthorizationService.validateTokenForPrayer("token", G1)).thenReturn("user@test.com");
        doThrow(software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException.builder()
                        .message("Already prayed")
                        .build())
                .when(prayerRepository)
                .recordPrayer(P1, "user@test.com");

        ResponseStatusException ex =
                assertThrows(ResponseStatusException.class, () -> commandService.markPrayed(P1, "token"));
        assertEquals(409, ex.getStatusCode().value());
    }

    // --- markPrayedAuth ---

    @Test
    void markPrayedAuthExtractsEmailAndRecords() {
        Prayer updated = Prayer.builder()
                .prayerId(P1)
                .prayedForCount(3)
                .prayedByEmails(Set.of("u@t.com"))
                .build();
        PrayerDTO dto = PrayerDTO.builder().prayedForCount(3).hasPrayed(true).build();

        // Using Mockito to return updated twice to simulate the find before/after
        when(prayerRepository.findById(P1)).thenReturn(Optional.of(updated)).thenReturn(Optional.of(updated));
        when(prayerMapper.convertToDTO(updated, List.of(), "u@t.com")).thenReturn(dto);

        Map<String, Object> result = commandService.markPrayedAuth(P1, "u@t.com");

        assertEquals("Thank you for praying", result.get("message"));
        assertEquals(3, result.get("prayedForCount"));
        assertEquals(true, result.get("hasPrayed"));
    }

    @Test
    void markPrayedAuthAlreadyPrayedThrowsConflict() {
        doThrow(ConditionalCheckFailedException.builder().message("conflict").build())
                .when(prayerRepository)
                .recordPrayer(P1, "u@t.com");
        ResponseStatusException ex =
                assertThrows(ResponseStatusException.class, () -> commandService.markPrayedAuth(P1, "u@t.com"));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void markPrayedResponseIncludesHasPrayed() {
        Prayer prayer = Prayer.builder()
                .prayerId(P1)
                .assignedGroupId(G1)
                .prayedForCount(1)
                .build();
        Prayer updated = Prayer.builder()
                .prayerId(P1)
                .assignedGroupId(G1)
                .prayedForCount(2)
                .prayedByEmails(Set.of("u@t.com"))
                .build();
        PrayerDTO dto = PrayerDTO.builder().prayedForCount(2).hasPrayed(true).build();

        when(prayerRepository.findById(P1)).thenReturn(Optional.of(prayer)).thenReturn(Optional.of(updated));
        when(groupAuthorizationService.validateTokenForPrayer("tok", G1)).thenReturn("u@t.com");
        when(prayerMapper.convertToDTO(updated, List.of(), "u@t.com")).thenReturn(dto);

        Map<String, Object> result = commandService.markPrayed(P1, "tok");
        assertEquals(true, result.get("hasPrayed"));
        assertEquals(2, result.get("prayedForCount"));
    }
}
