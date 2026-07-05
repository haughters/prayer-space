package com.prayerlink.prayer.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import tools.jackson.databind.ObjectMapper;
import com.prayerlink.common.dto.GroupDTO;
import com.prayerlink.common.dto.GroupMemberDTO;
import com.prayerlink.common.dto.PrayerDTO;
import com.prayerlink.prayer.model.Prayer;
import com.prayerlink.prayer.model.PrayerUpdate;
import com.prayerlink.prayer.repository.PrayerRepository;
import com.prayerlink.prayer.repository.PrayerUpdateRepository;
import com.prayerlink.prayer.util.JwtUtil;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
public class PrayerControllerIntegrationTest {

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

    @BeforeEach
    void setUp() {
        PutEventsResponse mockResponse = PutEventsResponse.builder().failedEntryCount(0).build();
        when(eventBridgeClient.putEvents(any(PutEventsRequest.class))).thenReturn(mockResponse);
    }

    @Test
    void fullPrayerLifecycle() throws Exception {
        // 1. Submit a prayer
        PrayerDTO inputDto = PrayerDTO.builder()
                .prayerText("Please pray for our community outreach event this weekend.")
                .deviceId("device-lifecycle")
                .groupId("group-lifecycle")
                .build();

        GroupDTO mockGroup = new GroupDTO();
        mockGroup.setGroupId("group-lifecycle");
        mockGroup.setName("Outreach Group");

        when(restTemplate.getForEntity(contains("/api/groups/group-lifecycle"), eq(GroupDTO.class)))
                .thenReturn(new ResponseEntity<>(mockGroup, HttpStatus.OK));

        mockMvc.perform(post("/api/prayers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(inputDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.assignedGroupId").value("group-lifecycle"));

        // 2. Fetch the prayer (mock repository lookup)
        Prayer mockPrayer = Prayer.builder()
                .prayerId("prayer-123")
                .deviceId("device-lifecycle")
                .prayerText("Please pray for our community outreach event this weekend.")
                .groupId("group-lifecycle")
                .assignedGroupId("group-lifecycle")
                .status("OPEN")
                .prayedForCount(0)
                .createdAt(Instant.now())
                .build();
        when(prayerRepository.findById("prayer-123")).thenReturn(Optional.of(mockPrayer));
        when(prayerUpdateRepository.findByPrayerId("prayer-123")).thenReturn(List.of());

        mockMvc.perform(get("/api/prayers/prayer-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.prayerText").value("Please pray for our community outreach event this weekend."));

        // 3. Update / close the prayer
        Map<String, String> updateBody = Map.of("updateText", "The event went great! Thanks for all prayers.");
        mockMvc.perform(post("/api/prayers/prayer-123/updates")
                .header("X-Device-ID", "device-lifecycle")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Prayer updated and closed successfully"));

        verify(prayerRepository, times(2)).save(any(Prayer.class));
        verify(prayerUpdateRepository, times(1)).save(any(PrayerUpdate.class));
    }

    @Test
    void prayerRoundRobin() throws Exception {
        // Mocks three active groups in group-service
        GroupDTO group1 = new GroupDTO();
        group1.setGroupId("g1");
        group1.setName("Group One");
        group1.setOptOutGeneral(false);

        GroupDTO group2 = new GroupDTO();
        group2.setGroupId("g2");
        group2.setName("Group Two");
        group2.setOptOutGeneral(false);

        GroupDTO group3 = new GroupDTO();
        group3.setGroupId("g3");
        group3.setName("Group Three");
        group3.setOptOutGeneral(false);

        when(restTemplate.exchange(
                contains("/api/groups"),
                eq(HttpMethod.GET),
                any(),
                any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(List.of(group1, group2, group3), HttpStatus.OK));

        // Submit three prayers and verify they get assigned via round-robin
        PrayerDTO requestDto = PrayerDTO.builder()
                .prayerText("General prayer request for round-robin assignment.")
                .deviceId("device-rr")
                .build();

        // Submit 1st prayer
        mockMvc.perform(post("/api/prayers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignedGroupId").exists());

        // Submit 2nd prayer
        mockMvc.perform(post("/api/prayers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignedGroupId").exists());

        // Submit 3rd prayer
        mockMvc.perform(post("/api/prayers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignedGroupId").exists());

        verify(prayerRepository, times(3)).save(any(Prayer.class));
    }

    @Test
    void intercessorMarkPrayed() throws Exception {
        // Create a prayer
        Prayer prayer = Prayer.builder()
                .prayerId("prayer-abc")
                .assignedGroupId("group-xyz")
                .status("OPEN")
                .prayedForCount(0)
                .build();
        when(prayerRepository.findById("prayer-abc")).thenReturn(Optional.of(prayer));

        // Group members list
        GroupMemberDTO member = new GroupMemberDTO();
        member.setEmail("intercessor@example.com");
        when(restTemplate.exchange(
                contains("/api/groups/group-xyz/members"),
                eq(HttpMethod.GET),
                any(),
                any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(List.of(member), HttpStatus.OK));

        // Generate HMAC signature for legacy 2-part token
        String hmacSecretKey = "default-secret-key-change-me-in-production";
        long expiry = Instant.now().getEpochSecond() + 3600;
        String payload = "prayer-abc:intercessor@example.com:" + expiry;
        String signature = computeHmac(payload, hmacSecretKey);
        String token = signature + "|" + expiry;

        Map<String, String> body = Map.of("intercessorToken", token);

        mockMvc.perform(post("/api/prayers/prayer-abc/prayed")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Thank you for praying"));

        verify(prayerRepository, times(1)).recordPrayer("prayer-abc", "intercessor@example.com");
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
}
