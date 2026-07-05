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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
public class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    private DecodedJWT mockJwt(String role, String username, String email, String name) {
        DecodedJWT jwt = mock(DecodedJWT.class);
        Claim roleClaim = mock(Claim.class);
        Claim userClaim = mock(Claim.class);
        Claim groupClaim = mock(Claim.class);
        Claim emailClaim = mock(Claim.class);
        Claim nameClaim = mock(Claim.class);

        when(roleClaim.asString()).thenReturn(role);
        when(userClaim.asString()).thenReturn(username);
        when(groupClaim.asString()).thenReturn(null);
        when(emailClaim.asString()).thenReturn(email);
        when(nameClaim.asString()).thenReturn(name);

        when(jwt.getClaim("role")).thenReturn(roleClaim);
        when(jwt.getClaim("username")).thenReturn(userClaim);
        when(jwt.getClaim("groupId")).thenReturn(groupClaim);
        when(jwt.getClaim("email")).thenReturn(emailClaim);
        when(jwt.getClaim("name")).thenReturn(nameClaim);
        when(jwt.getSubject()).thenReturn(username != null ? username : email);
        return jwt;
    }

    @Test
    void adminSetupAndLogin() throws Exception {
        // 1. Setup first admin
        when(adminRepository.isEmpty()).thenReturn(true);
        when(passwordEncoder.encode("supersecurepassword")).thenReturn("hashed_pass");
        when(jwtUtil.generateToken(any(), eq("adminuser"), eq("APP_ADMIN"), any())).thenReturn("setup-token");

        Map<String, String> setupBody = Map.of("username", "adminuser", "password", "supersecurepassword");
        mockMvc.perform(post("/api/admin/setup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(setupBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("APP_ADMIN"))
                .andExpect(cookie().exists("pl-auth-token"));

        // 2. Login as admin
        Admin admin = Admin.builder()
                .adminId("admin-uuid")
                .username("adminuser")
                .passwordHash("hashed_pass")
                .role("APP_ADMIN")
                .build();
        when(adminRepository.findByUsername("adminuser")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("supersecurepassword", "hashed_pass")).thenReturn(true);
        when(jwtUtil.generateToken("admin-uuid", "adminuser", "APP_ADMIN", null)).thenReturn("login-token");

        Map<String, String> loginBody = Map.of("username", "adminuser", "password", "supersecurepassword");
        mockMvc.perform(post("/api/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("APP_ADMIN"))
                .andExpect(cookie().value("pl-auth-token", "login-token"));

        // 3. Call protected endpoint with login cookie
        DecodedJWT decodedJwt = mockJwt("APP_ADMIN", "adminuser", null, null);
        when(jwtUtil.verifyToken("login-token")).thenReturn(decodedJwt);
        when(adminRepository.findAll()).thenReturn(List.of(admin));

        mockMvc.perform(get("/api/admin/admins")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", "login-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("adminuser"));

        // 4. Logout
        mockMvc.perform(post("/api/admin/logout"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("pl-auth-token", 0));
    }

    @Test
    void intercessorRegistrationAndLogin() throws Exception {
        // Mock intercessor account lookup & password verification
        IntercessorAccount intercessor = IntercessorAccount.builder()
                .email("intercessor@example.com")
                .passwordHash("intercessor_hash")
                .name("Intercessor Jane")
                .build();

        when(adminRepository.findByUsername("intercessor@example.com")).thenReturn(Optional.empty());
        when(intercessorAccountRepository.findById("intercessor@example.com")).thenReturn(Optional.of(intercessor));
        when(passwordEncoder.matches("intercessorpass", "intercessor_hash")).thenReturn(true);
        when(jwtUtil.generateTokenForIntercessor("intercessor@example.com", "Intercessor Jane")).thenReturn("intercessor-token");

        // Login intercessor via unified endpoint
        Map<String, String> body = Map.of("identifier", "intercessor@example.com", "password", "intercessorpass");
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("INTERCESSOR"))
                .andExpect(jsonPath("$.email").value("intercessor@example.com"))
                .andExpect(cookie().value("pl-auth-token", "intercessor-token"));

        // Call status to verify intercessor status details
        DecodedJWT decodedJwt = mockJwt("INTERCESSOR", null, "intercessor@example.com", "Intercessor Jane");
        when(jwtUtil.verifyToken("intercessor-token")).thenReturn(decodedJwt);

        GroupMemberDTO memberDto = new GroupMemberDTO();
        memberDto.setGroupId("group-a");
        when(restTemplate.exchange(
                contains("/api/groups/members/search?email=intercessor@example.com"),
                eq(HttpMethod.GET),
                any(),
                any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(List.of(memberDto), HttpStatus.OK));

        GroupDTO groupDto = new GroupDTO();
        groupDto.setGroupId("group-a");
        groupDto.setName("Group A");
        when(restTemplate.getForObject(
                contains("/api/groups/group-a"),
                eq(GroupDTO.class)))
                .thenReturn(groupDto);

        mockMvc.perform(get("/api/auth/status")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", "intercessor-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.role").value("INTERCESSOR"))
                .andExpect(jsonPath("$.groups[0].groupId").value("group-a"))
                .andExpect(jsonPath("$.groups[0].name").value("Group A"));
    }

    @Test
    void unifiedLoginAdmin() throws Exception {
        // Login as APP_ADMIN using unified login endpoint `/api/auth/login`
        Admin admin = Admin.builder()
                .adminId("admin-uuid")
                .username("adminuser")
                .passwordHash("hashed_pass")
                .role("APP_ADMIN")
                .build();
        when(adminRepository.findByUsername("adminuser")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("password123", "hashed_pass")).thenReturn(true);
        when(jwtUtil.generateToken("admin-uuid", "adminuser", "APP_ADMIN", null)).thenReturn("admin-token");

        Map<String, String> body = Map.of("identifier", "adminuser", "password", "password123");
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("APP_ADMIN"))
                .andExpect(cookie().value("pl-auth-token", "admin-token"));
    }

    @Test
    void unifiedLoginIntercessor() throws Exception {
        // Login as INTERCESSOR using unified login endpoint `/api/auth/login`
        IntercessorAccount account = IntercessorAccount.builder()
                .email("intercessor@example.com")
                .passwordHash("hashed_pass")
                .name("Jane Doe")
                .build();
        when(adminRepository.findByUsername("intercessor@example.com")).thenReturn(Optional.empty());
        when(intercessorAccountRepository.findById("intercessor@example.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("password123", "hashed_pass")).thenReturn(true);
        when(jwtUtil.generateTokenForIntercessor("intercessor@example.com", "Jane Doe")).thenReturn("intercessor-token");

        Map<String, String> body = Map.of("identifier", "intercessor@example.com", "password", "password123");
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("INTERCESSOR"))
                .andExpect(cookie().value("pl-auth-token", "intercessor-token"));
    }
}
