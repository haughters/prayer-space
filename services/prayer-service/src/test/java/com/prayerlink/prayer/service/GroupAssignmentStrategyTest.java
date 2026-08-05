package com.prayerlink.prayer.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prayerlink.common.dto.GroupDTO;
import com.prayerlink.common.exception.BadRequestException;
import com.prayerlink.prayer.client.GroupServiceClient;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupAssignmentStrategyTest {

    @Mock
    private GroupServiceClient groupServiceClient;

    @InjectMocks
    private GroupAssignmentStrategy strategy;

    @Test
    void resolveWithExplicitGroupIdReturnsGroupId() {
        UUID groupId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        GroupDTO group = GroupDTO.builder().groupId(groupId).build();
        when(groupServiceClient.fetchGroup(groupId)).thenReturn(Optional.of(group));

        UUID result = strategy.resolve(groupId);
        assertEquals(groupId, result);
    }

    @Test
    void resolveWithExplicitGroupIdNotFoundThrows() {
        when(groupServiceClient.fetchGroup(java.util.UUID.fromString("00000000-0000-0000-0000-000000000006")))
                .thenReturn(Optional.empty());

        assertThrows(
                BadRequestException.class,
                () -> strategy.resolve(java.util.UUID.fromString("00000000-0000-0000-0000-000000000006")));
    }

    @Test
    void resolveWithNullGroupIdRoundRobins() {
        GroupDTO g1 = GroupDTO.builder()
                .groupId(java.util.UUID.fromString("00000000-0000-0000-0000-000000000005"))
                .optOutGeneral(false)
                .build();
        GroupDTO g2 = GroupDTO.builder()
                .groupId(java.util.UUID.fromString("00000000-0000-0000-0000-000000000006"))
                .optOutGeneral(false)
                .build();
        when(groupServiceClient.fetchAllGroups()).thenReturn(List.of(g1, g2));

        UUID first = strategy.resolve(null);
        UUID second = strategy.resolve(null);

        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(first, second);
    }

    @Test
    void resolveWithEmptyGroupIdRoundRobins() {
        GroupDTO g1 = GroupDTO.builder()
                .groupId(java.util.UUID.fromString("00000000-0000-0000-0000-000000000005"))
                .optOutGeneral(false)
                .build();
        when(groupServiceClient.fetchAllGroups()).thenReturn(List.of(g1));

        UUID result = strategy.resolve(null);
        assertEquals(java.util.UUID.fromString("00000000-0000-0000-0000-000000000005"), result);
    }

    @Test
    void resolveFiltersOptedOutGroups() {
        UUID eligibleId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID optedOutId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        GroupDTO eligible =
                GroupDTO.builder().groupId(eligibleId).optOutGeneral(false).build();
        GroupDTO optedOut =
                GroupDTO.builder().groupId(optedOutId).optOutGeneral(true).build();
        when(groupServiceClient.fetchAllGroups()).thenReturn(List.of(eligible, optedOut));

        UUID result = strategy.resolve(null);
        assertEquals(eligibleId, result);
    }

    @Test
    void resolveWithAllGroupsOptedOutReturnsNull() {
        GroupDTO optedOut = GroupDTO.builder()
                .groupId(UUID.randomUUID())
                .optOutGeneral(true)
                .build();
        when(groupServiceClient.fetchAllGroups()).thenReturn(List.of(optedOut));

        UUID result = strategy.resolve(null);
        assertNull(result);
    }

    @Test
    void resolveWithNoGroupsReturnsNull() {
        when(groupServiceClient.fetchAllGroups()).thenReturn(List.of());

        UUID result = strategy.resolve(null);
        assertNull(result);
    }

    @Test
    void resolveWithNullOptOutGeneralTreatsAsEligible() {
        GroupDTO group = GroupDTO.builder()
                .groupId(java.util.UUID.fromString("00000000-0000-0000-0000-000000000005"))
                .optOutGeneral(null)
                .build();
        when(groupServiceClient.fetchAllGroups()).thenReturn(List.of(group));

        UUID result = strategy.resolve(null);
        assertEquals(java.util.UUID.fromString("00000000-0000-0000-0000-000000000005"), result);
    }
}
