package com.prayerlink.notification.listener;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import tools.jackson.databind.ObjectMapper;
import com.prayerlink.common.dto.GroupDTO;
import com.prayerlink.common.dto.GroupMemberDTO;
import com.prayerlink.common.dto.PrayerDTO;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;

public class NotificationListenerTest {

    private NotificationListener listener;
    private RestTemplate restTemplate;
    private SesClient sesClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        sesClient = mock(SesClient.class);
        objectMapper = new ObjectMapper();

        listener = new NotificationListener(restTemplate, sesClient, objectMapper);

        // Inject @Value fields
        ReflectionTestUtils.setField(listener, "groupServiceUrl", "http://localhost:8083");
        ReflectionTestUtils.setField(listener, "prayerServiceUrl", "http://localhost:8082");
        ReflectionTestUtils.setField(listener, "appDomain", "localhost:5173");
        ReflectionTestUtils.setField(listener, "hmacSecretKey", "test-secret-key-must-be-very-long-and-secure-for-hmac-sha-256");
    }

    // 1. onPrayerCreatedSendsEmailToGroupMembers
    @Test
    void onPrayerCreatedSendsEmailToGroupMembers() throws Exception {
        String sqsMessage = "{" +
                "  \"detail-type\": \"PrayerCreated\"," +
                "  \"detail\": {" +
                "    \"prayerId\": \"prayer-123\"," +
                "    \"prayerText\": \"Please pray for healing.\"," +
                "    \"assignedGroupId\": \"group-123\"" +
                "  }" +
                "}";

        GroupDTO group = new GroupDTO();
        group.setGroupId("group-123");
        group.setName("Healing Circle");
        when(restTemplate.getForObject("http://localhost:8083/api/groups/group-123", GroupDTO.class))
                .thenReturn(group);

        GroupMemberDTO member1 = new GroupMemberDTO();
        member1.setEmail("m1@example.com");
        member1.setName("Member One");
        member1.setBounced(false);

        List<GroupMemberDTO> members = Arrays.asList(member1);
        ResponseEntity<List<GroupMemberDTO>> responseEntity = new ResponseEntity<>(members, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://localhost:8083/api/groups/group-123/members"),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        SendEmailResponse sesResponse = SendEmailResponse.builder().messageId("msg-123").build();
        when(sesClient.sendEmail(any(SendEmailRequest.class))).thenReturn(sesResponse);

        listener.listenToNotifications(sqsMessage);

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient, times(1)).sendEmail(captor.capture());
        
        SendEmailRequest sentRequest = captor.getValue();
        assertEquals("m1@example.com", sentRequest.destination().toAddresses().get(0));
        assertTrue(sentRequest.message().subject().data().contains("Someone Needs Your Prayers"));
    }

    // 2. onPrayerCreatedSkipsBouncedMembers
    @Test
    void onPrayerCreatedSkipsBouncedMembers() throws Exception {
        String sqsMessage = "{" +
                "  \"detail-type\": \"PrayerCreated\"," +
                "  \"detail\": {" +
                "    \"prayerId\": \"prayer-123\"," +
                "    \"prayerText\": \"Please pray for healing.\"," +
                "    \"assignedGroupId\": \"group-123\"" +
                "  }" +
                "}";

        GroupDTO group = new GroupDTO();
        group.setGroupId("group-123");
        group.setName("Healing Circle");
        when(restTemplate.getForObject("http://localhost:8083/api/groups/group-123", GroupDTO.class))
                .thenReturn(group);

        GroupMemberDTO member1 = new GroupMemberDTO();
        member1.setEmail("bounced@example.com");
        member1.setBounced(true);

        GroupMemberDTO member2 = new GroupMemberDTO();
        member2.setEmail("active@example.com");
        member2.setBounced(false);

        List<GroupMemberDTO> members = Arrays.asList(member1, member2);
        ResponseEntity<List<GroupMemberDTO>> responseEntity = new ResponseEntity<>(members, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://localhost:8083/api/groups/group-123/members"),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        SendEmailResponse sesResponse = SendEmailResponse.builder().messageId("msg-123").build();
        when(sesClient.sendEmail(any(SendEmailRequest.class))).thenReturn(sesResponse);

        listener.listenToNotifications(sqsMessage);

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient, times(1)).sendEmail(captor.capture());
        assertEquals("active@example.com", captor.getValue().destination().toAddresses().get(0));
    }

    // 3. onPrayerCreatedWithNoMembersNoEmailSent
    @Test
    void onPrayerCreatedWithNoMembersNoEmailSent() throws Exception {
        String sqsMessage = "{" +
                "  \"detail-type\": \"PrayerCreated\"," +
                "  \"detail\": {" +
                "    \"prayerId\": \"prayer-123\"," +
                "    \"prayerText\": \"Please pray for healing.\"," +
                "    \"assignedGroupId\": \"group-123\"" +
                "  }" +
                "}";

        GroupDTO group = new GroupDTO();
        group.setGroupId("group-123");
        group.setName("Healing Circle");
        when(restTemplate.getForObject("http://localhost:8083/api/groups/group-123", GroupDTO.class))
                .thenReturn(group);

        ResponseEntity<List<GroupMemberDTO>> responseEntity = new ResponseEntity<>(List.of(), HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://localhost:8083/api/groups/group-123/members"),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        listener.listenToNotifications(sqsMessage);

        verify(sesClient, never()).sendEmail(any(SendEmailRequest.class));
    }

    // 4. onPrayerCreatedGeneratesValidHmacTokenPerMember
    @Test
    void onPrayerCreatedGeneratesValidHmacTokenPerMember() throws Exception {
        String sqsMessage = "{" +
                "  \"detail-type\": \"PrayerCreated\"," +
                "  \"detail\": {" +
                "    \"prayerId\": \"prayer-123\"," +
                "    \"prayerText\": \"Please pray for healing.\"," +
                "    \"assignedGroupId\": \"group-123\"" +
                "  }" +
                "}";

        GroupDTO group = new GroupDTO();
        group.setGroupId("group-123");
        group.setName("Healing Circle");
        when(restTemplate.getForObject("http://localhost:8083/api/groups/group-123", GroupDTO.class))
                .thenReturn(group);

        GroupMemberDTO member = new GroupMemberDTO();
        member.setEmail("member@example.com");
        member.setBounced(false);

        List<GroupMemberDTO> members = List.of(member);
        ResponseEntity<List<GroupMemberDTO>> responseEntity = new ResponseEntity<>(members, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://localhost:8083/api/groups/group-123/members"),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        SendEmailResponse sesResponse = SendEmailResponse.builder().messageId("msg-123").build();
        when(sesClient.sendEmail(any(SendEmailRequest.class))).thenReturn(sesResponse);

        listener.listenToNotifications(sqsMessage);

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient, times(1)).sendEmail(captor.capture());

        SendEmailRequest sentRequest = captor.getValue();
        String htmlBody = sentRequest.message().body().html().data();

        assertTrue(htmlBody.contains("/pray/prayer-123/"));
        int idx = htmlBody.indexOf("/pray/prayer-123/") + "/pray/prayer-123/".length();
        int endIdx = htmlBody.indexOf("\"", idx);
        String token = htmlBody.substring(idx, endIdx);

        String[] parts = token.split("\\|");
        assertEquals(3, parts.length);
        assertEquals("group-123", parts[1]);
        
        String signature = parts[0];
        String expiry = parts[2];
        String payloadStr = "group-123:member@example.com:" + expiry;
        String hmacSecretKey = "test-secret-key-must-be-very-long-and-secure-for-hmac-sha-256";
        
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(hmacSecretKey.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] rawHmac = mac.doFinal(payloadStr.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String expectedSignature = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
        
        assertEquals(expectedSignature, signature);
    }

    // 5. onPrayerCreatedEmailContainsPrayerText
    @Test
    void onPrayerCreatedEmailContainsPrayerText() throws Exception {
        String sqsMessage = "{" +
                "  \"detail-type\": \"PrayerCreated\"," +
                "  \"detail\": {" +
                "    \"prayerId\": \"prayer-123\"," +
                "    \"prayerText\": \"Specific prayer request content.\"," +
                "    \"assignedGroupId\": \"group-123\"" +
                "  }" +
                "}";

        GroupDTO group = new GroupDTO();
        group.setGroupId("group-123");
        group.setName("Healing Circle");
        when(restTemplate.getForObject("http://localhost:8083/api/groups/group-123", GroupDTO.class))
                .thenReturn(group);

        GroupMemberDTO member = new GroupMemberDTO();
        member.setEmail("m1@example.com");
        member.setBounced(false);

        List<GroupMemberDTO> members = List.of(member);
        ResponseEntity<List<GroupMemberDTO>> responseEntity = new ResponseEntity<>(members, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://localhost:8083/api/groups/group-123/members"),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        SendEmailResponse sesResponse = SendEmailResponse.builder().messageId("msg-123").build();
        when(sesClient.sendEmail(any(SendEmailRequest.class))).thenReturn(sesResponse);

        listener.listenToNotifications(sqsMessage);

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(captor.capture());
        assertTrue(captor.getValue().message().body().html().data().contains("Specific prayer request content."));
        assertTrue(captor.getValue().message().body().text().data().contains("Specific prayer request content."));
    }

    // 6. onPrayerUpdatedSendsFollowUpEmail
    @Test
    void onPrayerUpdatedSendsFollowUpEmail() throws Exception {
        String sqsMessage = "{" +
                "  \"detail-type\": \"PrayerUpdated\"," +
                "  \"detail\": {" +
                "    \"prayerId\": \"prayer-123\"," +
                "    \"updateText\": \"Healed completely!\"" +
                "  }" +
                "}";

        PrayerDTO prayer = new PrayerDTO();
        prayer.setPrayerId("prayer-123");
        prayer.setAssignedGroupId("group-123");
        prayer.setPrayerText("Original prayer request.");
        when(restTemplate.getForObject("http://localhost:8082/api/prayers/prayer-123", PrayerDTO.class))
                .thenReturn(prayer);

        GroupDTO group = new GroupDTO();
        group.setGroupId("group-123");
        group.setName("Healing Circle");
        when(restTemplate.getForObject("http://localhost:8083/api/groups/group-123", GroupDTO.class))
                .thenReturn(group);

        GroupMemberDTO member1 = new GroupMemberDTO();
        member1.setEmail("m1@example.com");
        member1.setBounced(false);

        List<GroupMemberDTO> members = List.of(member1);
        ResponseEntity<List<GroupMemberDTO>> responseEntity = new ResponseEntity<>(members, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://localhost:8083/api/groups/group-123/members"),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        SendEmailResponse sesResponse = SendEmailResponse.builder().messageId("msg-123").build();
        when(sesClient.sendEmail(any(SendEmailRequest.class))).thenReturn(sesResponse);

        listener.listenToNotifications(sqsMessage);

        verify(sesClient, times(1)).sendEmail(any(SendEmailRequest.class));
    }

    // 7. onPrayerUpdatedForClosedPrayerIncludesClosureMessage
    @Test
    void onPrayerUpdatedForClosedPrayerIncludesClosureMessage() throws Exception {
        String sqsMessage = "{" +
                "  \"detail-type\": \"PrayerUpdated\"," +
                "  \"detail\": {" +
                "    \"prayerId\": \"prayer-123\"," +
                "    \"updateText\": \"Healed completely!\"" +
                "  }" +
                "}";

        PrayerDTO prayer = new PrayerDTO();
        prayer.setPrayerId("prayer-123");
        prayer.setAssignedGroupId("group-123");
        prayer.setPrayerText("Original prayer request.");
        when(restTemplate.getForObject("http://localhost:8082/api/prayers/prayer-123", PrayerDTO.class))
                .thenReturn(prayer);

        GroupDTO group = new GroupDTO();
        group.setGroupId("group-123");
        group.setName("Healing Circle");
        when(restTemplate.getForObject("http://localhost:8083/api/groups/group-123", GroupDTO.class))
                .thenReturn(group);

        GroupMemberDTO member1 = new GroupMemberDTO();
        member1.setEmail("m1@example.com");
        member1.setBounced(false);

        List<GroupMemberDTO> members = List.of(member1);
        ResponseEntity<List<GroupMemberDTO>> responseEntity = new ResponseEntity<>(members, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://localhost:8083/api/groups/group-123/members"),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        SendEmailResponse sesResponse = SendEmailResponse.builder().messageId("msg-123").build();
        when(sesClient.sendEmail(any(SendEmailRequest.class))).thenReturn(sesResponse);

        listener.listenToNotifications(sqsMessage);

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(captor.capture());
        assertTrue(captor.getValue().message().body().html().data().contains("This prayer request has been closed."));
        assertTrue(captor.getValue().message().body().text().data().contains("This prayer request has been closed."));
    }

    // 8. onMemberAddedSendsWelcomeEmail
    @Test
    void onMemberAddedSendsWelcomeEmail() throws Exception {
        String sqsMessage = "{" +
                "  \"detail-type\": \"MemberAdded\"," +
                "  \"detail\": {" +
                "    \"groupId\": \"group-123\"," +
                "    \"email\": \"newmember@example.com\"," +
                "    \"name\": \"New Member\"" +
                "  }" +
                "}";

        GroupDTO group = new GroupDTO();
        group.setGroupId("group-123");
        group.setName("Healing Circle");
        when(restTemplate.getForObject("http://localhost:8083/api/groups/group-123", GroupDTO.class))
                .thenReturn(group);

        SendEmailResponse sesResponse = SendEmailResponse.builder().messageId("msg-123").build();
        when(sesClient.sendEmail(any(SendEmailRequest.class))).thenReturn(sesResponse);

        listener.listenToNotifications(sqsMessage);

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(captor.capture());
        assertEquals("newmember@example.com", captor.getValue().destination().toAddresses().get(0));
        assertTrue(captor.getValue().message().subject().data().contains("You're Invited to Join Prayer Link"));
    }

    // 9. onPermanentBounceCallsGroupService
    @Test
    void onPermanentBounceCallsGroupService() {
        String snsBouncePayload = "{" +
                "  \"Type\": \"Notification\"," +
                "  \"Message\": \"{\\\"notificationType\\\":\\\"Bounce\\\",\\\"bounce\\\":{\\\"bounceType\\\":\\\"Permanent\\\",\\\"bouncedRecipients\\\":[{\\\"emailAddress\\\":\\\"bad@example.com\\\"}]}}\"" +
                "}";

        listener.listenToBounces(snsBouncePayload);

        verify(restTemplate, times(1)).put(
                eq("http://localhost:8083/api/groups/members/bounce"),
                eq(Map.of("email", "bad@example.com"))
        );
    }

    // 10. onTransientBounceDoesNotMarkBounced
    @Test
    void onTransientBounceDoesNotMarkBounced() {
        String snsBouncePayload = "{" +
                "  \"Type\": \"Notification\"," +
                "  \"Message\": \"{\\\"notificationType\\\":\\\"Bounce\\\",\\\"bounce\\\":{\\\"bounceType\\\":\\\"Transient\\\",\\\"bouncedRecipients\\\":[{\\\"emailAddress\\\":\\\"bad@example.com\\\"}]}}\"" +
                "}";

        listener.listenToBounces(snsBouncePayload);

        verify(restTemplate, never()).put(anyString(), any());
    }

    // 11. onBounceWithInvalidPayloadLogsAndSkips
    @Test
    void onBounceWithInvalidPayloadLogsAndSkips() {
        String malformedPayload = "malformed JSON {{{{";
        assertDoesNotThrow(() -> listener.listenToBounces(malformedPayload));
    }

    // 12. onSesFailureLogsErrorAndDoesNotThrow
    @Test
    void onSesFailureLogsErrorAndDoesNotThrow() throws Exception {
        String sqsMessage = "{" +
                "  \"detail-type\": \"PrayerCreated\"," +
                "  \"detail\": {" +
                "    \"prayerId\": \"prayer-123\"," +
                "    \"prayerText\": \"Please pray for healing.\"," +
                "    \"assignedGroupId\": \"group-123\"" +
                "  }" +
                "}";

        GroupDTO group = new GroupDTO();
        group.setGroupId("group-123");
        group.setName("Healing Circle");
        when(restTemplate.getForObject("http://localhost:8083/api/groups/group-123", GroupDTO.class))
                .thenReturn(group);

        GroupMemberDTO member = new GroupMemberDTO();
        member.setEmail("m1@example.com");
        member.setBounced(false);

        List<GroupMemberDTO> members = List.of(member);
        ResponseEntity<List<GroupMemberDTO>> responseEntity = new ResponseEntity<>(members, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://localhost:8083/api/groups/group-123/members"),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        when(sesClient.sendEmail(any(SendEmailRequest.class))).thenThrow(new RuntimeException("SES failure"));
 
         assertDoesNotThrow(() -> listener.listenToNotifications(sqsMessage));
     }
 
    @Test
    void listenToNotificationsUnknownTypeLogsWarning() {
        String sqsMessage = "{\"detail-type\": \"UnknownType\", \"detail\": {}}";
        assertDoesNotThrow(() -> listener.listenToNotifications(sqsMessage));
    }

    @Test
    void listenToNotificationsGenericExceptionThrowsRuntimeException() {
        String invalidJson = "invalid-json";
        assertThrows(RuntimeException.class, () -> listener.listenToNotifications(invalidJson));
    }

    @Test
    void listenToBouncesNonBounceSnsLogsWarning() {
        String snsBouncePayload = "{" +
                "  \"Type\": \"Notification\"," +
                "  \"Message\": \"{\\\"notificationType\\\":\\\"Complaint\\\"}\"" +
                "}";
        assertDoesNotThrow(() -> listener.listenToBounces(snsBouncePayload));
    }

    @Test
    void listenToBouncesEmptyRecipientSkips() {
        String snsBouncePayload = "{" +
                "  \"Type\": \"Notification\"," +
                "  \"Message\": \"{\\\"notificationType\\\":\\\"Bounce\\\",\\\"bounce\\\":{\\\"bounceType\\\":\\\"Permanent\\\",\\\"bouncedRecipients\\\":[{\\\"emailAddress\\\":\\\"\\\"}]}}\"" +
                "}";
        assertDoesNotThrow(() -> listener.listenToBounces(snsBouncePayload));
    }

    @Test
    void processPrayerCreatedNoGroupIdSkips() {
        String sqsMessage = "{\"detail-type\": \"PrayerCreated\", \"detail\": {\"prayerId\": \"p1\"}}";
        assertDoesNotThrow(() -> listener.listenToNotifications(sqsMessage));
    }

    @Test
    void processPrayerCreatedGroupFetchThrowsSkips() {
        String sqsMessage = "{\"detail-type\": \"PrayerCreated\", \"detail\": {\"prayerId\": \"p1\", \"assignedGroupId\": \"g1\"}}";
        when(restTemplate.getForObject(anyString(), eq(GroupDTO.class))).thenThrow(new org.springframework.web.client.RestClientException("Fetch error"));
        assertDoesNotThrow(() -> listener.listenToNotifications(sqsMessage));
    }

    @Test
    void processPrayerCreatedGroupNotFoundSkips() {
        String sqsMessage = "{\"detail-type\": \"PrayerCreated\", \"detail\": {\"prayerId\": \"p1\", \"assignedGroupId\": \"g1\"}}";
        when(restTemplate.getForObject(anyString(), eq(GroupDTO.class))).thenReturn(null);
        assertDoesNotThrow(() -> listener.listenToNotifications(sqsMessage));
    }

    @Test
    void processPrayerCreatedMembersFetchThrowsSkips() {
        String sqsMessage = "{\"detail-type\": \"PrayerCreated\", \"detail\": {\"prayerId\": \"p1\", \"assignedGroupId\": \"g1\"}}";
        GroupDTO group = new GroupDTO();
        when(restTemplate.getForObject(anyString(), eq(GroupDTO.class))).thenReturn(group);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenThrow(new org.springframework.web.client.RestClientException("Fetch members error"));
        assertDoesNotThrow(() -> listener.listenToNotifications(sqsMessage));
    }

    @Test
    void processPrayerCreatedNoMembersSkips() {
        String sqsMessage = "{\"detail-type\": \"PrayerCreated\", \"detail\": {\"prayerId\": \"p1\", \"assignedGroupId\": \"g1\"}}";
        GroupDTO group = new GroupDTO();
        when(restTemplate.getForObject(anyString(), eq(GroupDTO.class))).thenReturn(group);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(List.of(), HttpStatus.OK));
        assertDoesNotThrow(() -> listener.listenToNotifications(sqsMessage));
    }

    @Test
    void processPrayerCreatedNoEligibleMembersSkips() {
        String sqsMessage = "{\"detail-type\": \"PrayerCreated\", \"detail\": {\"prayerId\": \"p1\", \"assignedGroupId\": \"g1\"}}";
        GroupDTO group = new GroupDTO();
        when(restTemplate.getForObject(anyString(), eq(GroupDTO.class))).thenReturn(group);
        GroupMemberDTO member = new GroupMemberDTO();
        member.setBounced(true);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(List.of(member), HttpStatus.OK));
        assertDoesNotThrow(() -> listener.listenToNotifications(sqsMessage));
    }

    @Test
    void processPrayerUpdatedThrowsSkips() {
        String sqsMessage = "{\"detail-type\": \"PrayerUpdated\", \"detail\": {\"prayerId\": \"p1\", \"updateText\": \"up\"}}";
        when(restTemplate.getForObject(anyString(), eq(PrayerDTO.class))).thenThrow(new org.springframework.web.client.RestClientException("error"));
        assertDoesNotThrow(() -> listener.listenToNotifications(sqsMessage));
    }

    @Test
    void processPrayerUpdatedNullPrayerOrNoGroupSkips() {
        String sqsMessage = "{\"detail-type\": \"PrayerUpdated\", \"detail\": {\"prayerId\": \"p1\", \"updateText\": \"up\"}}";
        when(restTemplate.getForObject(anyString(), eq(PrayerDTO.class))).thenReturn(null);
        assertDoesNotThrow(() -> listener.listenToNotifications(sqsMessage));
    }

    @Test
    void processPrayerUpdatedGroupFetchThrowsSkips() {
        String sqsMessage = "{\"detail-type\": \"PrayerUpdated\", \"detail\": {\"prayerId\": \"p1\", \"updateText\": \"up\"}}";
        PrayerDTO prayer = new PrayerDTO();
        prayer.setAssignedGroupId("g1");
        when(restTemplate.getForObject(contains("/api/prayers/"), eq(PrayerDTO.class))).thenReturn(prayer);
        when(restTemplate.getForObject(contains("/api/groups/"), eq(GroupDTO.class))).thenThrow(new org.springframework.web.client.RestClientException("error"));
        assertDoesNotThrow(() -> listener.listenToNotifications(sqsMessage));
    }

    @Test
    void processPrayerUpdatedGroupNotFoundSkips() {
        String sqsMessage = "{\"detail-type\": \"PrayerUpdated\", \"detail\": {\"prayerId\": \"p1\", \"updateText\": \"up\"}}";
        PrayerDTO prayer = new PrayerDTO();
        prayer.setAssignedGroupId("g1");
        when(restTemplate.getForObject(contains("/api/prayers/"), eq(PrayerDTO.class))).thenReturn(prayer);
        when(restTemplate.getForObject(contains("/api/groups/"), eq(GroupDTO.class))).thenReturn(null);
        assertDoesNotThrow(() -> listener.listenToNotifications(sqsMessage));
    }

    @Test
    void processPrayerUpdatedMembersFetchThrowsSkips() {
        String sqsMessage = "{\"detail-type\": \"PrayerUpdated\", \"detail\": {\"prayerId\": \"p1\", \"updateText\": \"up\"}}";
        PrayerDTO prayer = new PrayerDTO();
        prayer.setAssignedGroupId("g1");
        when(restTemplate.getForObject(contains("/api/prayers/"), eq(PrayerDTO.class))).thenReturn(prayer);
        when(restTemplate.getForObject(contains("/api/groups/"), eq(GroupDTO.class))).thenReturn(new GroupDTO());
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenThrow(new org.springframework.web.client.RestClientException("error"));
        assertDoesNotThrow(() -> listener.listenToNotifications(sqsMessage));
    }

    @Test
    void processPrayerUpdatedNoMembersSkips() {
        String sqsMessage = "{\"detail-type\": \"PrayerUpdated\", \"detail\": {\"prayerId\": \"p1\", \"updateText\": \"up\"}}";
        PrayerDTO prayer = new PrayerDTO();
        prayer.setAssignedGroupId("g1");
        when(restTemplate.getForObject(contains("/api/prayers/"), eq(PrayerDTO.class))).thenReturn(prayer);
        when(restTemplate.getForObject(contains("/api/groups/"), eq(GroupDTO.class))).thenReturn(new GroupDTO());
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(List.of(), HttpStatus.OK));
        assertDoesNotThrow(() -> listener.listenToNotifications(sqsMessage));
    }

    @Test
    void processMemberAddedGroupNotFoundSkips() {
        String sqsMessage = "{\"detail-type\": \"MemberAdded\", \"detail\": {\"groupId\": \"g1\", \"email\": \"m@ex.com\"}}";
        when(restTemplate.getForObject(anyString(), eq(GroupDTO.class))).thenReturn(null);
        assertDoesNotThrow(() -> listener.listenToNotifications(sqsMessage));
    }

    @Test
    void processMemberAddedExceptionSkips() {
        String sqsMessage = "{\"detail-type\": \"MemberAdded\", \"detail\": {\"groupId\": \"g1\", \"email\": \"m@ex.com\"}}";
        when(restTemplate.getForObject(anyString(), eq(GroupDTO.class))).thenThrow(new org.springframework.web.client.RestClientException("error"));
        assertDoesNotThrow(() -> listener.listenToNotifications(sqsMessage));
    }

    @Test
    void markEmailAsBouncedExceptionLogsAndSkips() {
        String snsBouncePayload = "{" +
                "  \"Type\": \"Notification\"," +
                "  \"Message\": \"{\\\"notificationType\\\":\\\"Bounce\\\",\\\"bounce\\\":{\\\"bounceType\\\":\\\"Permanent\\\",\\\"bouncedRecipients\\\":[{\\\"emailAddress\\\":\\\"bad@example.com\\\"}]}}\"" +
                "}";
        doThrow(new org.springframework.web.client.RestClientException("error")).when(restTemplate).put(anyString(), any());
        assertDoesNotThrow(() -> listener.listenToBounces(snsBouncePayload));
    }
}
