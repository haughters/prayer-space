package com.prayerlink.group.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.prayerlink.common.dto.GroupDTO;
import com.prayerlink.common.dto.GroupMemberDTO;
import com.prayerlink.group.model.Group;
import com.prayerlink.group.model.GroupMember;
import com.prayerlink.group.repository.GroupMemberRepository;
import com.prayerlink.group.repository.GroupRepository;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(GroupController.class)
@TestPropertySource(properties = {"aws.eventbridge.bus=prayer-link-bus"})
public class GroupControllerTest {

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
        PutEventsResponse mockResponse =
                PutEventsResponse.builder().failedEntryCount(0).build();
        when(eventBridgeClient.putEvents(any(PutEventsRequest.class))).thenReturn(mockResponse);
    }

    @Test
    void createGroupWithValidInputReturnsGroup() throws Exception {
        GroupDTO inputDto = new GroupDTO();
        inputDto.setName("Test Group");
        inputDto.setDescription("Test Description");
        inputDto.setPasscode("WELCOM");

        when(groupRepository.findByPasscode("WELCOM")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Group"))
                .andExpect(jsonPath("$.passcode").value("WELCOM"));
    }

    @Test
    void createGroupWithDuplicateNameReturns409() throws Exception {
        GroupDTO inputDto = new GroupDTO();
        inputDto.setName("Test Group");
        inputDto.setPasscode("DUPCOD");

        Group existingGroup = new Group();
        existingGroup.setGroupId(UUID.fromString("00000000-0000-0000-0000-000000000123"));
        existingGroup.setPasscode("DUPCOD");

        when(groupRepository.findByPasscode("DUPCOD")).thenReturn(Optional.of(existingGroup));

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDto)))
                .andExpect(status().isConflict());
    }

    @Test
    void getGroupWhenExistsReturnsGroup() throws Exception {
        UUID groupId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        Group group = new Group();
        group.setGroupId(groupId);
        group.setName("Assigned Group");

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));

        mockMvc.perform(get("/api/groups/" + groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value(groupId.toString()))
                .andExpect(jsonPath("$.name").value("Assigned Group"));
    }

    @Test
    void getGroupWhenNotFoundReturns404() throws Exception {
        UUID groupId = UUID.fromString("00000000-0000-0000-0000-000000000999");
        when(groupRepository.findById(groupId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/groups/" + groupId)).andExpect(status().isNotFound());
    }

    @Test
    void validatePasscodeWithValidCodeReturnsGroup() throws Exception {
        String passcode = "AAABBB";
        Group group = new Group();
        group.setGroupId(UUID.fromString("00000000-0000-0000-0000-000000000123"));
        group.setPasscode(passcode);

        when(groupRepository.findByPasscode(passcode)).thenReturn(Optional.of(group));

        mockMvc.perform(get("/api/groups/validate?passcode=" + passcode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value("00000000-0000-0000-0000-000000000123"));
    }

    @Test
    void validatePasscodeWithInvalidCodeReturns404() throws Exception {
        String passcode = "CCCDDD";
        when(groupRepository.findByPasscode(passcode)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/groups/validate?passcode=" + passcode)).andExpect(status().isNotFound());
    }

    @Test
    void addMemberWithValidEmailReturnsMember() throws Exception {
        UUID groupId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        GroupMemberDTO memberDto = new GroupMemberDTO();
        memberDto.setEmail("member@example.com");
        memberDto.setName("Member Name");

        Group group = new Group();
        group.setGroupId(groupId);
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));

        mockMvc.perform(post("/api/groups/" + groupId + "/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(memberDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("member@example.com"));
    }

    @Test
    void listGroupsReturnsAll() throws Exception {
        Group group1 = new Group();
        group1.setGroupId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        group1.setName("Group One");

        Group group2 = new Group();
        group2.setGroupId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        group2.setName("Group Two");

        when(groupRepository.findAll()).thenReturn(List.of(group1, group2));

        mockMvc.perform(get("/api/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].groupId").value("00000000-0000-0000-0000-000000000001"))
                .andExpect(jsonPath("$[1].groupId").value("00000000-0000-0000-0000-000000000002"));
    }

    @Test
    void updateGroupWithValidInputUpdatesFields() throws Exception {
        UUID groupId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        Group existingGroup = new Group();
        existingGroup.setGroupId(groupId);
        existingGroup.setName("Old Name");
        existingGroup.setPasscode("OLDCOD");

        GroupDTO updateDto = new GroupDTO();
        updateDto.setName("New Name");
        updateDto.setDescription("New Description");
        updateDto.setPasscode("NEWCOD");

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(existingGroup));
        when(groupRepository.findByPasscode("NEWCOD")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/groups/" + groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.description").value("New Description"))
                .andExpect(jsonPath("$.passcode").value("NEWCOD"));

        verify(groupRepository).save(any(Group.class));
    }

    @Test
    void deleteGroupCascadesMembers() throws Exception {
        UUID groupId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        Group group = new Group();
        group.setGroupId(groupId);

        GroupMember member1 = new GroupMember();
        member1.setGroupId(groupId);
        member1.setMemberId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        GroupMember member2 = new GroupMember();
        member2.setGroupId(groupId);
        member2.setMemberId(UUID.fromString("00000000-0000-0000-0000-000000000002"));

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupId(groupId)).thenReturn(List.of(member1, member2));

        mockMvc.perform(delete("/api/groups/" + groupId)).andExpect(status().isNoContent());

        verify(groupMemberRepository).delete(groupId, UUID.fromString("00000000-0000-0000-0000-000000000001"));
        verify(groupMemberRepository).delete(groupId, UUID.fromString("00000000-0000-0000-0000-000000000002"));
        verify(groupRepository).delete(groupId);
    }

    @Test
    void addMemberWithDuplicateEmailReturns409() throws Exception {
        UUID groupId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        GroupMemberDTO memberDto = new GroupMemberDTO();
        memberDto.setEmail("duplicate@example.com");
        memberDto.setName("Duplicate Name");

        Group group = new Group();
        group.setGroupId(groupId);

        GroupMember existingMember = new GroupMember();
        existingMember.setGroupId(groupId);
        existingMember.setEmail("duplicate@example.com");

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupId(groupId)).thenReturn(List.of(existingMember));

        mockMvc.perform(post("/api/groups/" + groupId + "/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(memberDto)))
                .andExpect(status().isConflict());
    }

    @Test
    void listMembersReturnsList() throws Exception {
        UUID groupId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        Group group = new Group();
        group.setGroupId(groupId);

        GroupMember member1 = new GroupMember();
        member1.setGroupId(groupId);
        member1.setMemberId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        member1.setEmail("member1@example.com");

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupId(groupId)).thenReturn(List.of(member1));

        mockMvc.perform(get("/api/groups/" + groupId + "/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].memberId").value("00000000-0000-0000-0000-000000000001"))
                .andExpect(jsonPath("$[0].email").value("member1@example.com"));
    }

    @Test
    void removeMemberWhenExistsReturns204() throws Exception {
        UUID groupId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        UUID memberId = UUID.fromString("00000000-0000-0000-0000-000000000456");
        GroupMember member = new GroupMember();
        member.setGroupId(groupId);
        member.setMemberId(memberId);

        when(groupMemberRepository.findById(groupId, memberId)).thenReturn(Optional.of(member));

        mockMvc.perform(delete("/api/groups/" + groupId + "/members/" + memberId))
                .andExpect(status().isNoContent());

        verify(groupMemberRepository).delete(groupId, memberId);
    }

    @Test
    void removeMemberWhenNotFoundReturns404() throws Exception {
        UUID groupId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        UUID memberId = UUID.fromString("00000000-0000-0000-0000-000000000999");

        when(groupMemberRepository.findById(groupId, memberId)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/groups/" + groupId + "/members/" + memberId))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchByEmailWhenFoundReturnsMemberships() throws Exception {
        String email = "search@example.com";
        GroupMember member1 = new GroupMember();
        member1.setGroupId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        member1.setMemberId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        member1.setEmail(email);

        GroupMember member2 = new GroupMember();
        member2.setGroupId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        member2.setMemberId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        member2.setEmail(email);

        when(groupMemberRepository.findByEmail(email)).thenReturn(List.of(member1, member2));

        mockMvc.perform(get("/api/groups/members/search?email=" + email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].groupId").value("00000000-0000-0000-0000-000000000001"))
                .andExpect(jsonPath("$[1].groupId").value("00000000-0000-0000-0000-000000000002"));
    }

    @Test
    void searchByEmailWhenNotFoundReturnsEmpty() throws Exception {
        String email = "notfound@example.com";
        when(groupMemberRepository.findByEmail(email)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/groups/members/search?email=" + email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void markBounceUpdatesStatus() throws Exception {
        String email = "bounce@example.com";
        GroupMember member1 = new GroupMember();
        member1.setGroupId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        member1.setMemberId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        member1.setEmail(email);
        member1.setBounced(false);

        when(groupMemberRepository.findByEmail(email)).thenReturn(List.of(member1));

        mockMvc.perform(put("/api/groups/members/bounce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isNoContent());

        verify(groupMemberRepository).save(argThat(m -> m.getBounced().equals(true)));
    }

    @Test
    void regeneratePasscodeGeneratesNewCode() throws Exception {
        UUID groupId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        Group existingGroup = new Group();
        existingGroup.setGroupId(groupId);
        existingGroup.setName("Group Name");
        existingGroup.setPasscode("OLDCOD");

        GroupDTO updateDto = new GroupDTO();
        updateDto.setPasscode("NEWCOD");

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(existingGroup));
        when(groupRepository.findByPasscode("NEWCOD")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/groups/" + groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passcode").value("NEWCOD"));

        verify(groupRepository).save(argThat(g -> g.getPasscode().equals("NEWCOD")));
    }
}
