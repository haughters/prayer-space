package com.prayerlink.common;

import static org.junit.jupiter.api.Assertions.*;

import com.prayerlink.common.dto.*;
import com.prayerlink.common.event.*;
import com.prayerlink.common.exception.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

public class CommonCoverageTest {

    @Test
    void testDTOsAndEventsAndExceptions() {
        java.util.UUID testUuid = java.util.UUID.randomUUID();
        // 1. DeviceDTO
        DeviceDTO device = DeviceDTO.builder()
                .deviceId(testUuid)
                .createdAt(Instant.now())
                .lastActiveAt(Instant.now())
                .platform("web")
                .build();
        assertEquals(testUuid, device.getDeviceId());
        assertNotNull(device.getCreatedAt());
        assertNotNull(device.getLastActiveAt());
        assertEquals("web", device.getPlatform());

        DeviceDTO device2 = new DeviceDTO();
        device2.setDeviceId(testUuid);
        assertEquals(testUuid, device2.getDeviceId());

        // 2. GroupDTO
        GroupDTO group = GroupDTO.builder()
                .groupId(testUuid)
                .name("Healing")
                .description("Pray for healing")
                .passcode("AAABBB")
                .optOutGeneral(true)
                .createdAt(Instant.now())
                .build();
        assertEquals(testUuid, group.getGroupId());
        assertEquals("Healing", group.getName());
        assertEquals("Pray for healing", group.getDescription());
        assertEquals("AAABBB", group.getPasscode());
        assertTrue(group.getOptOutGeneral());
        assertNotNull(group.getCreatedAt());

        // 3. GroupMemberDTO
        GroupMemberDTO member = GroupMemberDTO.builder()
                .groupId(testUuid)
                .memberId(testUuid)
                .deviceId(testUuid)
                .name("Alice")
                .email("alice@example.com")
                .role("MEMBER")
                .bounced(false)
                .joinedAt(Instant.now())
                .build();
        assertEquals(testUuid, member.getGroupId());
        assertEquals(testUuid, member.getMemberId());
        assertEquals(testUuid, member.getDeviceId());
        assertEquals("Alice", member.getName());
        assertEquals("alice@example.com", member.getEmail());
        assertEquals("MEMBER", member.getRole());
        assertFalse(member.getBounced());
        assertNotNull(member.getJoinedAt());

        // 4. MemberDTO
        MemberDTO mDto = MemberDTO.builder()
                .groupId(testUuid)
                .memberId(testUuid)
                .deviceId(testUuid)
                .email("alice@example.com")
                .role("MEMBER")
                .joinedAt(Instant.now())
                .build();
        assertEquals(testUuid, mDto.getGroupId());
        assertEquals(testUuid, mDto.getMemberId());
        assertEquals(testUuid, mDto.getDeviceId());
        assertEquals("alice@example.com", mDto.getEmail());
        assertEquals("MEMBER", mDto.getRole());
        assertNotNull(mDto.getJoinedAt());

        MemberDTO mDto2 = new MemberDTO();
        mDto2.setEmail("bob@example.com");
        assertEquals("bob@example.com", mDto2.getEmail());

        // 5. PrayerUpdateDTO
        PrayerUpdateDTO update = PrayerUpdateDTO.builder()
                .updateText("Healed!")
                .updatedAt(Instant.now())
                .build();
        assertEquals("Healed!", update.updateText());
        assertNotNull(update.updatedAt());

        PrayerDTO prayer = PrayerDTO.builder()
                .prayerId(testUuid)
                .deviceId(testUuid)
                .prayerText("Please pray")
                .groupId(testUuid)
                .assignedGroupId(testUuid)
                .status(com.prayerlink.common.enums.PrayerStatus.OPEN)
                .prayedForCount(5)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .hasPrayed(true)
                .updates(List.of(update))
                .build();
        assertEquals(testUuid, prayer.prayerId());
        assertEquals(testUuid, prayer.deviceId());
        assertEquals("Please pray", prayer.prayerText());
        assertEquals(testUuid, prayer.groupId());
        assertEquals(testUuid, prayer.assignedGroupId());
        assertEquals(com.prayerlink.common.enums.PrayerStatus.OPEN, prayer.status());
        assertEquals(5, prayer.prayedForCount());
        assertNotNull(prayer.createdAt());
        assertNotNull(prayer.updatedAt());
        assertTrue(prayer.hasPrayed());
        assertEquals(1, prayer.updates().size());

        // 6. Events
        MemberAddedEvent mae = MemberAddedEvent.builder()
                .groupId(testUuid)
                .memberId(testUuid)
                .email("m@ex.com")
                .name("Name")
                .addedAt(Instant.now())
                .build();
        assertEquals(testUuid, mae.getGroupId());
        assertEquals(testUuid, mae.getMemberId());
        assertEquals("m@ex.com", mae.getEmail());
        assertEquals("Name", mae.getName());
        assertNotNull(mae.getAddedAt());

        PrayerCreatedEvent pce = PrayerCreatedEvent.builder()
                .prayerId(testUuid)
                .prayerText("text")
                .assignedGroupId(testUuid)
                .build();
        assertEquals(testUuid, pce.getPrayerId());
        assertEquals("text", pce.getPrayerText());
        assertEquals(testUuid, pce.getAssignedGroupId());

        PrayerUpdatedEvent pue = PrayerUpdatedEvent.builder()
                .prayerId(testUuid)
                .updateText("updated")
                .build();
        assertEquals(testUuid, pue.getPrayerId());
        assertEquals("updated", pue.getUpdateText());

        // 7. Exceptions
        BadRequestException bre = new BadRequestException("bad request");
        assertEquals("bad request", bre.getMessage());

        ResourceNotFoundException rnfe = new ResourceNotFoundException("not found");
        assertEquals("not found", rnfe.getMessage());

        UnauthorizedException ue = new UnauthorizedException("unauthorized");
        assertEquals("unauthorized", ue.getMessage());

        ErrorResponse er = new ErrorResponse(Instant.now(), 400, "Bad Request", "detail", "/path");
        assertEquals(400, er.getStatus());
        assertEquals("Bad Request", er.getError());
        assertEquals("detail", er.getMessage());
        assertEquals("/path", er.getPath());
        assertNotNull(er.getTimestamp());
    }
}
