package com.prayerlink.prayer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.prayerlink.common.dto.PrayerDTO;
import com.prayerlink.common.enums.PrayerStatus;
import com.prayerlink.common.exception.ResourceNotFoundException;
import com.prayerlink.common.exception.UnauthorizedException;
import com.prayerlink.prayer.service.PrayerCommandService;
import com.prayerlink.prayer.service.PrayerQueryService;
import com.prayerlink.prayer.service.security.JwtUtil;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(PrayerController.class)
@AutoConfigureMockMvc(addFilters = false)
class PrayerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PrayerCommandService commandService;

    @MockitoBean
    private PrayerQueryService queryService;

    @MockitoBean
    private JwtUtil jwtUtil;

    private static final UUID P1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID P2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID DEV1 = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID G1 = UUID.fromString("00000000-0000-0000-0000-000000000005");

    // --- createPrayer ---

    @Test
    void createPrayerReturnsCreated() throws Exception {
        PrayerDTO requestDto = PrayerDTO.builder()
                .prayerText("Please pray for my upcoming job interview")
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
                .andExpect(jsonPath("$.prayerId").value(P1.toString()))
                .andExpect(jsonPath("$.prayerText").value("Please pray for my upcoming job interview"));

        verify(commandService).createPrayer(any(PrayerDTO.class));
    }

    @Test
    void createPrayerInvalidDtoReturnsBadRequest() throws Exception {
        PrayerDTO invalidDto = PrayerDTO.builder().deviceId(DEV1).build();

        mockMvc.perform(post("/api/prayers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest());

        verify(commandService, never()).createPrayer(any());
    }

    // --- getPrayer ---

    @Test
    void getPrayerReturnsOk() throws Exception {
        PrayerDTO responseDto = PrayerDTO.builder()
                .prayerId(P1)
                .deviceId(DEV1)
                .prayerText("Test prayer")
                .status(PrayerStatus.OPEN)
                .build();

        when(queryService.getPrayer(P1)).thenReturn(responseDto);

        mockMvc.perform(get("/api/prayers/{prayerId}", P1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prayerId").value(P1.toString()))
                .andExpect(jsonPath("$.prayerText").value("Test prayer"));
    }

    @Test
    void getPrayerNotFoundReturns404() throws Exception {
        when(queryService.getPrayer(P1)).thenThrow(new ResourceNotFoundException("Not found"));

        mockMvc.perform(get("/api/prayers/{prayerId}", P1)).andExpect(status().isNotFound());
    }

    // --- getPrayers ---

    @Test
    void getPrayersReturnsList() throws Exception {
        PrayerDTO dto1 = PrayerDTO.builder().prayerId(P1).prayerText("Prayer 1").build();
        PrayerDTO dto2 = PrayerDTO.builder().prayerId(P2).prayerText("Prayer 2").build();

        when(queryService.getPrayersByDevice(DEV1)).thenReturn(List.of(dto1, dto2));

        mockMvc.perform(get("/api/prayers").param("deviceId", DEV1.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].prayerId").value(P1.toString()))
                .andExpect(jsonPath("$[1].prayerId").value(P2.toString()));
    }

    // --- createUpdate ---

    @Test
    void createUpdateReturnsOk() throws Exception {
        Map<String, String> request = Map.of("updateText", "Answered!");

        mockMvc.perform(post("/api/prayers/{prayerId}/updates", P1)
                        .header("X-Device-ID", DEV1.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Prayer updated and closed successfully"));

        verify(commandService).createUpdate(P1, DEV1, "Answered!");
    }

    @Test
    void createUpdateUnauthorizedReturns401() throws Exception {
        Map<String, String> request = Map.of("updateText", "Answered!");

        doThrow(new UnauthorizedException("Not yours")).when(commandService).createUpdate(P1, DEV1, "Answered!");

        mockMvc.perform(post("/api/prayers/{prayerId}/updates", P1)
                        .header("X-Device-ID", DEV1.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // --- markPrayed ---

    @Test
    void markPrayedReturnsOk() throws Exception {
        Map<String, String> request = Map.of("intercessorToken", "valid.token");
        Map<String, Object> response = Map.of("message", "Thank you for praying", "prayedForCount", 1);

        when(commandService.markPrayed(P1, "valid.token")).thenReturn(response);

        mockMvc.perform(post("/api/prayers/{prayerId}/prayed", P1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Thank you for praying"))
                .andExpect(jsonPath("$.prayedForCount").value(1));
    }

    // --- getGroupPrayers ---

    @Test
    void getGroupPrayersReturnsOk() throws Exception {
        PrayerDTO dto = PrayerDTO.builder().prayerId(P1).prayerText("Prayer 1").build();

        when(queryService.getGroupPrayers(G1, "valid.token")).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/prayers/group/{groupId}", G1).param("token", "valid.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].prayerId").value(P1.toString()));
    }

    // --- markPrayedAuth ---

    @Test
    void markPrayedAuthReturnsOk() throws Exception {
        Map<String, Object> response = Map.of("message", "Thank you", "prayedForCount", 2);
        when(commandService.markPrayedAuth(P1, "u@t.com")).thenReturn(response);

        DecodedJWT decodedJWT = mock(DecodedJWT.class);
        Claim emailClaim = mock(Claim.class);
        when(emailClaim.asString()).thenReturn("u@t.com");
        when(decodedJWT.getClaim("email")).thenReturn(emailClaim);
        when(jwtUtil.verifyToken("jwt-token")).thenReturn(decodedJWT);

        mockMvc.perform(post("/api/prayers/{prayerId}/prayed/auth", P1)
                        .cookie(new Cookie("pl-auth-token", "jwt-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Thank you"))
                .andExpect(jsonPath("$.prayedForCount").value(2));
    }

    // --- getGroupPrayersAuth ---

    @Test
    void getGroupPrayersAuthReturnsOk() throws Exception {
        PrayerDTO dto = PrayerDTO.builder().prayerId(P1).build();
        when(queryService.getGroupPrayersAuth(G1, "u@t.com")).thenReturn(List.of(dto));

        DecodedJWT decodedJWT = mock(DecodedJWT.class);
        Claim emailClaim = mock(Claim.class);
        when(emailClaim.asString()).thenReturn("u@t.com");
        when(decodedJWT.getClaim("email")).thenReturn(emailClaim);
        when(jwtUtil.verifyToken("jwt-token")).thenReturn(decodedJWT);

        mockMvc.perform(get("/api/prayers/group/{groupId}/auth", G1).cookie(new Cookie("pl-auth-token", "jwt-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getGroupPrayersAuthForbiddenReturns403() throws Exception {
        when(queryService.getGroupPrayersAuth(G1, "other@t.com"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not belong to this group"));

        DecodedJWT decodedJWT = mock(DecodedJWT.class);
        Claim emailClaim = mock(Claim.class);
        when(emailClaim.asString()).thenReturn("other@t.com");
        when(decodedJWT.getClaim("email")).thenReturn(emailClaim);
        when(jwtUtil.verifyToken("jwt-token")).thenReturn(decodedJWT);

        mockMvc.perform(get("/api/prayers/group/{groupId}/auth", G1).cookie(new Cookie("pl-auth-token", "jwt-token")))
                .andExpect(status().isForbidden());
    }
}
