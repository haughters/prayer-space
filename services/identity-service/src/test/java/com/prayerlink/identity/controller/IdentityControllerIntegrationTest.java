package com.prayerlink.identity.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.prayerlink.identity.model.Device;
import com.prayerlink.identity.model.IntercessorAccount;
import com.prayerlink.identity.repository.DeviceRepository;
import com.prayerlink.identity.repository.IntercessorAccountRepository;
import com.prayerlink.identity.util.JwtUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
public class IdentityControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DeviceRepository deviceRepository;

    @MockitoBean
    private IntercessorAccountRepository intercessorAccountRepository;

    @MockitoBean
    private RestTemplate restTemplate;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private JwtUtil jwtUtil;

    @Test
    void testDeviceRegistrationIntegration() throws Exception {
        String deviceId = UUID.randomUUID().toString();
        Map<String, String> body = new HashMap<>();
        body.put("deviceId", deviceId);

        when(deviceRepository.findById(UUID.fromString(deviceId))).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/identity/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.deviceId").value(deviceId));

        verify(deviceRepository, times(1)).save(any(Device.class));
    }

    @Test
    void testDeviceSeenIntegration() throws Exception {
        String deviceId = UUID.randomUUID().toString();
        Device mockDevice = new Device();
        mockDevice.setDeviceId(UUID.fromString(deviceId));

        when(deviceRepository.findById(UUID.fromString(deviceId))).thenReturn(Optional.of(mockDevice));

        mockMvc.perform(put("/api/identity/" + deviceId + "/seen")).andExpect(status().isNoContent());

        verify(deviceRepository, times(1)).save(any(Device.class));
    }

    @Test
    void testIntercessorLoginIntegration() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("email", "integration@example.com");
        body.put("password", "password123");

        IntercessorAccount account = IntercessorAccount.builder()
                .email("integration@example.com")
                .name("Integration User")
                .passwordHash("hashed_password")
                .build();

        when(intercessorAccountRepository.findByEmail("integration@example.com"))
                .thenReturn(Optional.of(account));
        when(passwordEncoder.matches("password123", "hashed_password")).thenReturn(true);
        when(jwtUtil.generateToken("integration@example.com", "Integration User"))
                .thenReturn("mock_jwt");

        mockMvc.perform(post("/api/identity/intercessor/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.email").value("integration@example.com"));
    }
}
