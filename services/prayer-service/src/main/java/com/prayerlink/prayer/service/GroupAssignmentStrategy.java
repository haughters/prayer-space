package com.prayerlink.prayer.service;

import com.prayerlink.common.dto.GroupDTO;
import com.prayerlink.common.exception.BadRequestException;
import com.prayerlink.prayer.client.GroupServiceClient;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Determines which prayer group a new prayer request should be assigned to.
 *
 * <p>If the submitter specifies a group ID, that group is validated and used. Otherwise, the prayer
 * is assigned via round-robin across all eligible (non-opt-out) groups.
 */
@Slf4j
@Component
public class GroupAssignmentStrategy {

    private final GroupServiceClient groupServiceClient;
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);

    public GroupAssignmentStrategy(GroupServiceClient groupServiceClient) {
        this.groupServiceClient = groupServiceClient;
    }

    /**
     * Resolves the group a prayer should be assigned to.
     *
     * @param requestedGroupId the group ID from the prayer request, may be null
     * @return the assigned group ID, or null if no group could be assigned
     * @throws BadRequestException if an explicit group ID was provided but doesn't exist
     */
    public UUID resolve(UUID requestedGroupId) {
        if (requestedGroupId != null) {
            return resolveExplicit(requestedGroupId);
        }
        return resolveRoundRobin();
    }

    private UUID resolveExplicit(UUID groupId) {
        return groupServiceClient
                .fetchGroup(groupId)
                .map(GroupDTO::getGroupId)
                .orElseThrow(() -> new BadRequestException("Group not found: " + groupId));
    }

    private UUID resolveRoundRobin() {
        List<GroupDTO> allGroups = groupServiceClient.fetchAllGroups();

        List<GroupDTO> eligibleGroups = allGroups.stream()
                .filter(g -> g.getOptOutGeneral() == null || !g.getOptOutGeneral())
                .toList();

        if (eligibleGroups.isEmpty()) {
            log.warn("No eligible groups for round-robin assignment. Prayer will be unassigned.");
            return null;
        }

        int index = Math.abs(roundRobinCounter.getAndIncrement() % eligibleGroups.size());
        return eligibleGroups.get(index).getGroupId();
    }
}
