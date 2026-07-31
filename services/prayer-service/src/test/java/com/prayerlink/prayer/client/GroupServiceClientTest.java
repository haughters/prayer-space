package com.prayerlink.prayer.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.prayerlink.common.dto.GroupDTO;
import com.prayerlink.common.dto.GroupMemberDTO;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class GroupServiceClientTest {

    @Mock
    private RestTemplate restTemplate;

    private GroupServiceClient client;

    private static final String URL = "http://localhost:8083";

    private static final UUID GROUP_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        client = new GroupServiceClient(restTemplate, URL);
    }

    @Test
    void fetchGroup_Success_ReturnsGroup() {
        GroupDTO group = new GroupDTO();
        ResponseEntity<GroupDTO> response = new ResponseEntity<>(group, HttpStatus.OK);
        when(restTemplate.getForEntity(URL + "/api/groups/" + GROUP_ID, GroupDTO.class))
                .thenReturn(response);

        Optional<GroupDTO> result = client.fetchGroup(GROUP_ID);
        assertTrue(result.isPresent());
        assertEquals(group, result.get());
    }

    @Test
    void fetchGroup_Failure_ReturnsEmpty() {
        when(restTemplate.getForEntity(URL + "/api/groups/" + GROUP_ID, GroupDTO.class))
                .thenThrow(new RestClientException("Error"));

        Optional<GroupDTO> result = client.fetchGroup(GROUP_ID);
        assertFalse(result.isPresent());
    }

    @Test
    void fetchAllGroups_Success_ReturnsList() {
        List<GroupDTO> groups = Collections.singletonList(new GroupDTO());
        ResponseEntity<List<GroupDTO>> response = new ResponseEntity<>(groups, HttpStatus.OK);
        when(restTemplate.exchange(
                        eq(URL + "/api/groups"),
                        eq(HttpMethod.GET),
                        eq(null),
                        org.mockito.ArgumentMatchers.<ParameterizedTypeReference<List<GroupDTO>>>any()))
                .thenReturn(response);

        List<GroupDTO> result = client.fetchAllGroups();
        assertEquals(1, result.size());
    }

    @Test
    void fetchAllGroups_Failure_ReturnsEmptyList() {
        when(restTemplate.exchange(
                        eq(URL + "/api/groups"),
                        eq(HttpMethod.GET),
                        eq(null),
                        org.mockito.ArgumentMatchers.<ParameterizedTypeReference<List<GroupDTO>>>any()))
                .thenThrow(new RestClientException("Error"));

        List<GroupDTO> result = client.fetchAllGroups();
        assertTrue(result.isEmpty());
    }

    @Test
    void fetchGroupMembers_Success_ReturnsMembers() {
        List<GroupMemberDTO> members = Collections.singletonList(new GroupMemberDTO());
        ResponseEntity<List<GroupMemberDTO>> response = new ResponseEntity<>(members, HttpStatus.OK);
        when(restTemplate.exchange(
                        eq(URL + "/api/groups/" + GROUP_ID + "/members"),
                        eq(HttpMethod.GET),
                        eq(null),
                        org.mockito.ArgumentMatchers.<ParameterizedTypeReference<List<GroupMemberDTO>>>any()))
                .thenReturn(response);

        List<GroupMemberDTO> result = client.fetchGroupMembers(GROUP_ID);
        assertEquals(1, result.size());
    }

    @Test
    void fetchGroupMembers_Failure_ThrowsException() {
        when(restTemplate.exchange(
                        eq(URL + "/api/groups/" + GROUP_ID + "/members"),
                        eq(HttpMethod.GET),
                        eq(null),
                        org.mockito.ArgumentMatchers.<ParameterizedTypeReference<List<GroupMemberDTO>>>any()))
                .thenThrow(new RestClientException("Error"));

        assertThrows(RuntimeException.class, () -> client.fetchGroupMembers(GROUP_ID));
    }
}
