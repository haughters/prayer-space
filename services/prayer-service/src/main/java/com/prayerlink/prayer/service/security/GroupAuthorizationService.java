package com.prayerlink.prayer.service.security;

import com.prayerlink.common.dto.GroupMemberDTO;
import com.prayerlink.prayer.client.GroupServiceClient;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GroupAuthorizationService {

    private final GroupServiceClient groupServiceClient;
    private final IntercessorTokenValidator tokenValidator;

    public GroupAuthorizationService(GroupServiceClient groupServiceClient, IntercessorTokenValidator tokenValidator) {
        this.groupServiceClient = groupServiceClient;
        this.tokenValidator = tokenValidator;
    }

    public String validateTokenForPrayer(String token, UUID assignedGroupId) {
        List<GroupMemberDTO> members = groupServiceClient.fetchGroupMembers(assignedGroupId);
        return tokenValidator.validateForPrayer(token, assignedGroupId.toString(), members);
    }

    public void validateTokenForGroup(String token, UUID groupId) {
        List<GroupMemberDTO> members = groupServiceClient.fetchGroupMembers(groupId);
        tokenValidator.validateForGroup(token, groupId.toString(), members);
    }

    public void assertIsMember(String email, UUID groupId) {
        List<GroupMemberDTO> members = groupServiceClient.fetchGroupMembers(groupId);
        boolean isMember = members.stream().anyMatch(m -> email.equalsIgnoreCase(m.getEmail()));
        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not belong to this group");
        }
    }
}
