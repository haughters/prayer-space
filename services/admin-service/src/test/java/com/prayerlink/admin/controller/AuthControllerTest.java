package com.prayerlink.admin.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.prayerlink.admin.model.Admin;
import com.prayerlink.admin.model.IntercessorAccount;
import com.prayerlink.admin.repository.AdminRepository;
import com.prayerlink.admin.repository.IntercessorAccountRepository;
import com.prayerlink.admin.util.JwtUtil;
import com.prayerlink.common.dto.GroupDTO;
import com.prayerlink.common.dto.GroupMemberDTO;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
public class AuthControllerTest {

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private IntercessorAccountRepository intercessorAccountRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RestTemplate restTemplate;

    private AuthController controller;

    private final String secret = "secret12345678901234567890123456";
    private final UUID adminId = UUID.randomUUID();
    private final UUID groupId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new AuthController(
                adminRepository, intercessorAccountRepository, jwtUtil, passwordEncoder, restTemplate);

        ReflectionTestUtils.setField(controller, "groupServiceUrl", "http://localhost:8083");
        ReflectionTestUtils.setField(controller, "cookieSecure", false);
    }

    @Test
    void testGetStatus_UninitializedAndUnauthenticated() {
        when(adminRepository.isEmpty()).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = controller.getStatus(null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(false, response.getBody().get("initialized"));
        assertEquals(false, response.getBody().get("authenticated"));
    }

    @Test
    void testGetStatus_AuthenticatedAppAdmin() {
        when(adminRepository.isEmpty()).thenReturn(false);

        String token = JWT.create()
                .withSubject(adminId.toString())
                .withClaim("username", "adminUser")
                .withClaim("role", "APP_ADMIN")
                .sign(Algorithm.HMAC256(secret));

        when(jwtUtil.verifyToken(token)).thenReturn(JWT.decode(token));

        ResponseEntity<Map<String, Object>> response = controller.getStatus(token);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().get("initialized"));
        assertEquals(true, response.getBody().get("authenticated"));
        assertEquals("APP_ADMIN", response.getBody().get("role"));
        assertEquals("adminUser", response.getBody().get("username"));
    }

    @Test
    void testGetStatus_AuthenticatedIntercessorWithGroups() {
        when(adminRepository.isEmpty()).thenReturn(false);

        String email = "intercessor@example.com";
        String token = JWT.create()
                .withClaim("email", email)
                .withClaim("name", "Mary")
                .withClaim("role", "INTERCESSOR")
                .sign(Algorithm.HMAC256(secret));

        when(jwtUtil.verifyToken(token)).thenReturn(JWT.decode(token));

        GroupMemberDTO member =
                GroupMemberDTO.builder().groupId(groupId).email(email).build();
        GroupDTO group =
                GroupDTO.builder().groupId(groupId).name("Intercession Circle").build();

        when(restTemplate.exchange(
                        contains("/api/groups/members/search?email=" + email),
                        eq(HttpMethod.GET),
                        isNull(),
                        any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(List.of(member)));
        when(restTemplate.getForObject(contains("/api/groups/" + groupId), eq(GroupDTO.class)))
                .thenReturn(group);

        ResponseEntity<Map<String, Object>> response = controller.getStatus(token);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().get("authenticated"));
        assertEquals("INTERCESSOR", response.getBody().get("role"));
        assertEquals("Mary", response.getBody().get("name"));
        List<?> groups = (List<?>) response.getBody().get("groups");
        assertEquals(1, groups.size());
    }

    @Test
    void testLogin_Admin_Success() {
        Admin admin = Admin.builder()
                .adminId(adminId)
                .username("admin1")
                .passwordHash("encodedHash")
                .role("APP_ADMIN")
                .build();

        when(adminRepository.findByUsername("admin1")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("pass123", "encodedHash")).thenReturn(true);
        when(jwtUtil.generateToken(adminId, "admin1", "APP_ADMIN", null)).thenReturn("admin-token");

        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, String> body = Map.of("identifier", "admin1", "password", "pass123");

        ResponseEntity<?> result = controller.login(body, response);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        Map<?, ?> respBody = (Map<?, ?>) result.getBody();
        assertEquals("APP_ADMIN", respBody.get("role"));
        assertEquals("admin1", respBody.get("username"));
        assertNotNull(response.getHeader("Set-Cookie"));
    }

    @Test
    void testLogin_Intercessor_Success() {
        when(adminRepository.findByUsername("intercessor@example.com")).thenReturn(Optional.empty());

        IntercessorAccount account = IntercessorAccount.builder()
                .email("intercessor@example.com")
                .name("Sarah")
                .passwordHash("encodedHash")
                .build();

        when(intercessorAccountRepository.findById("intercessor@example.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("interpass", "encodedHash")).thenReturn(true);
        when(jwtUtil.generateTokenForIntercessor("intercessor@example.com", "Sarah"))
                .thenReturn("intercessor-token");

        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, String> body = Map.of("identifier", "intercessor@example.com", "password", "interpass");

        ResponseEntity<?> result = controller.login(body, response);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        Map<?, ?> respBody = (Map<?, ?>) result.getBody();
        assertEquals("INTERCESSOR", respBody.get("role"));
        assertEquals("intercessor@example.com", respBody.get("email"));
    }

    @Test
    void testLogin_InvalidCredentials() {
        when(adminRepository.findByUsername("unknown")).thenReturn(Optional.empty());
        when(intercessorAccountRepository.findById("unknown")).thenReturn(Optional.empty());

        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, String> body = Map.of("identifier", "unknown", "password", "wrongpass");

        ResponseEntity<?> result = controller.login(body, response);
        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
    }

    @Test
    void testLogin_MissingCredentials() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, String> body = Map.of("identifier", "", "password", "");

        ResponseEntity<?> result = controller.login(body, response);
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    }

    @Test
    void testLogout() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        ResponseEntity<?> result = controller.logout(response);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(response.getHeader("Set-Cookie"));
        assertTrue(response.getHeader("Set-Cookie").contains("Max-Age=0"));
    }
}
