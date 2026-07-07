package com.prayerlink.admin.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import tools.jackson.databind.ObjectMapper;
import com.prayerlink.admin.model.Admin;
import com.prayerlink.admin.model.Prayer;
import com.prayerlink.admin.repository.AdminRepository;
import com.prayerlink.admin.repository.PrayerRepository;
import com.prayerlink.admin.util.JwtUtil;
import com.prayerlink.common.dto.GroupDTO;
import com.prayerlink.common.dto.GroupMemberDTO;
import java.time.Instant;
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

@WebMvcTest(AdminController.class)
@TestPropertySource(properties = {
    "services.group-service.url=http://localhost:8083",
    "services.prayer-service.url=http://localhost:8082",
    "cookie.secure=false"
})
public class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminRepository adminRepository;

    @MockitoBean
    private PrayerRepository prayerRepository;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private DecodedJWT mockJwt(String sub, String role, String groupId, String username) {
        DecodedJWT jwt = mock(DecodedJWT.class);
        Claim roleClaim = mock(Claim.class);
        Claim groupClaim = mock(Claim.class);
        Claim userClaim = mock(Claim.class);
        
        when(roleClaim.asString()).thenReturn(role);
        when(groupClaim.asString()).thenReturn(groupId);
        when(userClaim.asString()).thenReturn(username);
        
        when(jwt.getClaim("role")).thenReturn(roleClaim);
        when(jwt.getClaim("groupId")).thenReturn(groupClaim);
        when(jwt.getClaim("username")).thenReturn(userClaim);
        when(jwt.getSubject()).thenReturn(sub);
        return jwt;
    }

    // === AUTHENTICATION / STATUS TESTS ===

    @Test
    void statusWithNoTokenReturnsUnauthenticated() throws Exception {
        when(adminRepository.isEmpty()).thenReturn(false);

        mockMvc.perform(get("/api/admin/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.initialized").value(true));
    }

    @Test
    void statusWithValidAdminJwtReturnsAdminRole() throws Exception {
        String token = "valid-admin-token";
        DecodedJWT mockJwt = mockJwt("admin1", "APP_ADMIN", null, "adminuser");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);
        when(adminRepository.isEmpty()).thenReturn(false);

        mockMvc.perform(get("/api/admin/status")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.role").value("APP_ADMIN"))
                .andExpect(jsonPath("$.username").value("adminuser"));
    }

    @Test
    void statusWithExpiredJwtReturnsUnauthenticated() throws Exception {
        String token = "expired-token";
        when(jwtUtil.verifyToken(token)).thenThrow(new RuntimeException("Expired token"));
        when(adminRepository.isEmpty()).thenReturn(false);

        mockMvc.perform(get("/api/admin/status")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void setupFirstAdminCreatesAccount() throws Exception {
        when(adminRepository.isEmpty()).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_password");
        when(jwtUtil.generateToken(anyString(), eq("adminuser"), eq("APP_ADMIN"), any())).thenReturn("generated_token");

        Map<String, String> body = new HashMap<>();
        body.put("username", "adminuser");
        body.put("password", "strongpassword123");

        mockMvc.perform(post("/api/admin/setup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("APP_ADMIN"))
                .andExpect(cookie().exists("pl-auth-token"));

        verify(adminRepository, times(1)).save(any(Admin.class));
    }

    @Test
    void setupWhenAlreadyInitializedReturns409() throws Exception {
        when(adminRepository.isEmpty()).thenReturn(false);

        Map<String, String> body = new HashMap<>();
        body.put("username", "adminuser");
        body.put("password", "strongpassword123");

        mockMvc.perform(post("/api/admin/setup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("An administrator already exists. Please log in."));
    }

    // === ADMIN MANAGEMENT TESTS ===

    @Test
    void listAdminsAsAppAdminReturnsList() throws Exception {
        String token = "admin-token";
        DecodedJWT mockJwt = mockJwt("admin1", "APP_ADMIN", null, "adminuser");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);

        Admin admin = Admin.builder()
                .adminId("admin1")
                .username("adminuser")
                .role("APP_ADMIN")
                .createdAt(Instant.now())
                .build();
        when(adminRepository.findAll()).thenReturn(List.of(admin));

        mockMvc.perform(get("/api/admin/admins")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("adminuser"))
                .andExpect(jsonPath("$[0].role").value("APP_ADMIN"));
    }

    @Test
    void listAdminsAsGroupAdminReturns403() throws Exception {
        String token = "group-admin-token";
        DecodedJWT mockJwt = mockJwt("groupAdmin1", "GROUP_ADMIN", "group123", "groupadmin");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);

        mockMvc.perform(get("/api/admin/admins")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createAdminAsAppAdminCreatesAccount() throws Exception {
        String token = "admin-token";
        DecodedJWT mockJwt = mockJwt("admin1", "APP_ADMIN", null, "adminuser");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);
        when(adminRepository.findByUsername("newadmin")).thenReturn(Optional.empty());

        Map<String, String> body = new HashMap<>();
        body.put("username", "newadmin");
        body.put("password", "newpassword123");
        body.put("role", "GROUP_ADMIN");
        body.put("groupId", "group123");

        mockMvc.perform(post("/api/admin/admins")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.adminId").exists());

        verify(adminRepository, times(1)).save(any(Admin.class));
    }

    @Test
    void deleteAdminCannotDeleteSelfReturns400() throws Exception {
        String token = "admin-token";
        DecodedJWT mockJwt = mockJwt("admin1", "APP_ADMIN", null, "adminuser");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);

        mockMvc.perform(delete("/api/admin/admins/admin1")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("You cannot delete your own account."));
    }

    // === PRAYER DASHBOARD TESTS ===

    @Test
    void getPrayersAsAppAdminReturnsPaginated() throws Exception {
        String token = "admin-token";
        DecodedJWT mockJwt = mockJwt("admin1", "APP_ADMIN", null, "adminuser");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);

        Prayer prayer = Prayer.builder()
                .prayerId("prayer123")
                .prayerText("Please pray for my exams")
                .status("OPEN")
                .createdAt(Instant.now())
                .build();

        when(prayerRepository.searchPrayers(any(), any(), any(), any())).thenReturn(List.of(prayer));
        when(prayerRepository.findUpdatesByPrayerId("prayer123")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/admin/prayers?page=0&size=20")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].prayerText").value("Please pray for my exams"));
    }

    @Test
    void getPrayersAsGroupAdminFilteredToGroup() throws Exception {
        String token = "group-admin-token";
        DecodedJWT mockJwt = mockJwt("admin2", "GROUP_ADMIN", "group123", "groupadmin");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);

        Prayer prayer = Prayer.builder()
                .prayerId("prayer456")
                .prayerText("Group prayer request")
                .assignedGroupId("group123")
                .status("OPEN")
                .build();

        when(prayerRepository.searchPrayers(any(), eq("group123"), any(), any())).thenReturn(List.of(prayer));

        mockMvc.perform(get("/api/admin/prayers?groupId=group123")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].prayerId").value("prayer456"));
    }

    // === GROUP MANAGEMENT TESTS ===

    @Test
    void listGroupsProxiesToGroupService() throws Exception {
        String token = "admin-token";
        DecodedJWT mockJwt = mockJwt("admin1", "APP_ADMIN", null, "adminuser");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);

        GroupDTO group = new GroupDTO();
        group.setGroupId("group123");
        group.setName("Test Group");

        when(restTemplate.exchange(
                eq("http://localhost:8083/api/groups"),
                eq(HttpMethod.GET),
                any(),
                any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(List.of(group), HttpStatus.OK));

        mockMvc.perform(get("/api/admin/groups")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Test Group"));
    }

    @Test
    void createGroupProxiesToGroupService() throws Exception {
        String token = "admin-token";
        DecodedJWT mockJwt = mockJwt("admin1", "APP_ADMIN", null, "adminuser");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);

        GroupDTO group = new GroupDTO();
        group.setName("New Group");

        when(restTemplate.postForEntity(
                eq("http://localhost:8083/api/groups"),
                any(GroupDTO.class),
                eq(GroupDTO.class)))
                .thenReturn(new ResponseEntity<>(group, HttpStatus.CREATED));

        mockMvc.perform(post("/api/admin/groups")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(group)))
                .andExpect(status().isCreated());
    }

    @Test
    void updateGroupAsGroupAdminForOwnGroupSucceeds() throws Exception {
        String token = "group-admin-token";
        DecodedJWT mockJwt = mockJwt("admin2", "GROUP_ADMIN", "group123", "groupadmin");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);

        GroupDTO dto = new GroupDTO();
        dto.setDescription("Updated desc");

        when(restTemplate.exchange(
                eq("http://localhost:8083/api/groups/group123"),
                eq(HttpMethod.PUT),
                any(HttpEntity.class),
                eq(GroupDTO.class)))
                .thenReturn(new ResponseEntity<>(dto, HttpStatus.OK));

        mockMvc.perform(put("/api/admin/groups/group123")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    void updateGroupAsGroupAdminForOtherGroupReturns403() throws Exception {
        String token = "group-admin-token";
        DecodedJWT mockJwt = mockJwt("admin2", "GROUP_ADMIN", "group123", "groupadmin");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);

        GroupDTO dto = new GroupDTO();
        dto.setName("Wrong Group");

        mockMvc.perform(put("/api/admin/groups/group999")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteGroupAsGroupAdminReturns403() throws Exception {
        String token = "group-admin-token";
        DecodedJWT mockJwt = mockJwt("admin2", "GROUP_ADMIN", "group123", "groupadmin");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);

        mockMvc.perform(delete("/api/admin/groups/group123")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token)))
                .andExpect(status().isForbidden());
    }

    // === MEMBER MANAGEMENT TESTS ===

    @Test
    void addMemberProxiesToGroupService() throws Exception {
        String token = "admin-token";
        DecodedJWT mockJwt = mockJwt("admin1", "APP_ADMIN", null, "adminuser");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);

        GroupMemberDTO member = new GroupMemberDTO();
        member.setEmail("member@example.com");

        when(restTemplate.postForEntity(
                eq("http://localhost:8083/api/groups/group123/members"),
                any(GroupMemberDTO.class),
                eq(GroupMemberDTO.class)))
                .thenReturn(new ResponseEntity<>(member, HttpStatus.CREATED));

        mockMvc.perform(post("/api/admin/groups/group123/members")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(member)))
                .andExpect(status().isCreated());
    }

    @Test
    void bulkAddMembersParsesAndAddsMultiple() throws Exception {
        String token = "admin-token";
        DecodedJWT mockJwt = mockJwt("admin1", "APP_ADMIN", null, "adminuser");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);

        Map<String, String> m1 = Map.of("name", "John", "email", "john@example.com");
        Map<String, String> m2 = Map.of("name", "Jane", "email", "jane@example.com");
        Map<String, List<Map<String, String>>> body = Map.of("members", List.of(m1, m2));

        when(restTemplate.postForEntity(
                eq("http://localhost:8083/api/groups/group123/members"),
                any(GroupMemberDTO.class),
                eq(GroupMemberDTO.class)))
                .thenReturn(new ResponseEntity<>(new GroupMemberDTO(), HttpStatus.CREATED));

        mockMvc.perform(post("/api/admin/groups/group123/members/bulk")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(2))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void bulkAddMembersWithInvalidEmailReturns400() throws Exception {
        String token = "admin-token";
        DecodedJWT mockJwt = mockJwt("admin1", "APP_ADMIN", null, "adminuser");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);

        Map<String, String> m1 = Map.of("name", "John", "email", "invalid-email");
        Map<String, List<Map<String, String>>> body = Map.of("members", List.of(m1));

        mockMvc.perform(post("/api/admin/groups/group123/members/bulk")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(0))
                .andExpect(jsonPath("$.errors[0].reason").value("Invalid email format."));
    }

    @Test
    void removeMemberProxiesToGroupService() throws Exception {
        String token = "admin-token";
        DecodedJWT mockJwt = mockJwt("admin1", "APP_ADMIN", null, "adminuser");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);

        mockMvc.perform(delete("/api/admin/groups/group123/members/member456")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token)))
                .andExpect(status().isNoContent());

        verify(restTemplate, times(1)).delete("http://localhost:8083/api/groups/group123/members/member456");
    }

    @Test
    void regeneratePasscodeProxiesToGroupService() throws Exception {
        String token = "admin-token";
        DecodedJWT mockJwt = mockJwt("admin1", "APP_ADMIN", null, "adminuser");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);

        when(restTemplate.exchange(
                eq("http://localhost:8083/api/groups/group123"),
                eq(HttpMethod.PUT),
                any(HttpEntity.class),
                eq(GroupDTO.class)))
                .thenReturn(new ResponseEntity<>(new GroupDTO(), HttpStatus.OK));

        mockMvc.perform(post("/api/admin/groups/group123/regenerate-passcode")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passcode").exists());
    }

    @Test
    void anyEndpointWithNoJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/admins"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anyEndpointWithIntercessorJwtReturns403() throws Exception {
        String token = "intercessor-token";
        DecodedJWT mockJwt = mockJwt(null, "INTERCESSOR", null, null);
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);

        mockMvc.perform(get("/api/admin/admins")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getStatusInvalidJwt() throws Exception {
        when(jwtUtil.verifyToken("invalid")).thenThrow(new RuntimeException("invalid"));
        mockMvc.perform(get("/api/admin/status")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", "invalid")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void setupWhenAlreadyExists() throws Exception {
        when(adminRepository.isEmpty()).thenReturn(false);
        mockMvc.perform(post("/api/admin/setup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"password123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void setupInvalidUsernameOrPassword() throws Exception {
        when(adminRepository.isEmpty()).thenReturn(true);
        mockMvc.perform(post("/api/admin/setup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ad\",\"password\":\"pass\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginMissingUsernameOrPassword() throws Exception {
        mockMvc.perform(post("/api/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginInvalidUsernameOrPassword() throws Exception {
        when(adminRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        mockMvc.perform(post("/api/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"user\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createAdminMissingFields() throws Exception {
        String token = "admin-token";
        DecodedJWT mockJwt = mockJwt("admin1", "APP_ADMIN", null, "adminuser");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);

        mockMvc.perform(post("/api/admin/admins")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAdminUsernameExists() throws Exception {
        String token = "admin-token";
        DecodedJWT mockJwt = mockJwt("admin1", "APP_ADMIN", null, "adminuser");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);
        when(adminRepository.findByUsername("admin")).thenReturn(Optional.of(new Admin()));

        mockMvc.perform(post("/api/admin/admins")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"pass\",\"role\":\"APP_ADMIN\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteAdminSelfDelete() throws Exception {
        String token = "admin-token";
        DecodedJWT mockJwt = mockJwt("admin1", "APP_ADMIN", null, "adminuser");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);

        mockMvc.perform(delete("/api/admin/admins/admin1")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteAdminLastAppAdmin() throws Exception {
        String token = "admin-token";
        DecodedJWT mockJwt = mockJwt("admin1", "APP_ADMIN", null, "adminuser");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);
        when(adminRepository.countAppAdmins()).thenReturn(1L);
        Admin target = new Admin();
        target.setRole("APP_ADMIN");
        when(adminRepository.findById("admin2")).thenReturn(Optional.of(target));

        mockMvc.perform(delete("/api/admin/admins/admin2")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getGroupsQueryFails() throws Exception {
        String token = "admin-token";
        DecodedJWT mockJwt = mockJwt("admin1", "APP_ADMIN", null, "adminuser");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("query error"));

        mockMvc.perform(get("/api/admin/groups")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void createGroupServiceFails() throws Exception {
        String token = "admin-token";
        DecodedJWT mockJwt = mockJwt("admin1", "APP_ADMIN", null, "adminuser");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);
        when(restTemplate.postForEntity(anyString(), any(), eq(GroupDTO.class)))
                .thenThrow(new RuntimeException("create error"));

        mockMvc.perform(post("/api/admin/groups")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"g1\"}"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void updateGroupServiceFails() throws Exception {
        String token = "admin-token";
        DecodedJWT mockJwt = mockJwt("admin1", "APP_ADMIN", null, "adminuser");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(), eq(GroupDTO.class)))
                .thenThrow(new RuntimeException("update error"));

        mockMvc.perform(put("/api/admin/groups/g1")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"g1\"}"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void deleteGroupServiceFails() throws Exception {
        String token = "admin-token";
        DecodedJWT mockJwt = mockJwt("admin1", "APP_ADMIN", null, "adminuser");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);
        doThrow(new RuntimeException("delete error")).when(restTemplate).delete(anyString());

        mockMvc.perform(delete("/api/admin/groups/g1")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void regeneratePasscodeServiceFails() throws Exception {
        String token = "admin-token";
        DecodedJWT mockJwt = mockJwt("admin1", "APP_ADMIN", null, "adminuser");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(), eq(GroupDTO.class)))
                .thenThrow(new RuntimeException("regen error"));

        mockMvc.perform(post("/api/admin/groups/g1/regenerate-passcode")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getMembersServiceFails() throws Exception {
        String token = "admin-token";
        DecodedJWT mockJwt = mockJwt("admin1", "APP_ADMIN", null, "adminuser");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("members error"));

        mockMvc.perform(get("/api/admin/groups/g1/members")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void addMemberInvalidEmail() throws Exception {
        String token = "admin-token";
        DecodedJWT mockJwt = mockJwt("admin1", "APP_ADMIN", null, "adminuser");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);

        mockMvc.perform(post("/api/admin/groups/g1/members")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"invalid-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addMemberServiceFails() throws Exception {
        String token = "admin-token";
        DecodedJWT mockJwt = mockJwt("admin1", "APP_ADMIN", null, "adminuser");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);
        when(restTemplate.postForEntity(anyString(), any(), eq(GroupMemberDTO.class)))
                .thenThrow(new RuntimeException("add error"));

        mockMvc.perform(post("/api/admin/groups/g1/members")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void bulkAddMembersMissingList() throws Exception {
        String token = "admin-token";
        DecodedJWT mockJwt = mockJwt("admin1", "APP_ADMIN", null, "adminuser");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);

        mockMvc.perform(post("/api/admin/groups/g1/members/bulk")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void removeMemberServiceFails() throws Exception {
        String token = "admin-token";
        DecodedJWT mockJwt = mockJwt("admin1", "APP_ADMIN", null, "adminuser");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);
        doThrow(new RuntimeException("remove error")).when(restTemplate).delete(anyString());

        mockMvc.perform(delete("/api/admin/groups/g1/members/m1")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void deleteAdminAdminNotFound() throws Exception {
        String token = "admin-token";
        DecodedJWT mockJwt = mockJwt("admin1", "APP_ADMIN", null, "adminuser");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);
        when(adminRepository.countAppAdmins()).thenReturn(1L);
        when(adminRepository.findById("admin2")).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/admin/admins/admin2")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token)))
                .andExpect(status().isNoContent());
    }

    @Test
    void bulkAddMembersBackendFails() throws Exception {
        String token = "admin-token";
        DecodedJWT mockJwt = mockJwt("admin1", "APP_ADMIN", null, "adminuser");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);

        Map<String, String> m1 = Map.of("name", "John", "email", "john@example.com");
        Map<String, List<Map<String, String>>> body = Map.of("members", List.of(m1));

        when(restTemplate.postForEntity(anyString(), any(), eq(GroupMemberDTO.class)))
                .thenThrow(new org.springframework.web.client.HttpClientErrorException(HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/admin/groups/group123/members/bulk")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(0))
                .andExpect(jsonPath("$.errors[0].reason").value("Backend returned: 400 BAD_REQUEST"));
    }

    @Test
    void bulkAddMembersConnectionFails() throws Exception {
        String token = "admin-token";
        DecodedJWT mockJwt = mockJwt("admin1", "APP_ADMIN", null, "adminuser");
        when(jwtUtil.verifyToken(token)).thenReturn(mockJwt);

        Map<String, String> m1 = Map.of("name", "John", "email", "john@example.com");
        Map<String, List<Map<String, String>>> body = Map.of("members", List.of(m1));

        when(restTemplate.postForEntity(anyString(), any(), eq(GroupMemberDTO.class)))
                .thenThrow(new RuntimeException("Connection timed out"));

        mockMvc.perform(post("/api/admin/groups/group123/members/bulk")
                .cookie(new jakarta.servlet.http.Cookie("pl-auth-token", token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(0))
                .andExpect(jsonPath("$.errors[0].reason").value("Connection error."));
    }
}
