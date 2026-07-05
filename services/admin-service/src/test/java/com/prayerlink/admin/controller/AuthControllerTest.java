package com.prayerlink.admin.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import tools.jackson.databind.ObjectMapper;
import com.prayerlink.admin.model.Admin;
import com.prayerlink.admin.model.IntercessorAccount;
import com.prayerlink.admin.repository.AdminRepository;
import com.prayerlink.admin.repository.IntercessorAccountRepository;
import com.prayerlink.admin.util.JwtUtil;
import com.prayerlink.common.dto.GroupDTO;
import com.prayerlink.common.dto.GroupMemberDTO;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

@WebMvcTest(AuthController.class)
@TestPropertySource(properties = {
    "services.group-service.url=http://localhost:8083",
    "cookie.secure=false"
})
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminRepository adminRepository;

    @MockitoBean
    private IntercessorAccountRepository intercessorAccountRepository;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private DecodedJWT mockJwt(String role, String username, String groupId, String email, String name) {
        DecodedJWT jwt = mock(DecodedJWT.class);
        Claim roleClaim = mock(Claim.class);
        Claim userClaim = mock(Claim.class);
        Claim groupClaim = mock(Claim.class);
        Claim emailClaim = mock(Claim.class);
        Claim nameClaim = mock(Claim.class);

        when(roleClaim.asString()).thenReturn(role);
        when(userClaim.asString()).thenReturn(username);
        when(groupClaim.asString()).thenReturn(groupId);
        when(emailClaim.asString()).thenReturn(email);
        when(nameClaim.asString()).thenReturn(name);

        when(jwt.getClaim("role")).thenReturn(roleClaim);
        when(jwt.getClaim("username")).thenReturn(userClaim);
        when(jwt.getClaim("groupId")).thenReturn(groupClaim);
        when(jwt.getClaim("email")).thenReturn(emailClaim);
        when(jwt.getClaim("name")).thenReturn(nameClaim);
        return jwt;
    }

    // === STATUS TESTS ===

    @Test
    void statusWithNoTokenReturnsUnauthenticated() throws Exception {
        when(adminRepository.isEmpty()).thenReturn(false);

        mockMvc.perform(get("/api/auth/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.initialized").value(true));
    }

    @Test
    void statusWithValidAdminJwtReturnsAdminRole() throws Exception {
        String token = "valid-admin-token";
        DecodedJWT mockJwt = mockJwt("APP_ADMIN", "adminuser", "group123", null, null);
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);
        when(adminRepository.isEmpty()).thenReturn(false);

        mockMvc.perform(get("/api/auth/status")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.role").value("APP_ADMIN"))
                .andExpect(jsonPath("$.username").value("adminuser"));
    }

    @Test
    void statusWithValidIntercessorJwtReturnsIntercessorRole() throws Exception {
        String token = "valid-intercessor-token";
        DecodedJWT mockJwt = mockJwt("INTERCESSOR", null, null, "intercessor@example.com", "John Doe");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);
        when(adminRepository.isEmpty()).thenReturn(false);

        // Mock RestTemplate query for intercessor group membership search
        GroupMemberDTO memberDto = new GroupMemberDTO();
        memberDto.setGroupId("group123");
        when(restTemplate.exchange(
                contains("/api/groups/members/search?email=intercessor@example.com"),
                eq(HttpMethod.GET),
                any(),
                any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(List.of(memberDto), HttpStatus.OK));

        // Mock RestTemplate query for group details
        GroupDTO groupDto = new GroupDTO();
        groupDto.setGroupId("group123");
        groupDto.setName("Test Group");
        when(restTemplate.getForObject(
                contains("/api/groups/group123"),
                eq(GroupDTO.class)))
                .thenReturn(groupDto);

        mockMvc.perform(get("/api/auth/status")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.role").value("INTERCESSOR"))
                .andExpect(jsonPath("$.email").value("intercessor@example.com"))
                .andExpect(jsonPath("$.name").value("John Doe"))
                .andExpect(jsonPath("$.groups[0].groupId").value("group123"))
                .andExpect(jsonPath("$.groups[0].name").value("Test Group"));
    }

    @Test
    void statusWithExpiredJwtReturnsUnauthenticated() throws Exception {
        String token = "expired-token";
        when(jwtUtil.verifyToken(token)).thenThrow(new RuntimeException("Expired token"));
        when(adminRepository.isEmpty()).thenReturn(false);

        mockMvc.perform(get("/api/auth/status")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    // === LOGIN TESTS ===

    @Test
    void loginWithAdminCredentialsSetsJwtWithAdminRole() throws Exception {
        Admin admin = Admin.builder()
                .adminId("admin1")
                .username("adminuser")
                .passwordHash("hashed_password")
                .role("APP_ADMIN")
                .groupId(null)
                .build();

        when(adminRepository.findByUsername("adminuser")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("password123", "hashed_password")).thenReturn(true);
        when(jwtUtil.generateToken("admin1", "adminuser", "APP_ADMIN", null)).thenReturn("admin-token");

        Map<String, String> body = Map.of("identifier", "adminuser", "password", "password123");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("APP_ADMIN"))
                .andExpect(jsonPath("$.username").value("adminuser"))
                .andExpect(cookie().value("pl-auth-token", "admin-token"));
    }

    @Test
    void loginWithIntercessorCredentialsSetsJwtWithIntercessorRole() throws Exception {
        IntercessorAccount account = IntercessorAccount.builder()
                .email("intercessor@example.com")
                .passwordHash("hashed_password")
                .name("John Doe")
                .build();

        when(adminRepository.findByUsername("intercessor@example.com")).thenReturn(Optional.empty());
        when(intercessorAccountRepository.findById("intercessor@example.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("password123", "hashed_password")).thenReturn(true);
        when(jwtUtil.generateTokenForIntercessor("intercessor@example.com", "John Doe")).thenReturn("intercessor-token");

        Map<String, String> body = Map.of("identifier", "intercessor@example.com", "password", "password123");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("INTERCESSOR"))
                .andExpect(jsonPath("$.email").value("intercessor@example.com"))
                .andExpect(cookie().value("pl-auth-token", "intercessor-token"));
    }

    @Test
    void loginWithInvalidCredentialsReturns401() throws Exception {
        when(adminRepository.findByUsername("user")).thenReturn(Optional.empty());
        when(intercessorAccountRepository.findById("user")).thenReturn(Optional.empty());

        Map<String, String> body = Map.of("identifier", "user", "password", "wrongpass");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid username/email or password."));
    }

    // === LOGOUT TESTS ===

    @Test
    void logoutClearsCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"))
                .andExpect(cookie().maxAge("pl-auth-token", 0));
    }
}
