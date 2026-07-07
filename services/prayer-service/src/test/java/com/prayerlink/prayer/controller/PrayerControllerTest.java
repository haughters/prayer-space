package com.prayerlink.prayer.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import tools.jackson.databind.ObjectMapper;
import com.prayerlink.common.dto.GroupDTO;
import com.prayerlink.common.dto.GroupMemberDTO;
import com.prayerlink.common.dto.PrayerDTO;
import com.prayerlink.prayer.model.Prayer;
import com.prayerlink.prayer.repository.PrayerRepository;
import com.prayerlink.prayer.repository.PrayerUpdateRepository;
import com.prayerlink.prayer.util.JwtUtil;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

@WebMvcTest(PrayerController.class)
@TestPropertySource(properties = {
    "services.group-service.url=http://localhost:8083",
    "aws.eventbridge.bus=prayer-link-bus",
    "hmac.secret-key=test-secret-key-must-be-very-long-and-secure-for-hmac-sha-256-prayer-link"
})
public class PrayerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PrayerRepository prayerRepository;

    @MockitoBean
    private PrayerUpdateRepository prayerUpdateRepository;

    @MockitoBean
    private RestTemplate restTemplate;

    @MockitoBean
    private EventBridgeClient eventBridgeClient;

    @MockitoBean
    private JwtUtil jwtUtil;

    private static final String SECRET_KEY = "test-secret-key-must-be-very-long-and-secure-for-hmac-sha-256-prayer-link";

    @BeforeEach
    void setUp() {
        PutEventsResponse mockResponse = PutEventsResponse.builder().failedEntryCount(0).build();
        when(eventBridgeClient.putEvents(any(PutEventsRequest.class))).thenReturn(mockResponse);
    }

    private String computeHmac(String data, String secretKey) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secretKey.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] rawHmac = mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC", e);
        }
    }

    // 1. createPrayerWithValidInputReturnsPrayer
    @Test
    void createPrayerWithValidInputReturnsPrayer() throws Exception {
        PrayerDTO inputDto = PrayerDTO.builder()
                .prayerText("This is a valid prayer request that is longer than ten characters.")
                .deviceId("device123")
                .groupId("group123")
                .build();

        GroupDTO mockGroup = new GroupDTO();
        mockGroup.setGroupId("group123");
        mockGroup.setName("Test Group");

        when(restTemplate.getForEntity(anyString(), eq(GroupDTO.class)))
                .thenReturn(new ResponseEntity<>(mockGroup, HttpStatus.OK));

        mockMvc.perform(post("/api/prayers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(inputDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.prayerText").value(inputDto.getPrayerText()))
                .andExpect(jsonPath("$.deviceId").value(inputDto.getDeviceId()))
                .andExpect(jsonPath("$.assignedGroupId").value("group123"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    // 2. createPrayerWithGroupIdAssignsSpecifiedGroup
    @Test
    void createPrayerWithGroupIdAssignsSpecifiedGroup() throws Exception {
        PrayerDTO inputDto = PrayerDTO.builder()
                .prayerText("This is a valid prayer request that is longer than ten characters.")
                .deviceId("device123")
                .groupId("group123")
                .build();

        GroupDTO mockGroup = new GroupDTO();
        mockGroup.setGroupId("group123");
        mockGroup.setName("Test Group");

        when(restTemplate.getForEntity(anyString(), eq(GroupDTO.class)))
                .thenReturn(new ResponseEntity<>(mockGroup, HttpStatus.OK));

        mockMvc.perform(post("/api/prayers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(inputDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignedGroupId").value("group123"));
    }

    // 3. createPrayerWithoutGroupIdRoundRobinAssigns
    @Test
    void createPrayerWithoutGroupIdRoundRobinAssigns() throws Exception {
        PrayerDTO inputDto = PrayerDTO.builder()
                .prayerText("This is a valid prayer request that is longer than ten characters.")
                .deviceId("device123")
                .build();

        GroupDTO group1 = new GroupDTO();
        group1.setGroupId("group1");
        group1.setOptOutGeneral(false);

        GroupDTO group2 = new GroupDTO();
        group2.setGroupId("group2");
        group2.setOptOutGeneral(false);

        when(restTemplate.exchange(
                eq("http://localhost:8083/api/groups"),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(List.of(group1, group2), HttpStatus.OK));

        mockMvc.perform(post("/api/prayers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(inputDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignedGroupId").value(org.hamcrest.Matchers.oneOf("group1", "group2")));
    }

    // 4. createPrayerWithTextTooShortReturns400
    @Test
    void createPrayerWithTextTooShortReturns400() throws Exception {
        PrayerDTO inputDto = PrayerDTO.builder()
                .prayerText("Short")
                .deviceId("device123")
                .build();

        mockMvc.perform(post("/api/prayers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(inputDto)))
                .andExpect(status().isBadRequest());
    }

    // 5. createPrayerWithTextTooLongReturns400
    @Test
    void createPrayerWithTextTooLongReturns400() throws Exception {
        PrayerDTO inputDto = PrayerDTO.builder()
                .prayerText("a".repeat(2001))
                .deviceId("device123")
                .build();

        mockMvc.perform(post("/api/prayers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(inputDto)))
                .andExpect(status().isBadRequest());
    }

    // 6. createPrayerWithEmptyBodyReturns400
    @Test
    void createPrayerWithEmptyBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/prayers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(""))
                .andExpect(status().isBadRequest());
    }

    // 7. createPrayerPublishesEventBridgeEvent
    @Test
    void createPrayerPublishesEventBridgeEvent() throws Exception {
        PrayerDTO inputDto = PrayerDTO.builder()
                .prayerText("This is a valid prayer request that is longer than ten characters.")
                .deviceId("device123")
                .groupId("group123")
                .build();

        GroupDTO mockGroup = new GroupDTO();
        mockGroup.setGroupId("group123");
        mockGroup.setName("Test Group");

        when(restTemplate.getForEntity(anyString(), eq(GroupDTO.class)))
                .thenReturn(new ResponseEntity<>(mockGroup, HttpStatus.OK));

        mockMvc.perform(post("/api/prayers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(inputDto)))
                .andExpect(status().isCreated());

        ArgumentCaptor<PutEventsRequest> captor = ArgumentCaptor.forClass(PutEventsRequest.class);
        verify(eventBridgeClient, atLeastOnce()).putEvents(captor.capture());
        PutEventsRequest req = captor.getValue();
        assertEquals("PrayerCreated", req.entries().get(0).detailType());
    }

    // 8. getPrayerWhenExistsReturnsPrayerWithUpdates
    @Test
    void getPrayerWhenExistsReturnsPrayerWithUpdates() throws Exception {
        String prayerId = "prayer123";
        Prayer mockPrayer = new Prayer();
        mockPrayer.setPrayerId(prayerId);
        mockPrayer.setPrayerText("Test prayer request");
        mockPrayer.setStatus("OPEN");

        when(prayerRepository.findById(prayerId)).thenReturn(Optional.of(mockPrayer));

        mockMvc.perform(get("/api/prayers/" + prayerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prayerId").value(prayerId))
                .andExpect(jsonPath("$.prayerText").value("Test prayer request"));
    }

    // 9. getPrayerWhenNotFoundReturns404
    @Test
    void getPrayerWhenNotFoundReturns404() throws Exception {
        String prayerId = "nonexistent";
        when(prayerRepository.findById(prayerId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/prayers/" + prayerId))
                .andExpect(status().isNotFound());
    }

    // 10. getPrayersByDeviceReturnsList
    @Test
    void getPrayersByDeviceReturnsList() throws Exception {
        String deviceId = "device123";
        Prayer p1 = new Prayer();
        p1.setPrayerId("p1");
        p1.setDeviceId(deviceId);
        p1.setPrayerText("Test prayer text 1");
        p1.setStatus("OPEN");

        when(prayerRepository.findByDeviceId(deviceId)).thenReturn(List.of(p1));

        mockMvc.perform(get("/api/prayers").param("deviceId", deviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].prayerId").value("p1"));
    }

    // 11. getPrayersByDeviceWithEmptyListReturns200
    @Test
    void getPrayersByDeviceWithEmptyListReturns200() throws Exception {
        String deviceId = "device123";
        when(prayerRepository.findByDeviceId(deviceId)).thenReturn(List.of());

        mockMvc.perform(get("/api/prayers").param("deviceId", deviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // 12. closePrayerWithValidUpdateSetsStatusClosed
    @Test
    void closePrayerWithValidUpdateSetsStatusClosed() throws Exception {
        String prayerId = "prayer123";
        Prayer mockPrayer = new Prayer();
        mockPrayer.setPrayerId(prayerId);
        mockPrayer.setDeviceId("device123");
        mockPrayer.setStatus("OPEN");

        when(prayerRepository.findById(prayerId)).thenReturn(Optional.of(mockPrayer));

        mockMvc.perform(post("/api/prayers/" + prayerId + "/updates")
                .header("X-Device-ID", "device123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"updateText\":\"God has answered my prayers!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Prayer updated and closed successfully"));
    }

    // 13. closePrayerWhenAlreadyClosedReturns409
    @Test
    void closePrayerWhenAlreadyClosedReturns409() throws Exception {
        String prayerId = "prayer123";
        Prayer mockPrayer = new Prayer();
        mockPrayer.setPrayerId(prayerId);
        mockPrayer.setDeviceId("device123");
        mockPrayer.setStatus("CLOSED");

        when(prayerRepository.findById(prayerId)).thenReturn(Optional.of(mockPrayer));

        mockMvc.perform(post("/api/prayers/" + prayerId + "/updates")
                .header("X-Device-ID", "device123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"updateText\":\"God has answered my prayers!\"}"))
                .andExpect(status().isConflict());
    }

    // 14. closePrayerPublishesEventBridgeEvent
    @Test
    void closePrayerPublishesEventBridgeEvent() throws Exception {
        String prayerId = "prayer123";
        Prayer mockPrayer = new Prayer();
        mockPrayer.setPrayerId(prayerId);
        mockPrayer.setDeviceId("device123");
        mockPrayer.setStatus("OPEN");

        when(prayerRepository.findById(prayerId)).thenReturn(Optional.of(mockPrayer));

        mockMvc.perform(post("/api/prayers/" + prayerId + "/updates")
                .header("X-Device-ID", "device123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"updateText\":\"God has answered my prayers!\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<PutEventsRequest> captor = ArgumentCaptor.forClass(PutEventsRequest.class);
        verify(eventBridgeClient, atLeastOnce()).putEvents(captor.capture());
        assertEquals("PrayerUpdated", captor.getValue().entries().get(0).detailType());
    }

    // 15. markPrayedWithValidHmacTokenIncrementsCount
    @Test
    void markPrayedWithValidHmacTokenIncrementsCount() throws Exception {
        String prayerId = "prayer123";
        String groupId = "group123";
        String email = "member@example.com";
        long expiry = Instant.now().getEpochSecond() + 3600;

        String payloadStr = groupId + ":" + email + ":" + expiry;
        String signature = computeHmac(payloadStr, SECRET_KEY);
        String token = signature + "|" + groupId + "|" + expiry;

        Prayer mockPrayer = new Prayer();
        mockPrayer.setPrayerId(prayerId);
        mockPrayer.setAssignedGroupId(groupId);
        mockPrayer.setPrayedForCount(5);

        when(prayerRepository.findById(prayerId)).thenReturn(Optional.of(mockPrayer));

        GroupMemberDTO member = new GroupMemberDTO();
        member.setEmail(email);
        member.setName("Test Member");
        member.setBounced(false);

        List<GroupMemberDTO> members = List.of(member);
        ResponseEntity<List<GroupMemberDTO>> responseEntity = new ResponseEntity<>(members, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://localhost:8083/api/groups/" + groupId + "/members"),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        doNothing().when(prayerRepository).recordPrayer(prayerId, email);

        Prayer updatedPrayer = new Prayer();
        updatedPrayer.setPrayerId(prayerId);
        updatedPrayer.setAssignedGroupId(groupId);
        updatedPrayer.setPrayedForCount(6);

        // First findById call returns mockPrayer, second returns updatedPrayer
        when(prayerRepository.findById(prayerId)).thenReturn(Optional.of(mockPrayer)).thenReturn(Optional.of(updatedPrayer));

        mockMvc.perform(post("/api/prayers/" + prayerId + "/prayed")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("intercessorToken", token))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prayedForCount").value(6));
    }

    // 16. markPrayedWithExpiredTokenReturns401
    @Test
    void markPrayedWithExpiredTokenReturns401() throws Exception {
        String prayerId = "prayer123";
        String groupId = "group123";
        String email = "member@example.com";
        long expiry = Instant.now().getEpochSecond() - 3600; // Past expiry

        String payloadStr = groupId + ":" + email + ":" + expiry;
        String signature = computeHmac(payloadStr, SECRET_KEY);
        String token = signature + "|" + groupId + "|" + expiry;

        mockMvc.perform(post("/api/prayers/" + prayerId + "/prayed")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("intercessorToken", token))))
                .andExpect(status().isUnauthorized());
    }

    // 17. markPrayedWithInvalidTokenReturns401
    @Test
    void markPrayedWithInvalidTokenReturns401() throws Exception {
        String prayerId = "prayer123";
        String token = "invalidSignature|group123|" + (Instant.now().getEpochSecond() + 3600);

        Prayer mockPrayer = new Prayer();
        mockPrayer.setPrayerId(prayerId);
        mockPrayer.setAssignedGroupId("group123");
        when(prayerRepository.findById(prayerId)).thenReturn(Optional.of(mockPrayer));

        GroupMemberDTO member = new GroupMemberDTO();
        member.setEmail("member@example.com");
        ResponseEntity<List<GroupMemberDTO>> responseEntity = new ResponseEntity<>(List.of(member), HttpStatus.OK);
        when(restTemplate.exchange(
                eq("http://localhost:8083/api/groups/group123/members"),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        mockMvc.perform(post("/api/prayers/" + prayerId + "/prayed")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("intercessorToken", token))))
                .andExpect(status().isUnauthorized());
    }

    // 18. markPrayedIsIdempotentForSameEmail
    @Test
    void markPrayedIsIdempotentForSameEmail() throws Exception {
        String prayerId = "prayer123";
        String groupId = "group123";
        String email = "member@example.com";
        long expiry = Instant.now().getEpochSecond() + 3600;

        String payloadStr = groupId + ":" + email + ":" + expiry;
        String signature = computeHmac(payloadStr, SECRET_KEY);
        String token = signature + "|" + groupId + "|" + expiry;

        Prayer mockPrayer = new Prayer();
        mockPrayer.setPrayerId(prayerId);
        mockPrayer.setAssignedGroupId(groupId);

        when(prayerRepository.findById(prayerId)).thenReturn(Optional.of(mockPrayer));

        GroupMemberDTO member = new GroupMemberDTO();
        member.setEmail(email);

        List<GroupMemberDTO> members = List.of(member);
        ResponseEntity<List<GroupMemberDTO>> responseEntity = new ResponseEntity<>(members, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://localhost:8083/api/groups/" + groupId + "/members"),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        doThrow(software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException.builder().message("Already prayed").build())
                .when(prayerRepository).recordPrayer(prayerId, email);

        mockMvc.perform(post("/api/prayers/" + prayerId + "/prayed")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("intercessorToken", token))))
                .andExpect(status().isConflict());
    }

    // 19. getGroupPrayersWithValidTokenReturnsOpenPrayers
    @Test
    void getGroupPrayersWithValidTokenReturnsOpenPrayers() throws Exception {
        String groupId = "group123";
        String email = "member@example.com";
        long expiry = Instant.now().getEpochSecond() + 3600;

        String payloadStr = groupId + ":" + email + ":" + expiry;
        String signature = computeHmac(payloadStr, SECRET_KEY);
        String token = signature + "|" + groupId + "|" + expiry;

        GroupMemberDTO member = new GroupMemberDTO();
        member.setEmail(email);

        List<GroupMemberDTO> members = List.of(member);
        ResponseEntity<List<GroupMemberDTO>> responseEntity = new ResponseEntity<>(members, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://localhost:8083/api/groups/" + groupId + "/members"),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        Prayer openPrayer = new Prayer();
        openPrayer.setPrayerId("p1");
        openPrayer.setStatus("OPEN");
        openPrayer.setGroupId(groupId);

        Prayer closedPrayer = new Prayer();
        closedPrayer.setPrayerId("p2");
        closedPrayer.setStatus("CLOSED");
        closedPrayer.setGroupId(groupId);

        when(prayerRepository.findByGroupId(groupId)).thenReturn(List.of(openPrayer, closedPrayer));

        mockMvc.perform(get("/api/prayers/group/" + groupId).param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].prayerId").value("p1"));
    }

    // 20. getGroupPrayersWithInvalidTokenReturns401
    @Test
    void getGroupPrayersWithInvalidTokenReturns401() throws Exception {
        String groupId = "group123";
        String token = "badtoken|group123|" + (Instant.now().getEpochSecond() + 3600);

        GroupMemberDTO member = new GroupMemberDTO();
        member.setEmail("member@example.com");
        ResponseEntity<List<GroupMemberDTO>> responseEntity = new ResponseEntity<>(List.of(member), HttpStatus.OK);
        when(restTemplate.exchange(
                eq("http://localhost:8083/api/groups/" + groupId + "/members"),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        mockMvc.perform(get("/api/prayers/group/" + groupId).param("token", token))
                .andExpect(status().isUnauthorized());
    }

    // 21. markPrayedAuthWithValidJwtIncrementsCount
    @Test
    void markPrayedAuthWithValidJwtIncrementsCount() throws Exception {
        String prayerId = "prayer123";
        String jwtToken = "valid-jwt";
        String email = "portal-user@example.com";

        com.auth0.jwt.interfaces.DecodedJWT decodedJwt = mock(com.auth0.jwt.interfaces.DecodedJWT.class);
        com.auth0.jwt.interfaces.Claim emailClaim = mock(com.auth0.jwt.interfaces.Claim.class);
        when(emailClaim.asString()).thenReturn(email);
        when(decodedJwt.getClaim("email")).thenReturn(emailClaim);
        when(jwtUtil.verifyToken(jwtToken)).thenReturn(decodedJwt);

        doNothing().when(prayerRepository).recordPrayer(prayerId, email);

        Prayer updatedPrayer = new Prayer();
        updatedPrayer.setPrayerId(prayerId);
        updatedPrayer.setPrayedForCount(11);
        updatedPrayer.setPrayedByEmails(Set.of(email));

        when(prayerRepository.findById(prayerId)).thenReturn(Optional.of(updatedPrayer));

        mockMvc.perform(post("/api/prayers/" + prayerId + "/prayed/auth")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", jwtToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prayedForCount").value(11))
                .andExpect(jsonPath("$.hasPrayed").value(true));
    }

    // 22. markPrayedAuthWithInvalidJwtReturns401
    @Test
    void markPrayedAuthWithInvalidJwtReturns401() throws Exception {
        String prayerId = "prayer123";
        String jwtToken = "invalid-jwt";

        when(jwtUtil.verifyToken(jwtToken)).thenThrow(new RuntimeException("Invalid token"));

        mockMvc.perform(post("/api/prayers/" + prayerId + "/prayed/auth")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", jwtToken)))
                .andExpect(status().isUnauthorized());
    }

    // 23. getGroupPrayersAuthWithValidJwtReturnsOpenPrayers
    @Test
    void getGroupPrayersAuthWithValidJwtReturnsOpenPrayers() throws Exception {
        String groupId = "group123";
        String jwtToken = "valid-jwt";
        String email = "portal-user@example.com";

        com.auth0.jwt.interfaces.DecodedJWT decodedJwt = mock(com.auth0.jwt.interfaces.DecodedJWT.class);
        com.auth0.jwt.interfaces.Claim emailClaim = mock(com.auth0.jwt.interfaces.Claim.class);
        when(emailClaim.asString()).thenReturn(email);
        when(decodedJwt.getClaim("email")).thenReturn(emailClaim);
        when(jwtUtil.verifyToken(jwtToken)).thenReturn(decodedJwt);

        GroupMemberDTO member = new GroupMemberDTO();
        member.setEmail(email);

        List<GroupMemberDTO> members = List.of(member);
        ResponseEntity<List<GroupMemberDTO>> responseEntity = new ResponseEntity<>(members, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://localhost:8083/api/groups/" + groupId + "/members"),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        Prayer prayer1 = new Prayer();
        prayer1.setPrayerId("p1");
        prayer1.setStatus("OPEN");
        prayer1.setGroupId(groupId);

        when(prayerRepository.findByGroupId(groupId)).thenReturn(List.of(prayer1));

        mockMvc.perform(get("/api/prayers/group/" + groupId + "/auth")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", jwtToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].prayerId").value("p1"));
    }

    // 24. getGroupPrayersAuthWithWrongGroupReturns403
    @Test
    void getGroupPrayersAuthWithWrongGroupReturns403() throws Exception {
        String groupId = "group123";
        String jwtToken = "valid-jwt";
        String email = "portal-user@example.com";

        com.auth0.jwt.interfaces.DecodedJWT decodedJwt = mock(com.auth0.jwt.interfaces.DecodedJWT.class);
        com.auth0.jwt.interfaces.Claim emailClaim = mock(com.auth0.jwt.interfaces.Claim.class);
        when(emailClaim.asString()).thenReturn(email);
        when(decodedJwt.getClaim("email")).thenReturn(emailClaim);
        when(jwtUtil.verifyToken(jwtToken)).thenReturn(decodedJwt);

        GroupMemberDTO member = new GroupMemberDTO();
        member.setEmail("other-user@example.com");

        List<GroupMemberDTO> members = List.of(member);
        ResponseEntity<List<GroupMemberDTO>> responseEntity = new ResponseEntity<>(members, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://localhost:8083/api/groups/" + groupId + "/members"),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        mockMvc.perform(get("/api/prayers/group/" + groupId + "/auth")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", jwtToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createPrayerRoundRobinThrowsException() throws Exception {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenThrow(new org.springframework.web.client.RestClientException("RR error"));

        PrayerDTO dto = new PrayerDTO();
        dto.setDeviceId("device123");
        dto.setPrayerText("Please pray.");

        mockMvc.perform(post("/api/prayers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());
    }

    @Test
    void publishEventBridgeEventFails() throws Exception {
        when(eventBridgeClient.putEvents(any(PutEventsRequest.class)))
                .thenThrow(new RuntimeException("EB error"));

        PrayerDTO dto = new PrayerDTO();
        dto.setDeviceId("device123");
        dto.setPrayerText("Please pray.");

        mockMvc.perform(post("/api/prayers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());
    }

    @Test
    void getGroupPrayersInvalidToken() throws Exception {
        mockMvc.perform(get("/api/prayers/group/g1?token=invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getGroupPrayersTokenPartsLengthMismatch() throws Exception {
        mockMvc.perform(get("/api/prayers/group/g1?token=sig|part2|part3|part4"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getGroupPrayersTokenGroupIdMismatch() throws Exception {
        mockMvc.perform(get("/api/prayers/group/g1?token=sig|otherGroup|1234567"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getGroupPrayersTokenInvalidExpiry() throws Exception {
        mockMvc.perform(get("/api/prayers/group/g1?token=sig|g1|invalidExpiry"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getGroupPrayersGroupServiceQueryFails() throws Exception {
        long expiry = Instant.now().getEpochSecond() + 3600;
        String token = "sig|g1|" + expiry;
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenThrow(new org.springframework.web.client.RestClientException("Fetch error"));

        mockMvc.perform(get("/api/prayers/group/g1?token=" + token))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getGroupPrayersMembersEmpty() throws Exception {
        long expiry = Instant.now().getEpochSecond() + 3600;
        String token = "sig|g1|" + expiry;
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(List.of(), HttpStatus.OK));

        mockMvc.perform(get("/api/prayers/group/g1?token=" + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getGroupPrayersSignatureMismatch() throws Exception {
        long expiry = Instant.now().getEpochSecond() + 3600;
        String token = "badsig|g1|" + expiry;
        GroupMemberDTO member = new GroupMemberDTO();
        member.setEmail("user@example.com");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(List.of(member), HttpStatus.OK));

        mockMvc.perform(get("/api/prayers/group/g1?token=" + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void markPrayedAuthNoCookie() throws Exception {
        mockMvc.perform(post("/api/prayers/p1/prayed/auth"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void markPrayedAuthInvalidEmail() throws Exception {
        com.auth0.jwt.interfaces.DecodedJWT decodedJwt = mock(com.auth0.jwt.interfaces.DecodedJWT.class);
        com.auth0.jwt.interfaces.Claim emailClaim = mock(com.auth0.jwt.interfaces.Claim.class);
        when(emailClaim.asString()).thenReturn("");
        when(decodedJwt.getClaim("email")).thenReturn(emailClaim);
        when(jwtUtil.verifyToken(anyString())).thenReturn(decodedJwt);

        mockMvc.perform(post("/api/prayers/p1/prayed/auth")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", "token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void markPrayedAuthTokenVerifyThrows() throws Exception {
        when(jwtUtil.verifyToken(anyString())).thenThrow(new RuntimeException("invalid token"));

        mockMvc.perform(post("/api/prayers/p1/prayed/auth")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", "token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void markPrayedAuthRecordPrayerThrowsConflict() throws Exception {
        com.auth0.jwt.interfaces.DecodedJWT decodedJwt = mock(com.auth0.jwt.interfaces.DecodedJWT.class);
        com.auth0.jwt.interfaces.Claim emailClaim = mock(com.auth0.jwt.interfaces.Claim.class);
        when(emailClaim.asString()).thenReturn("test@example.com");
        when(decodedJwt.getClaim("email")).thenReturn(emailClaim);
        when(jwtUtil.verifyToken(anyString())).thenReturn(decodedJwt);

        doThrow(software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException.builder().message("Conflict").build())
                .when(prayerRepository).recordPrayer(anyString(), anyString());

        mockMvc.perform(post("/api/prayers/p1/prayed/auth")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", "token")))
                .andExpect(status().isConflict());
    }

    @Test
    void getGroupPrayersAuthNoCookie() throws Exception {
        mockMvc.perform(get("/api/prayers/group/g1/auth"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getGroupPrayersAuthInvalidEmail() throws Exception {
        com.auth0.jwt.interfaces.DecodedJWT decodedJwt = mock(com.auth0.jwt.interfaces.DecodedJWT.class);
        com.auth0.jwt.interfaces.Claim emailClaim = mock(com.auth0.jwt.interfaces.Claim.class);
        when(emailClaim.asString()).thenReturn(null);
        when(decodedJwt.getClaim("email")).thenReturn(emailClaim);
        when(jwtUtil.verifyToken(anyString())).thenReturn(decodedJwt);

        mockMvc.perform(get("/api/prayers/group/g1/auth")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", "token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getGroupPrayersAuthVerifyThrows() throws Exception {
        when(jwtUtil.verifyToken(anyString())).thenThrow(new RuntimeException("invalid token"));

        mockMvc.perform(get("/api/prayers/group/g1/auth")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", "token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getGroupPrayersAuthServiceQueryThrows() throws Exception {
        com.auth0.jwt.interfaces.DecodedJWT decodedJwt = mock(com.auth0.jwt.interfaces.DecodedJWT.class);
        com.auth0.jwt.interfaces.Claim emailClaim = mock(com.auth0.jwt.interfaces.Claim.class);
        when(emailClaim.asString()).thenReturn("test@example.com");
        when(decodedJwt.getClaim("email")).thenReturn(emailClaim);
        when(jwtUtil.verifyToken(anyString())).thenReturn(decodedJwt);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenThrow(new org.springframework.web.client.RestClientException("members error"));

        mockMvc.perform(get("/api/prayers/group/g1/auth")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", "token")))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getGroupPrayersAuthMembersEmpty() throws Exception {
        com.auth0.jwt.interfaces.DecodedJWT decodedJwt = mock(com.auth0.jwt.interfaces.DecodedJWT.class);
        com.auth0.jwt.interfaces.Claim emailClaim = mock(com.auth0.jwt.interfaces.Claim.class);
        when(emailClaim.asString()).thenReturn("test@example.com");
        when(decodedJwt.getClaim("email")).thenReturn(emailClaim);
        when(jwtUtil.verifyToken(anyString())).thenReturn(decodedJwt);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(List.of(), HttpStatus.OK));

        mockMvc.perform(get("/api/prayers/group/g1/auth")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", "token")))
                .andExpect(status().isForbidden());
    }
}
