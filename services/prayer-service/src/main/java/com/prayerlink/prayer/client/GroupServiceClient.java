package com.prayerlink.prayer.client;

import com.prayerlink.common.dto.GroupDTO;
import com.prayerlink.common.dto.GroupMemberDTO;
import com.prayerlink.common.util.UrlUtils;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class GroupServiceClient {

    private final RestTemplate restTemplate;
    private final String groupServiceUrl;

    public GroupServiceClient(
            RestTemplate restTemplate, @Value("${services.group-service.url}") String groupServiceUrl) {
        this.restTemplate = restTemplate;
        this.groupServiceUrl = UrlUtils.cleanBaseUrl(groupServiceUrl);
    }

    public Optional<GroupDTO> fetchGroup(UUID groupId) {
        try {
            ResponseEntity<GroupDTO> response =
                    restTemplate.getForEntity(groupServiceUrl + "/api/groups/" + groupId, GroupDTO.class);
            return Optional.ofNullable(response.getBody());
        } catch (Exception e) {
            log.error("Failed to fetch group {}: {}", groupId, e.getMessage());
            return Optional.empty();
        }
    }

    public List<GroupDTO> fetchAllGroups() {
        try {
            ResponseEntity<List<GroupDTO>> response = restTemplate.exchange(
                    groupServiceUrl + "/api/groups", HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to fetch all groups: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<GroupMemberDTO> fetchGroupMembers(UUID groupId) {
        try {
            ResponseEntity<List<GroupMemberDTO>> response = restTemplate.exchange(
                    groupServiceUrl + "/api/groups/" + groupId + "/members",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {});
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to fetch group members for group {}: {}", groupId, e.getMessage());
            throw new RuntimeException("Failed to fetch group members", e);
        }
    }
}
