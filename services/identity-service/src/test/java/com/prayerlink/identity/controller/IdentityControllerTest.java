package com.prayerlink.identity.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import tools.jackson.databind.ObjectMapper;
import com.prayerlink.common.dto.GroupDTO;
import com.prayerlink.common.dto.GroupMemberDTO;
import com.prayerlink.identity.model.Device;
import com.prayerlink.identity.model.IntercessorAccount;
import com.prayerlink.identity.repository.DeviceRepository;
import com.prayerlink.identity.repository.IntercessorAccountRepository;
import com.prayerlink.identity.util.JwtUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

@WebMvcTest(IdentityController.class)
@TestPropertySource(properties = {
    "services.group-service.url=http://localhost:8083",
    "cookie.secure=false"
})
public class IdentityControllerTest {

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
    void registerNewDeviceReturnsDeviceAndSetsCookie() throws Exception {
        String deviceId = UUID.randomUUID().toString();
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("deviceId", deviceId);

        when(deviceRepository.findById(deviceId)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/identity/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.deviceId").value(deviceId));
    }

    @Test
    void registerExistingDeviceReturns200() throws Exception {
        String deviceId = UUID.randomUUID().toString();
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("deviceId", deviceId);

        Device mockDevice = new Device();
        mockDevice.setDeviceId(deviceId);

        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(mockDevice));

        mockMvc.perform(post("/api/identity/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.deviceId").value(deviceId));
    }

    @Test
    void getMeWithNoCookieReturns401() throws Exception {
        mockMvc.perform(get("/api/identity/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMeWithValidCookieReturnsDevice() throws Exception {
        String deviceId = UUID.randomUUID().toString();
        Device mockDevice = new Device();
        mockDevice.setDeviceId(deviceId);

        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(mockDevice));

        mockMvc.perform(get("/api/identity/me")
                .cookie(new jakarta.servlet.http.Cookie("pl-device-id", deviceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value(deviceId));
    }

    @Test
    void updateSeenWithValidDeviceUpdatesTimestamp() throws Exception {
        String deviceId = UUID.randomUUID().toString();
        Device mockDevice = new Device();
        mockDevice.setDeviceId(deviceId);

        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(mockDevice));

        mockMvc.perform(put("/api/identity/" + deviceId + "/seen"))
                .andExpect(status().isNoContent());
    }

    @Test
    void updateSeenWithUnknownDeviceReturns404() throws Exception {
        String deviceId = UUID.randomUUID().toString();
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/identity/" + deviceId + "/seen"))
                .andExpect(status().isNotFound());
    }

    @Test
    void registerIntercessorAsValidGroupMemberCreatesAccount() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("email", "member@example.com");
        body.put("name", "Member Name");
        body.put("password", "securepassword");
        body.put("inviteCode", "INVITE");

        GroupDTO group = GroupDTO.builder()
                .groupId("group123")
                .name("Group Name")
                .passcode("INVITE")
                .build();

        GroupMemberDTO member = GroupMemberDTO.builder()
                .groupId("group123")
                .memberId("member123")
                .email("member@example.com")
                .build();

        when(restTemplate.getForEntity(anyString(), eq(GroupDTO.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>(group, HttpStatus.OK));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>(List.of(member), HttpStatus.OK));
        when(intercessorAccountRepository.findById("member@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("securepassword")).thenReturn("hashed_password");
        when(jwtUtil.generateToken("member@example.com", "Member Name")).thenReturn("mock_jwt");

        mockMvc.perform(post("/api/identity/intercessor/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.email").value("member@example.com"));
    }

    @Test
    void registerIntercessorWhenNotGroupMemberReturns403() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("email", "notmember@example.com");
        body.put("name", "Not Member");
        body.put("password", "securepassword");
        body.put("inviteCode", "INVITE");

        GroupDTO group = GroupDTO.builder()
                .groupId("group123")
                .name("Group Name")
                .passcode("INVITE")
                .build();

        when(restTemplate.getForEntity(anyString(), eq(GroupDTO.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>(group, HttpStatus.OK));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>(List.of(), HttpStatus.OK));

        mockMvc.perform(post("/api/identity/intercessor/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void registerIntercessorWithDuplicateEmailReturns409() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("email", "duplicate@example.com");
        body.put("name", "Duplicate Name");
        body.put("password", "securepassword");
        body.put("inviteCode", "INVITE");

        GroupDTO group = GroupDTO.builder()
                .groupId("group123")
                .name("Group Name")
                .passcode("INVITE")
                .build();

        GroupMemberDTO member = GroupMemberDTO.builder()
                .groupId("group123")
                .memberId("member123")
                .email("duplicate@example.com")
                .build();

        when(restTemplate.getForEntity(anyString(), eq(GroupDTO.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>(group, HttpStatus.OK));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>(List.of(member), HttpStatus.OK));

        IntercessorAccount existingAccount = new IntercessorAccount();
        existingAccount.setEmail("duplicate@example.com");

        when(intercessorAccountRepository.findById("duplicate@example.com")).thenReturn(Optional.of(existingAccount));

        mockMvc.perform(post("/api/identity/intercessor/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    @Test
    void loginIntercessorWithValidCredentialsSetsJwt() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("email", "login@example.com");
        body.put("password", "password123");

        IntercessorAccount account = IntercessorAccount.builder()
                .email("login@example.com")
                .name("Login User")
                .passwordHash("hashed_password")
                .build();

        when(intercessorAccountRepository.findById("login@example.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("password123", "hashed_password")).thenReturn(true);
        when(jwtUtil.generateToken("login@example.com", "Login User")).thenReturn("mock_jwt");

        mockMvc.perform(post("/api/identity/intercessor/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.email").value("login@example.com"));
    }

    @Test
    void loginIntercessorWithWrongPasswordReturns401() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("email", "login@example.com");
        body.put("password", "wrongpassword");

        IntercessorAccount account = IntercessorAccount.builder()
                .email("login@example.com")
                .name("Login User")
                .passwordHash("hashed_password")
                .build();

        when(intercessorAccountRepository.findById("login@example.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrongpassword", "hashed_password")).thenReturn(false);

        mockMvc.perform(post("/api/identity/intercessor/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginIntercessorWithUnknownEmailReturns401() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("email", "unknown@example.com");
        body.put("password", "password123");

        when(intercessorAccountRepository.findById("unknown@example.com")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/identity/intercessor/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getIntercessorMeWithValidJwtReturnsProfile() throws Exception {
        String token = "valid_jwt";
        com.auth0.jwt.interfaces.DecodedJWT decoded = mock(com.auth0.jwt.interfaces.DecodedJWT.class);
        com.auth0.jwt.interfaces.Claim emailClaim = mock(com.auth0.jwt.interfaces.Claim.class);
        com.auth0.jwt.interfaces.Claim nameClaim = mock(com.auth0.jwt.interfaces.Claim.class);

        when(decoded.getClaim("email")).thenReturn(emailClaim);
        when(decoded.getClaim("name")).thenReturn(nameClaim);
        when(emailClaim.asString()).thenReturn("me@example.com");
        when(nameClaim.asString()).thenReturn("Me");

        when(jwtUtil.verifyToken(token)).thenReturn(decoded);

        GroupMemberDTO member = GroupMemberDTO.builder()
                .groupId("group123")
                .memberId("member123")
                .email("me@example.com")
                .build();

        GroupDTO group = GroupDTO.builder()
                .groupId("group123")
                .name("My Group")
                .build();

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>(List.of(member), HttpStatus.OK));
        when(restTemplate.getForObject(anyString(), eq(GroupDTO.class))).thenReturn(group);

        mockMvc.perform(get("/api/identity/intercessor/me")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.groups[0].groupId").value("group123"))
                .andExpect(jsonPath("$.groups[0].name").value("My Group"));
    }

    @Test
    void logoutIntercessorClearsCookie() throws Exception {
        mockMvc.perform(post("/api/identity/intercessor/logout"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"));
    }

    @Test
    void registerIntercessorInviteCodeFetchException() throws Exception {
        Map<String, String> body = Map.of(
                "email", "test@example.com",
                "name", "Test",
                "password", "pass",
                "inviteCode", "CODE123"
        );

        when(restTemplate.getForEntity(anyString(), eq(GroupDTO.class)))
                .thenThrow(new org.springframework.web.client.RestClientException("group error"));

        mockMvc.perform(post("/api/identity/intercessor/register")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid invite code"));
    }

    @Test
    void registerIntercessorMemberFetchException() throws Exception {
        Map<String, String> body = Map.of(
                "email", "test@example.com",
                "name", "Test",
                "password", "pass",
                "inviteCode", "CODE123"
        );

        GroupDTO group = GroupDTO.builder()
                .groupId("group123")
                .name("Group")
                .build();

        when(restTemplate.getForEntity(anyString(), eq(GroupDTO.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>(group, HttpStatus.OK));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenThrow(new org.springframework.web.client.RestClientException("member error"));

        mockMvc.perform(post("/api/identity/intercessor/register")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Email is not pre-authorized for this invite code"));
    }

    @Test
    void getIntercessorMeGroupsFetchException() throws Exception {
        String token = "valid-token";
        com.auth0.jwt.interfaces.DecodedJWT decoded = mock(com.auth0.jwt.interfaces.DecodedJWT.class);
        com.auth0.jwt.interfaces.Claim emailClaim = mock(com.auth0.jwt.interfaces.Claim.class);
        com.auth0.jwt.interfaces.Claim nameClaim = mock(com.auth0.jwt.interfaces.Claim.class);
        when(decoded.getClaim("email")).thenReturn(emailClaim);
        when(decoded.getClaim("name")).thenReturn(nameClaim);
        when(emailClaim.asString()).thenReturn("me@example.com");
        when(nameClaim.asString()).thenReturn("Me");

        when(jwtUtil.verifyToken(token)).thenReturn(decoded);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenThrow(new org.springframework.web.client.RestClientException("search error"));

        mockMvc.perform(get("/api/identity/intercessor/me")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups").isEmpty());
    }

    @Test
    void getIntercessorMeGroupDetailsFetchException() throws Exception {
        String token = "valid-token";
        com.auth0.jwt.interfaces.DecodedJWT decoded = mock(com.auth0.jwt.interfaces.DecodedJWT.class);
        com.auth0.jwt.interfaces.Claim emailClaim = mock(com.auth0.jwt.interfaces.Claim.class);
        com.auth0.jwt.interfaces.Claim nameClaim = mock(com.auth0.jwt.interfaces.Claim.class);
        when(decoded.getClaim("email")).thenReturn(emailClaim);
        when(decoded.getClaim("name")).thenReturn(nameClaim);
        when(emailClaim.asString()).thenReturn("me@example.com");
        when(nameClaim.asString()).thenReturn("Me");

        when(jwtUtil.verifyToken(token)).thenReturn(decoded);

        GroupMemberDTO member = GroupMemberDTO.builder()
                .groupId("group123")
                .memberId("member123")
                .email("me@example.com")
                .build();

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>(List.of(member), HttpStatus.OK));
        when(restTemplate.getForObject(anyString(), eq(GroupDTO.class)))
                .thenThrow(new org.springframework.web.client.RestClientException("details error"));

        mockMvc.perform(get("/api/identity/intercessor/me")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups").isEmpty());
    }

    @Test
    void getIntercessorMeInvalidToken() throws Exception {
        String token = "invalid-token";
        when(jwtUtil.verifyToken(token)).thenThrow(new RuntimeException("invalid token"));

        mockMvc.perform(get("/api/identity/intercessor/me")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid or expired token"));
    }
}
