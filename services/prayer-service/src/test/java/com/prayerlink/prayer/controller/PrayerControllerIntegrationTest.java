package com.prayerlink.prayer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prayerlink.common.dto.PrayerDTO;
import com.prayerlink.common.enums.PrayerStatus;
import com.prayerlink.prayer.repository.PrayerRepository;
import com.prayerlink.prayer.repository.PrayerUpdateRepository;
import com.prayerlink.prayer.service.PrayerCommandService;
import com.prayerlink.prayer.service.PrayerQueryService;
import com.prayerlink.prayer.service.security.JwtUtil;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
public class PrayerControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PrayerCommandService commandService;

    @MockitoBean
    private PrayerQueryService queryService;

    // Infrastructure beans mocked to avoid AWS/DynamoDB connection requirements
    @MockitoBean
    private PrayerRepository prayerRepository;

    @MockitoBean
    private PrayerUpdateRepository prayerUpdateRepository;

    @MockitoBean
    private RestTemplate restTemplateBean;

    @MockitoBean
    private EventBridgeClient eventBridgeClient;

    @MockitoBean
    private JwtUtil jwtUtil;

    private static final UUID P1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DEV1 = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void createPrayerFlow() throws Exception {
        PrayerDTO requestDto = PrayerDTO.builder()
                .prayerText("Test integration prayer")
                .deviceId(DEV1)
                .build();
        PrayerDTO responseDto = PrayerDTO.builder()
                .prayerId(P1)
                .status(PrayerStatus.OPEN)
                .deviceId(DEV1)
                .prayerText(requestDto.prayerText())
                .build();

        when(commandService.createPrayer(any(PrayerDTO.class))).thenReturn(responseDto);

        mockMvc.perform(post("/api/prayers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.prayerId").value(P1.toString()));
    }

    @Test
    void getPrayerFlow() throws Exception {
        PrayerDTO responseDto = PrayerDTO.builder()
                .prayerId(P1)
                .deviceId(DEV1)
                .status(PrayerStatus.OPEN)
                .build();

        when(queryService.getPrayer(P1)).thenReturn(responseDto);

        mockMvc.perform(get("/api/prayers/{prayerId}", P1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prayerId").value(P1.toString()));
    }

    @Test
    void getPrayersByDeviceFlow() throws Exception {
        PrayerDTO dto = PrayerDTO.builder().prayerId(P1).build();

        when(queryService.getPrayersByDevice(DEV1)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/prayers").param("deviceId", DEV1.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
