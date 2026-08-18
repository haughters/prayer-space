package com.prayerlink.admin.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.prayerlink.admin.model.Admin;
import com.prayerlink.admin.model.Prayer;
import com.prayerlink.admin.repository.AdminRepository;
import com.prayerlink.admin.repository.PrayerRepository;
import com.prayerlink.admin.util.JwtUtil;
import com.prayerlink.common.dto.GroupDTO;
import com.prayerlink.common.dto.GroupMemberDTO;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
public class AdminControllerTest {

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private PrayerRepository prayerRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RestTemplate restTemplate;

    private AdminController controller;

    private final String secret = "secret12345678901234567890123456";
    private final UUID adminId = UUID.randomUUID();
    private final UUID groupId = UUID.randomUUID();
    private final UUID otherGroupId = UUID.randomUUID();
    private final UUID prayerId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();

    private String validAdminToken;
    private String validGroupAdminToken;

    @BeforeEach
    void setUp() {
        controller = new AdminController(adminRepository, prayerRepository, jwtUtil, passwordEncoder, restTemplate);

        ReflectionTestUtils.setField(controller, "groupServiceUrl", "http://localhost:8083");
        ReflectionTestUtils.setField(controller, "prayerServiceUrl", "http://localhost:8082");
        ReflectionTestUtils.setField(controller, "cookieSecure", false);

        Algorithm algorithm = Algorithm.HMAC256(secret);
        validAdminToken = JWT.create()
                .withSubject(adminId.toString())
                .withClaim("username", "admin")
                .withClaim("role", "APP_ADMIN")
                .sign(algorithm);

        validGroupAdminToken = JWT.create()
                .withSubject(adminId.toString())
                .withClaim("username", "groupadmin")
                .withClaim("role", "GROUP_ADMIN")
                .withClaim("groupId", groupId.toString())
                .sign(algorithm);
    }

    private void mockJwtVerification(String token) {
        when(jwtUtil.verifyToken(token)).thenReturn(JWT.decode(token));
    }

    @Test
    void testGetStatus_InitializedAndAuthenticated() {
        when(adminRepository.isEmpty()).thenReturn(false);
        mockJwtVerification(validAdminToken);

        ResponseEntity<Map<String, Object>> response = controller.getStatus(validAdminToken);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().get("initialized"));
        assertEquals(true, response.getBody().get("authenticated"));
        assertEquals("APP_ADMIN", response.getBody().get("role"));
    }

    @Test
    void testSetup_Success() {
        when(adminRepository.isEmpty()).thenReturn(true);
        when(passwordEncoder.encode("password123")).thenReturn("hashedPassword");
        when(jwtUtil.generateToken(any(UUID.class), eq("superadmin"), eq("APP_ADMIN"), isNull()))
                .thenReturn("new-jwt-token");

        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, String> body = Map.of("username", "superadmin", "password", "password123");

        ResponseEntity<?> result = controller.setup(body, response);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        verify(adminRepository).save(any(Admin.class));
        assertNotNull(response.getHeader("Set-Cookie"));
    }

    @Test
    void testSetup_AlreadyInitializedForbidden() {
        when(adminRepository.isEmpty()).thenReturn(false);

        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, String> body = Map.of("username", "admin", "password", "password123");

        ResponseEntity<?> result = controller.setup(body, response);
        assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
    }

    @Test
    void testLogin_AdminSuccess() {
        Admin admin = Admin.builder()
                .adminId(adminId)
                .username("admin")
                .passwordHash("hashedPass")
                .role("APP_ADMIN")
                .build();

        when(adminRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("pass12345", "hashedPass")).thenReturn(true);
        when(jwtUtil.generateToken(adminId, "admin", "APP_ADMIN", null)).thenReturn("tok");

        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, String> body = Map.of("username", "admin", "password", "pass12345");

        ResponseEntity<?> result = controller.login(body, response);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(response.getHeader("Set-Cookie"));
    }

    @Test
    void testLogout() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        ResponseEntity<Void> result = controller.logout(response);
        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        assertNotNull(response.getHeader("Set-Cookie"));
    }

    @Test
    void testGetAdmins() {
        mockJwtVerification(validAdminToken);

        Admin a = Admin.builder()
                .adminId(adminId)
                .username("adm")
                .role("APP_ADMIN")
                .build();
        when(adminRepository.findAll()).thenReturn(List.of(a));

        ResponseEntity<List<Map<String, Object>>> res = controller.getAdmins(validAdminToken);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(1, res.getBody().size());
    }

    @Test
    void testCreateAdmin_Success() {
        mockJwtVerification(validAdminToken);
        when(adminRepository.findByUsername("newadmin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secretpass")).thenReturn("encoded");

        Map<String, String> body = Map.of("username", "newadmin", "password", "secretpass", "role", "APP_ADMIN");
        ResponseEntity<?> res = controller.createAdmin(validAdminToken, body);
        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        verify(adminRepository).save(any(Admin.class));
    }

    @Test
    void testCreateGroupAdmin_Success() {
        mockJwtVerification(validAdminToken);
        when(adminRepository.findByUsername("grpadmin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secretpass")).thenReturn("encoded");

        Map<String, String> body = Map.of(
                "username", "grpadmin",
                "password", "secretpass",
                "role", "GROUP_ADMIN",
                "groupId", groupId.toString());
        ResponseEntity<?> res = controller.createAdmin(validAdminToken, body);
        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        verify(adminRepository).save(any(Admin.class));
    }

    @Test
    void testDeleteAdmin_CannotDeleteSelf() {
        mockJwtVerification(validAdminToken);

        ResponseEntity<?> res = controller.deleteAdmin(validAdminToken, adminId);
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
    }

    @Test
    void testGetPrayers() {
        mockJwtVerification(validAdminToken);

        Prayer p = Prayer.builder()
                .prayerId(prayerId)
                .prayerText("Pray")
                .status("OPEN")
                .createdAt(Instant.now())
                .build();
        when(prayerRepository.searchPrayers(any(), any(), any(), any())).thenReturn(List.of(p));
        when(prayerRepository.findUpdatesByPrayerId(prayerId)).thenReturn(Collections.emptyList());

        ResponseEntity<Map<String, Object>> res =
                controller.getPrayers(validAdminToken, 0, 20, "OPEN", null, "2026-01-01", "2026-01-31");
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(1, res.getBody().get("totalCount"));
    }

    @Test
    void testGetPrayers_AsGroupAdmin() {
        mockJwtVerification(validGroupAdminToken);

        when(prayerRepository.searchPrayers(any(), eq(groupId), any(), any())).thenReturn(Collections.emptyList());

        ResponseEntity<Map<String, Object>> res =
                controller.getPrayers(validGroupAdminToken, 0, 20, null, null, null, null);
        assertEquals(HttpStatus.OK, res.getStatusCode());
    }

    @Test
    void testGetGroups_Success() {
        mockJwtVerification(validAdminToken);

        GroupDTO g = GroupDTO.builder()
                .groupId(groupId)
                .name("Healing")
                .description("Desc")
                .passcode("CODE12")
                .build();
        GroupMemberDTO member = GroupMemberDTO.builder()
                .memberId(memberId)
                .groupId(groupId)
                .name("Alice")
                .build();

        when(restTemplate.exchange(
                        contains("/api/groups"), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(List.of(g)))
                .thenReturn(ResponseEntity.ok(List.of(member)));

        ResponseEntity<List<Map<String, Object>>> response = controller.getGroups(validAdminToken);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("Healing", response.getBody().get(0).get("name"));
        assertEquals(1, response.getBody().get(0).get("memberCount"));
    }

    @Test
    void testCreateGroup_Success() {
        mockJwtVerification(validAdminToken);

        GroupDTO dto = GroupDTO.builder().name("New Group").build();
        when(restTemplate.postForEntity(contains("/api/groups"), eq(dto), eq(GroupDTO.class)))
                .thenReturn(ResponseEntity.ok(dto));

        ResponseEntity<?> response = controller.createGroup(validAdminToken, dto);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testUpdateGroup_AsGroupAdmin_Success() {
        mockJwtVerification(validGroupAdminToken);

        GroupDTO dto = GroupDTO.builder().description("Updated desc").build();
        when(restTemplate.exchange(
                        contains("/api/groups/" + groupId),
                        eq(HttpMethod.PUT),
                        any(HttpEntity.class),
                        eq(GroupDTO.class)))
                .thenReturn(ResponseEntity.ok(dto));

        ResponseEntity<?> response = controller.updateGroup(validGroupAdminToken, groupId, dto);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testUpdateGroup_AsGroupAdmin_ForbiddenForOtherGroup() {
        mockJwtVerification(validGroupAdminToken);

        ResponseEntity<?> response = controller.updateGroup(validGroupAdminToken, otherGroupId, new GroupDTO());
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void testDeleteGroup_Success() {
        mockJwtVerification(validAdminToken);

        ResponseEntity<?> response = controller.deleteGroup(validAdminToken, groupId);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(restTemplate).delete(contains("/api/groups/" + groupId));
    }

    @Test
    void testRegeneratePasscode_AsAppAdmin() {
        mockJwtVerification(validAdminToken);

        ResponseEntity<?> response = controller.regeneratePasscode(validAdminToken, groupId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(((Map<?, ?>) response.getBody()).get("passcode"));
    }

    @Test
    void testRegeneratePasscode_AsGroupAdmin_Success() {
        mockJwtVerification(validGroupAdminToken);

        ResponseEntity<?> response = controller.regeneratePasscode(validGroupAdminToken, groupId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testRegeneratePasscode_AsGroupAdmin_ForbiddenForOtherGroup() {
        mockJwtVerification(validGroupAdminToken);

        ResponseEntity<?> response = controller.regeneratePasscode(validGroupAdminToken, otherGroupId);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void testGetMembers_AsAppAdmin() {
        mockJwtVerification(validAdminToken);

        GroupMemberDTO m =
                GroupMemberDTO.builder().memberId(memberId).name("Bob").build();
        when(restTemplate.exchange(
                        contains("/members"), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(List.of(m)));

        ResponseEntity<?> response = controller.getMembers(validAdminToken, groupId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testGetMembers_AsGroupAdmin_Success() {
        mockJwtVerification(validGroupAdminToken);

        GroupMemberDTO m =
                GroupMemberDTO.builder().memberId(memberId).name("Bob").build();
        when(restTemplate.exchange(
                        contains("/members"), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(List.of(m)));

        ResponseEntity<?> response = controller.getMembers(validGroupAdminToken, groupId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testGetMembers_AsGroupAdmin_ForbiddenForOtherGroup() {
        mockJwtVerification(validGroupAdminToken);

        ResponseEntity<?> response = controller.getMembers(validGroupAdminToken, otherGroupId);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void testAddMember_ValidEmail() {
        mockJwtVerification(validAdminToken);

        GroupMemberDTO dto = GroupMemberDTO.builder()
                .email("valid@example.com")
                .name("Valid")
                .build();
        when(restTemplate.postForEntity(contains("/members"), eq(dto), eq(GroupMemberDTO.class)))
                .thenReturn(ResponseEntity.ok(dto));

        ResponseEntity<?> response = controller.addMember(validAdminToken, groupId, dto);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testAddMember_AsGroupAdmin_ForbiddenForOtherGroup() {
        mockJwtVerification(validGroupAdminToken);

        GroupMemberDTO dto = GroupMemberDTO.builder()
                .email("valid@example.com")
                .name("Valid")
                .build();
        ResponseEntity<?> response = controller.addMember(validGroupAdminToken, otherGroupId, dto);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void testAddMember_InvalidEmail() {
        mockJwtVerification(validAdminToken);

        GroupMemberDTO dto = GroupMemberDTO.builder().email("invalid-email").build();
        ResponseEntity<?> response = controller.addMember(validAdminToken, groupId, dto);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void testBulkAddMembers_AsAppAdmin() {
        mockJwtVerification(validAdminToken);

        Map<String, List<Map<String, String>>> body = Map.of(
                "members",
                List.of(
                        Map.of("name", "User1", "email", "user1@example.com"),
                        Map.of("name", "BadUser", "email", "not-an-email"),
                        Map.of("name", "", "email", "")));

        ResponseEntity<?> response = controller.bulkAddMembers(validAdminToken, groupId, body);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> result = (Map<?, ?>) response.getBody();
        assertEquals(1, result.get("added"));
        assertEquals(2, ((List<?>) result.get("errors")).size());
    }

    @Test
    void testBulkAddMembers_AsGroupAdmin_ForbiddenForOtherGroup() {
        mockJwtVerification(validGroupAdminToken);

        Map<String, List<Map<String, String>>> body =
                Map.of("members", List.of(Map.of("name", "User1", "email", "user1@example.com")));

        ResponseEntity<?> response = controller.bulkAddMembers(validGroupAdminToken, otherGroupId, body);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void testRemoveMember_AsAppAdmin() {
        mockJwtVerification(validAdminToken);

        ResponseEntity<?> response = controller.removeMember(validAdminToken, groupId, memberId);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(restTemplate).delete(contains("/members/" + memberId));
    }

    @Test
    void testRemoveMember_AsGroupAdmin_Success() {
        mockJwtVerification(validGroupAdminToken);

        ResponseEntity<?> response = controller.removeMember(validGroupAdminToken, groupId, memberId);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(restTemplate).delete(contains("/members/" + memberId));
    }

    @Test
    void testRemoveMember_AsGroupAdmin_ForbiddenForOtherGroup() {
        mockJwtVerification(validGroupAdminToken);

        ResponseEntity<?> response = controller.removeMember(validGroupAdminToken, otherGroupId, memberId);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void testCheckAuth_MissingTokenThrows() {
        assertThrows(ResponseStatusException.class, () -> controller.getGroups(null));
    }
}
