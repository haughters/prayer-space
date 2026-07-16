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
        // 1. DeviceDTO
        DeviceDTO device = DeviceDTO.builder()
                .deviceId("device-123")
                .createdAt(Instant.now())
                .lastActiveAt(Instant.now())
                .platform("web")
                .build();
        assertEquals("device-123", device.getDeviceId());
        assertNotNull(device.getCreatedAt());
        assertNotNull(device.getLastActiveAt());
        assertEquals("web", device.getPlatform());
        
        DeviceDTO device2 = new DeviceDTO();
        device2.setDeviceId("device-456");
        assertEquals("device-456", device2.getDeviceId());

        // 2. GroupDTO
        GroupDTO group = GroupDTO.builder()
                .groupId("group-123")
                .name("Healing")
                .description("Pray for healing")
                .passcode("AAABBB")
                .optOutGeneral(true)
                .createdAt(Instant.now())
                .build();
        assertEquals("group-123", group.getGroupId());
        assertEquals("Healing", group.getName());
        assertEquals("Pray for healing", group.getDescription());
        assertEquals("AAABBB", group.getPasscode());
        assertTrue(group.getOptOutGeneral());
        assertNotNull(group.getCreatedAt());

        // 3. GroupMemberDTO
        GroupMemberDTO member = GroupMemberDTO.builder()
                .groupId("group-123")
                .memberId("member-123")
                .deviceId("device-123")
                .name("Alice")
                .email("alice@example.com")
                .role("MEMBER")
                .bounced(false)
                .joinedAt(Instant.now())
                .build();
        assertEquals("group-123", member.getGroupId());
        assertEquals("member-123", member.getMemberId());
        assertEquals("device-123", member.getDeviceId());
        assertEquals("Alice", member.getName());
        assertEquals("alice@example.com", member.getEmail());
        assertEquals("MEMBER", member.getRole());
        assertFalse(member.getBounced());
        assertNotNull(member.getJoinedAt());

        // 4. MemberDTO
        MemberDTO mDto = MemberDTO.builder()
                .groupId("group-123")
                .memberId("member-123")
                .deviceId("device-123")
                .email("alice@example.com")
                .role("MEMBER")
                .joinedAt(Instant.now())
                .build();
        assertEquals("group-123", mDto.getGroupId());
        assertEquals("member-123", mDto.getMemberId());
        assertEquals("device-123", mDto.getDeviceId());
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
        assertEquals("Healed!", update.getUpdateText());
        assertNotNull(update.getUpdatedAt());

        PrayerDTO prayer = PrayerDTO.builder()
                .prayerId("p-123")
                .deviceId("d-123")
                .prayerText("Please pray")
                .groupId("g-123")
                .assignedGroupId("g-123")
                .status("OPEN")
                .prayedForCount(5)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .hasPrayed(true)
                .updates(List.of(update))
                .build();
        assertEquals("p-123", prayer.getPrayerId());
        assertEquals("d-123", prayer.getDeviceId());
        assertEquals("Please pray", prayer.getPrayerText());
        assertEquals("g-123", prayer.getGroupId());
        assertEquals("g-123", prayer.getAssignedGroupId());
        assertEquals("OPEN", prayer.getStatus());
        assertEquals(5, prayer.getPrayedForCount());
        assertNotNull(prayer.getCreatedAt());
        assertNotNull(prayer.getUpdatedAt());
        assertTrue(prayer.getHasPrayed());
        assertEquals(1, prayer.getUpdates().size());

        // 6. Events
        MemberAddedEvent mae = MemberAddedEvent.builder()
                .groupId("g-1")
                .memberId("m-1")
                .email("m@ex.com")
                .name("Name")
                .addedAt(Instant.now())
                .build();
        assertEquals("g-1", mae.getGroupId());
        assertEquals("m-1", mae.getMemberId());
        assertEquals("m@ex.com", mae.getEmail());
        assertEquals("Name", mae.getName());
        assertNotNull(mae.getAddedAt());

        PrayerCreatedEvent pce = PrayerCreatedEvent.builder()
                .prayerId("p-1")
                .prayerText("text")
                .assignedGroupId("g-1")
                .build();
        assertEquals("p-1", pce.getPrayerId());
        assertEquals("text", pce.getPrayerText());
        assertEquals("g-1", pce.getAssignedGroupId());

        PrayerUpdatedEvent pue = PrayerUpdatedEvent.builder()
                .prayerId("p-1")
                .updateText("updated")
                .build();
        assertEquals("p-1", pue.getPrayerId());
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
