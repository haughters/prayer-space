package com.prayerlink.group.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import tools.jackson.databind.ObjectMapper;
import com.prayerlink.common.dto.GroupDTO;
import com.prayerlink.group.model.Group;
import com.prayerlink.group.repository.GroupMemberRepository;
import com.prayerlink.group.repository.GroupRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
public class GroupControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GroupRepository groupRepository;

    @MockitoBean
    private GroupMemberRepository groupMemberRepository;

    @MockitoBean
    private EventBridgeClient eventBridgeClient;

    @BeforeEach
    void setUp() {
        PutEventsResponse mockResponse = PutEventsResponse.builder().failedEntryCount(0).build();
        when(eventBridgeClient.putEvents(any(PutEventsRequest.class))).thenReturn(mockResponse);
    }

    @Test
    void testGroupCreationIntegration() throws Exception {
        GroupDTO inputDto = new GroupDTO();
        inputDto.setName("Integration Group");
        inputDto.setPasscode("INTG12");

        when(groupRepository.findByPasscode("INTG12")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(inputDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Integration Group"))
                .andExpect(jsonPath("$.passcode").value("INTG12"));

        verify(groupRepository, times(1)).save(any(Group.class));
    }

    @Test
    void testGroupUpdatesIntegration() throws Exception {
        String groupId = "group_int";
        Group existingGroup = new Group();
        existingGroup.setGroupId(groupId);
        existingGroup.setName("Old Name");
        existingGroup.setPasscode("OLDCODE");

        GroupDTO updateDto = new GroupDTO();
        updateDto.setName("New Name");
        updateDto.setPasscode("NEWCODE");

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(existingGroup));
        when(groupRepository.findByPasscode("NEWCODE")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/groups/" + groupId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.passcode").value("NEWCODE"));

        verify(groupRepository, times(1)).save(any(Group.class));
    }

    @Test
    void testGroupPasscodeValidationIntegration() throws Exception {
        String passcode = "VALIDCODE";
        Group group = new Group();
        group.setGroupId("group123");
        group.setPasscode(passcode);

        when(groupRepository.findByPasscode(passcode)).thenReturn(Optional.of(group));

        mockMvc.perform(get("/api/groups/validate?passcode=" + passcode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value("group123"));
    }
}
