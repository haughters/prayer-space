package com.prayerlink.notification.listener;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.prayerlink.common.dto.GroupDTO;
import com.prayerlink.common.dto.GroupMemberDTO;
import com.prayerlink.common.dto.PrayerDTO;
import com.prayerlink.common.enums.PrayerStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
public class NotificationListenerTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private SesClient sesClient;

    private ObjectMapper objectMapper;
    private NotificationListener listener;

    private final UUID groupId = UUID.randomUUID();
    private final UUID prayerId = UUID.randomUUID();
    private final String secretKey = "test-secret-key-1234567890123456";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        listener = new NotificationListener(restTemplate, sesClient, objectMapper);

        ReflectionTestUtils.setField(listener, "groupServiceUrl", "http://localhost:8083");
        ReflectionTestUtils.setField(listener, "prayerServiceUrl", "http://localhost:8082");
        ReflectionTestUtils.setField(listener, "appDomain", "localhost:5173");
        ReflectionTestUtils.setField(listener, "fromEmail", "test-sender@example.com");
        ReflectionTestUtils.setField(listener, "hmacSecretKey", secretKey);
    }

    @Test
    void testListenToPrayerCreatedEvent_Success() {
        GroupDTO group =
                GroupDTO.builder().groupId(groupId).name("Intercessors").build();
        GroupMemberDTO member = GroupMemberDTO.builder()
                .email("intercessor@example.com")
                .name("John")
                .bounced(false)
                .build();

        when(restTemplate.getForObject(contains("/api/groups/" + groupId), eq(GroupDTO.class)))
                .thenReturn(group);
        when(restTemplate.exchange(
                        contains("/api/groups/" + groupId + "/members"),
                        eq(HttpMethod.GET),
                        isNull(),
                        any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(List.of(member)));
        when(sesClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("msg-123").build());

        String message = String.format(
                "{\"detail-type\":\"PrayerCreated\",\"detail\":{\"prayerId\":\"%s\",\"prayerText\":\"Please pray for peace\",\"assignedGroupId\":\"%s\"}}",
                prayerId, groupId);

        listener.listenToNotifications(message);

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(captor.capture());
        SendEmailRequest sent = captor.getValue();
        assertEquals("intercessor@example.com", sent.destination().toAddresses().get(0));
        assertEquals("Someone Needs Your Prayers", sent.message().subject().data());
        assertTrue(sent.message().body().html().data().contains("Intercessors"));
    }

    @Test
    void testListenToPrayerCreatedEvent_MissingAssignedGroup() {
        String message = String.format(
                "{\"detail-type\":\"PrayerCreated\",\"detail\":{\"prayerId\":\"%s\",\"prayerText\":\"Please pray\"}}",
                prayerId);

        listener.listenToNotifications(message);
        verifyNoInteractions(sesClient);
    }

    @Test
    void testListenToPrayerCreatedEvent_GroupNotFound() {
        when(restTemplate.getForObject(anyString(), eq(GroupDTO.class))).thenReturn(null);

        String message = String.format(
                "{\"detail-type\":\"PrayerCreated\",\"detail\":{\"prayerId\":\"%s\",\"prayerText\":\"Please pray\",\"assignedGroupId\":\"%s\"}}",
                prayerId, groupId);

        listener.listenToNotifications(message);
        verifyNoInteractions(sesClient);
    }

    @Test
    void testListenToPrayerCreatedEvent_GroupFetchThrows() {
        when(restTemplate.getForObject(anyString(), eq(GroupDTO.class)))
                .thenThrow(new RestClientException("Connection error"));

        String message = String.format(
                "{\"detail-type\":\"PrayerCreated\",\"detail\":{\"prayerId\":\"%s\",\"prayerText\":\"Please pray\",\"assignedGroupId\":\"%s\"}}",
                prayerId, groupId);

        listener.listenToNotifications(message);
        verifyNoInteractions(sesClient);
    }

    @Test
    void testListenToPrayerCreatedEvent_NoEligibleMembers() {
        GroupDTO group = GroupDTO.builder().groupId(groupId).name("Group").build();
        GroupMemberDTO bounced =
                GroupMemberDTO.builder().email("b@ex.com").bounced(true).build();

        when(restTemplate.getForObject(anyString(), eq(GroupDTO.class))).thenReturn(group);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(List.of(bounced)));

        String message = String.format(
                "{\"detail-type\":\"PrayerCreated\",\"detail\":{\"prayerId\":\"%s\",\"prayerText\":\"Please pray\",\"assignedGroupId\":\"%s\"}}",
                prayerId, groupId);

        listener.listenToNotifications(message);
        verifyNoInteractions(sesClient);
    }

    @Test
    void testListenToPrayerUpdatedEvent_Success() {
        PrayerDTO prayer = PrayerDTO.builder()
                .prayerId(prayerId)
                .assignedGroupId(groupId)
                .prayerText("Original prayer")
                .status(PrayerStatus.CLOSED)
                .build();
        GroupDTO group =
                GroupDTO.builder().groupId(groupId).name("Healing Ministry").build();
        GroupMemberDTO member = GroupMemberDTO.builder()
                .email("jane@example.com")
                .name("Jane")
                .bounced(false)
                .build();

        when(restTemplate.getForObject(contains("/api/prayers/" + prayerId), eq(PrayerDTO.class)))
                .thenReturn(prayer);
        when(restTemplate.getForObject(contains("/api/groups/" + groupId), eq(GroupDTO.class)))
                .thenReturn(group);
        when(restTemplate.exchange(
                        contains("/api/groups/" + groupId + "/members"),
                        eq(HttpMethod.GET),
                        isNull(),
                        any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(List.of(member)));
        when(sesClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("msg-456").build());

        String message = String.format(
                "{\"detail-type\":\"PrayerUpdated\",\"detail\":{\"prayerId\":\"%s\",\"updateText\":\"Prayer answered!\"}}",
                prayerId);

        listener.listenToNotifications(message);

        verify(sesClient).sendEmail(any(SendEmailRequest.class));
    }

    @Test
    void testListenToPrayerUpdatedEvent_PrayerFetchThrows() {
        when(restTemplate.getForObject(contains("/api/prayers/"), eq(PrayerDTO.class)))
                .thenThrow(new RestClientException("Prayer service unavailable"));

        String message = String.format(
                "{\"detail-type\":\"PrayerUpdated\",\"detail\":{\"prayerId\":\"%s\",\"updateText\":\"Update\"}}",
                prayerId);

        listener.listenToNotifications(message);
        verifyNoInteractions(sesClient);
    }

    @Test
    void testListenToMemberAddedEvent_Success() {
        GroupDTO group =
                GroupDTO.builder().groupId(groupId).name("Young Adults").build();

        when(restTemplate.getForObject(contains("/api/groups/" + groupId), eq(GroupDTO.class)))
                .thenReturn(group);
        when(sesClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("msg-789").build());

        String message = String.format(
                "{\"detail-type\":\"MemberAdded\",\"detail\":{\"groupId\":\"%s\",\"memberId\":\"%s\",\"email\":\"newmember@example.com\",\"name\":\"Alice\"}}",
                groupId, UUID.randomUUID());

        listener.listenToNotifications(message);

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(captor.capture());
        SendEmailRequest sent = captor.getValue();
        assertEquals("newmember@example.com", sent.destination().toAddresses().get(0));
        assertEquals(
                "You're Invited to Join Prayer Link", sent.message().subject().data());
        assertTrue(sent.message().body().html().data().contains("Young Adults"));
    }

    @Test
    void testListenToMemberAddedEvent_GroupNotFound() {
        when(restTemplate.getForObject(anyString(), eq(GroupDTO.class))).thenReturn(null);

        String message = String.format(
                "{\"detail-type\":\"MemberAdded\",\"detail\":{\"groupId\":\"%s\",\"memberId\":\"%s\",\"email\":\"m@ex.com\",\"name\":\"Bob\"}}",
                groupId, UUID.randomUUID());

        listener.listenToNotifications(message);
        verifyNoInteractions(sesClient);
    }

    @Test
    void testListenToNotifications_UnknownDetailType() {
        String message = "{\"detail-type\":\"UnknownEvent\",\"detail\":{}}";
        listener.listenToNotifications(message);
        verifyNoInteractions(sesClient);
    }

    @Test
    void testListenToNotifications_InvalidJsonThrows() {
        assertThrows(RuntimeException.class, () -> listener.listenToNotifications("invalid json {{{"));
    }

    @Test
    void testListenToBounces_PermanentBounce() {
        String bouncePayload = "{\n" + "  \"notificationType\": \"Bounce\",\n"
                + "  \"bounce\": {\n"
                + "    \"bounceType\": \"Permanent\",\n"
                + "    \"bouncedRecipients\": [\n"
                + "      {\"emailAddress\": \"bad-email@example.com\"}\n"
                + "    ]\n"
                + "  }\n"
                + "}";

        listener.listenToBounces(bouncePayload);
        verify(restTemplate)
                .put(contains("/api/groups/members/bounce"), eq(java.util.Map.of("email", "bad-email@example.com")));
    }

    @Test
    void testListenToBounces_SnsWrappedBounce() {
        String rawBounce =
                "{\"notificationType\":\"Bounce\",\"bounce\":{\"bounceType\":\"Permanent\",\"bouncedRecipients\":[{\"emailAddress\":\"bounced@example.com\"}]}}";
        String snsPayload = "{\n" + "  \"Type\": \"Notification\",\n"
                + "  \"Message\": \""
                + rawBounce.replace("\"", "\\\"") + "\"\n" + "}";

        listener.listenToBounces(snsPayload);
        verify(restTemplate)
                .put(contains("/api/groups/members/bounce"), eq(java.util.Map.of("email", "bounced@example.com")));
    }

    @Test
    void testListenToBounces_TransientBounceIgnored() {
        String bouncePayload = "{\n" + "  \"notificationType\": \"Bounce\",\n"
                + "  \"bounce\": {\n"
                + "    \"bounceType\": \"Transient\",\n"
                + "    \"bouncedRecipients\": [\n"
                + "      {\"emailAddress\": \"temp-fail@example.com\"}\n"
                + "    ]\n"
                + "  }\n"
                + "}";

        listener.listenToBounces(bouncePayload);
        verifyNoInteractions(restTemplate);
    }
}
