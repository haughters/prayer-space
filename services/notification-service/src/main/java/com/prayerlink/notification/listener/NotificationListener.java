package com.prayerlink.notification.listener;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prayerlink.common.dto.GroupDTO;
import com.prayerlink.common.dto.GroupMemberDTO;
import com.prayerlink.common.dto.PrayerDTO;
import com.prayerlink.common.event.PrayerCreatedEvent;
import com.prayerlink.common.event.PrayerUpdatedEvent;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Component
public class NotificationListener {

  private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

  private final RestTemplate restTemplate;
  private final SesClient sesClient;
  private final ObjectMapper objectMapper;

  @Value("${services.group-service.url:http://localhost:8083}")
  private String groupServiceUrl;

  @Value("${services.prayer-service.url:http://localhost:8082}")
  private String prayerServiceUrl;

  @Value("${app.domain:localhost:5173}")
  private String appDomain;

  @Value("${hmac.secret-key:default-secret-key-change-me-in-production}")
  private String hmacSecretKey;

  public NotificationListener(
      RestTemplate restTemplate, SesClient sesClient, ObjectMapper objectMapper) {
    this.restTemplate = restTemplate;
    this.sesClient = sesClient;
    this.objectMapper = objectMapper;
  }

  @SqsListener("notification-queue")
  public void listenToNotifications(String messageBody) {
    log.info("Received message from notification-queue: {}", messageBody);
    try {
      JsonNode envelope = objectMapper.readTree(messageBody);
      String detailType = envelope.path("detail-type").asText();
      JsonNode detailNode = envelope.path("detail");

      if ("PrayerCreated".equals(detailType) || "PRAYER_CREATED".equals(detailType)) {
        PrayerCreatedEvent event = objectMapper.treeToValue(detailNode, PrayerCreatedEvent.class);
        processPrayerCreated(event);
      } else if ("PrayerUpdated".equals(detailType) || "PRAYER_UPDATED".equals(detailType)) {
        PrayerUpdatedEvent event = objectMapper.treeToValue(detailNode, PrayerUpdatedEvent.class);
        processPrayerUpdated(event);
      } else if ("MemberAdded".equals(detailType) || "MEMBER_ADDED".equals(detailType)) {
        com.prayerlink.common.event.MemberAddedEvent event = objectMapper.treeToValue(detailNode, com.prayerlink.common.event.MemberAddedEvent.class);
        processMemberAdded(event);
      } else {
        log.warn("Unknown detail-type received: {}", detailType);
      }
    } catch (Exception e) {
      log.error("Failed to process message: {}", messageBody, e);
      throw new RuntimeException(e);
    }
  }

  @SqsListener("bounce-queue")
  public void listenToBounces(String messageBody) {
    log.info("Received message from bounce-queue: {}", messageBody);
    try {
      JsonNode envelope = objectMapper.readTree(messageBody);
      
      // Check if it is an SNS envelope
      String type = envelope.path("Type").asText();
      String actualMessage = messageBody;
      if ("Notification".equals(type) && envelope.has("Message")) {
        actualMessage = envelope.path("Message").asText();
      }
      
      JsonNode bounceNode = objectMapper.readTree(actualMessage);
      String notificationType = bounceNode.path("notificationType").asText();
      
      if ("Bounce".equals(notificationType)) {
        String bounceType = bounceNode.path("bounce").path("bounceType").asText();
        if ("Transient".equalsIgnoreCase(bounceType)) {
          log.info("Ignoring transient bounce notification: {}", actualMessage);
          return;
        }
        
        JsonNode bouncedRecipients = bounceNode.path("bounce").path("bouncedRecipients");
        if (bouncedRecipients.isArray()) {
          for (JsonNode recipient : bouncedRecipients) {
            String emailAddress = recipient.path("emailAddress").asText();
            if (emailAddress != null && !emailAddress.trim().isEmpty()) {
              log.info("Processing bounce for email: {}", emailAddress);
              markEmailAsBounced(emailAddress);
            }
          }
        }
      } else {
        log.warn("Received non-bounce notification from SNS: {}", notificationType);
      }
    } catch (Exception e) {
      log.error("Failed to process bounce message: {}", messageBody, e);
    }
  }

  private void processPrayerCreated(PrayerCreatedEvent event) {
    String assignedGroupId = event.getAssignedGroupId();
    if (assignedGroupId == null) {
      log.warn("PrayerCreatedEvent has no assignedGroupId, skipping email notification. Prayer ID: {}", event.getPrayerId());
      return;
    }

    // Fetch Group details
    GroupDTO group;
    try {
      group = restTemplate.getForObject(groupServiceUrl + "/api/groups/" + assignedGroupId, GroupDTO.class);
    } catch (Exception e) {
      log.error("Failed to fetch group info for ID: {}", assignedGroupId, e);
      return;
    }

    if (group == null) {
      log.warn("Group not found for ID: {}, skipping notification.", assignedGroupId);
      return;
    }

    // Fetch Group Members
    List<GroupMemberDTO> members;
    try {
      ResponseEntity<List<GroupMemberDTO>> response = restTemplate.exchange(
          groupServiceUrl + "/api/groups/" + assignedGroupId + "/members",
          HttpMethod.GET,
          null,
          new ParameterizedTypeReference<List<GroupMemberDTO>>() {});
      members = response.getBody();
    } catch (Exception e) {
      log.error("Failed to fetch members for group ID: {}", assignedGroupId, e);
      return;
    }

    if (members == null || members.isEmpty()) {
      log.warn("No members in group ID: {}, skipping notification.", assignedGroupId);
      return;
    }

    // Filter out bounced members
    List<GroupMemberDTO> eligibleMembers = members.stream()
        .filter(m -> m.getBounced() == null || !m.getBounced())
        .toList();

    if (eligibleMembers.isEmpty()) {
      log.warn("No non-bounced members in group ID: {}, skipping notification.", assignedGroupId);
      return;
    }

    long expiryTimestamp = Instant.now().plus(30, ChronoUnit.DAYS).getEpochSecond();

    for (GroupMemberDTO member : eligibleMembers) {
      try {
        // Generate Token
        // payload = "{groupId}:{intercessorEmail}:{expiryTimestamp}"
        String payloadStr = assignedGroupId + ":" + member.getEmail() + ":" + expiryTimestamp;
        String signature = computeHmac(payloadStr, hmacSecretKey);
        String token = signature + "|" + assignedGroupId + "|" + expiryTimestamp;

        String scheme = appDomain.startsWith("localhost") ? "http://" : "https://";
        String actionUrl = scheme + appDomain + "/pray/" + event.getPrayerId() + "/" + token;

        sendCreatedEmail(member.getEmail(), group.getName(), event.getPrayerText(), actionUrl);
      } catch (Exception e) {
        log.error("Failed to send prayer request email to member: {}", member.getEmail(), e);
      }
    }
  }

  private void processPrayerUpdated(PrayerUpdatedEvent event) {
    // Fetch the prayer details to find assignedGroupId
    PrayerDTO prayer;
    try {
      prayer = restTemplate.getForObject(prayerServiceUrl + "/api/prayers/" + event.getPrayerId(), PrayerDTO.class);
    } catch (Exception e) {
      log.error("Failed to fetch prayer details for ID: {}", event.getPrayerId(), e);
      return;
    }

    if (prayer == null || prayer.getAssignedGroupId() == null) {
      log.warn("Prayer details not found or has no assigned group. Prayer ID: {}", event.getPrayerId());
      return;
    }

    String assignedGroupId = prayer.getAssignedGroupId();

    // Fetch Group details
    GroupDTO group;
    try {
      group = restTemplate.getForObject(groupServiceUrl + "/api/groups/" + assignedGroupId, GroupDTO.class);
    } catch (Exception e) {
      log.error("Failed to fetch group info for ID: {}", assignedGroupId, e);
      return;
    }

    if (group == null) {
      log.warn("Group not found for ID: {}, skipping notification.", assignedGroupId);
      return;
    }

    // Fetch Group Members
    List<GroupMemberDTO> members;
    try {
      ResponseEntity<List<GroupMemberDTO>> response = restTemplate.exchange(
          groupServiceUrl + "/api/groups/" + assignedGroupId + "/members",
          HttpMethod.GET,
          null,
          new ParameterizedTypeReference<List<GroupMemberDTO>>() {});
      members = response.getBody();
    } catch (Exception e) {
      log.error("Failed to fetch members for group ID: {}", assignedGroupId, e);
      return;
    }

    if (members == null || members.isEmpty()) {
      log.warn("No members in group ID: {}, skipping notification.", assignedGroupId);
      return;
    }

    List<GroupMemberDTO> eligibleMembers = members.stream()
        .filter(m -> m.getBounced() == null || !m.getBounced())
        .toList();

    for (GroupMemberDTO member : eligibleMembers) {
      try {
        sendUpdatedEmail(member.getEmail(), group.getName(), prayer.getPrayerText(), event.getUpdateText());
      } catch (Exception e) {
        log.error("Failed to send update email to member: {}", member.getEmail(), e);
      }
    }
  }

  private void sendCreatedEmail(String recipientEmail, String groupName, String prayerText, String actionUrl) {
    String subject = "Someone Needs Your Prayers";
    String fromEmail = "prayers@prayer-link.org";
    String scheme = appDomain.startsWith("localhost") ? "http://" : "https://";
    String portalUrl = scheme + appDomain + "/intercessor.html#login";

    String htmlBody = "<div style=\"max-width: 600px; margin: 0 auto; font-family: Inter, sans-serif; color: #1a1a2e;\">\n" +
        "  <h2 style=\"color: #d4a574;\">A Prayer Request for Your Group</h2>\n" +
        "  <p>A prayer request has been shared with <strong>" + groupName + "</strong>.</p>\n" +
        "  <div style=\"background: #f8f8fc; border-left: 4px solid #d4a574; padding: 16px 20px; margin: 24px 0; border-radius: 0 8px 8px 0;\">\n" +
        "    <p style=\"margin: 0; line-height: 1.6; white-space: pre-wrap;\">" + prayerText + "</p>\n" +
        "  </div>\n" +
        "  <div style=\"text-align: center; margin: 32px 0;\">\n" +
        "    <a href=\"" + actionUrl + "\" style=\"display: inline-block; background: #d4a574; color: #1a1a2e; padding: 16px 40px; border-radius: 9999px; text-decoration: none; font-weight: 600; font-size: 16px;\">\n" +
        "      View Prayer & Pray 🙏\n" +
        "    </a>\n" +
        "    <p style=\"margin-top: 16px; font-size: 14px;\">\n" +
        "      Or <a href=\"" + portalUrl + "\" style=\"color: #d4a574; text-decoration: underline;\">log in to your portal</a> to view all group prayers.\n" +
        "    </p>\n" +
        "  </div>\n" +
        "  <hr style=\"border: none; border-top: 1px solid #eee; margin: 32px 0;\">\n" +
        "  <p style=\"font-size: 12px; color: #888;\">\n" +
        "    You're receiving this because you're a member of " + groupName + ".<br>\n" +
        "    To unsubscribe, contact your group administrator.\n" +
        "  </p>\n" +
        "</div>";

    String textBody = "A Prayer Request for Your Group\n\n" +
        "A prayer request has been shared with " + groupName + ":\n\n" +
        "\"" + prayerText + "\"\n\n" +
        "To view the prayer and pray for it, visit: " + actionUrl + "\n\n" +
        "Or log in to your Prayer Link portal to view all prayers: " + portalUrl + "\n\n" +
        "---\n" +
        "You're receiving this because you're a member of " + groupName + ".\n" +
        "To unsubscribe, contact your group administrator.";

    sendEmail(recipientEmail, subject, htmlBody, textBody, fromEmail);
  }

  private void sendUpdatedEmail(String recipientEmail, String groupName, String prayerText, String updateText) {
    String subject = "Prayer Update: An Answer to Share";
    String fromEmail = "prayers@prayer-link.org";
    String scheme = appDomain.startsWith("localhost") ? "http://" : "https://";
    String portalUrl = scheme + appDomain + "/intercessor.html#login";

    String htmlBody = "<div style=\"max-width: 600px; margin: 0 auto; font-family: Inter, sans-serif; color: #1a1a2e;\">\n" +
        "  <h2 style=\"color: #d4a574;\">A Prayer Update</h2>\n" +
        "  <p>An update has been shared for a prayer request in <strong>" + groupName + "</strong>.</p>\n" +
        "  <h3>Original Prayer</h3>\n" +
        "  <div style=\"background: #f8f8fc; border-left: 4px solid #ccc; padding: 16px 20px; margin: 16px 0; border-radius: 0 8px 8px 0;\">\n" +
        "    <p style=\"margin: 0; line-height: 1.6; white-space: pre-wrap;\">" + prayerText + "</p>\n" +
        "  </div>\n" +
        "  <h3>Update</h3>\n" +
        "  <div style=\"background: #f0fdf4; border-left: 4px solid #4ade80; padding: 16px 20px; margin: 16px 0; border-radius: 0 8px 8px 0;\">\n" +
        "    <p style=\"margin: 0; line-height: 1.6; white-space: pre-wrap;\">" + updateText + "</p>\n" +
        "  </div>\n" +
        "  <p><em>This prayer request has been closed.</em></p>\n" +
        "  <p style=\"font-size: 14px;\">\n" +
        "    Log in to your <a href=\"" + portalUrl + "\" style=\"color: #d4a574; text-decoration: underline;\">Prayer Link portal</a> to view all other requests.\n" +
        "  </p>\n" +
        "  <hr style=\"border: none; border-top: 1px solid #eee; margin: 32px 0;\">\n" +
        "  <p style=\"font-size: 12px; color: #888;\">\n" +
        "    You're receiving this because you're a member of " + groupName + ".<br>\n" +
        "    To unsubscribe, contact your group administrator.\n" +
        "  </p>\n" +
        "</div>";

    String textBody = "A Prayer Update\n\n" +
        "An update has been shared for a prayer request in " + groupName + ".\n\n" +
        "Original Prayer:\n" +
        "\"" + prayerText + "\"\n\n" +
        "Update:\n" +
        "\"" + updateText + "\"\n\n" +
        "This prayer request has been closed.\n\n" +
        "Log in to your Prayer Link portal to view all other requests: " + portalUrl + "\n\n" +
        "---\n" +
        "You're receiving this because you're a member of " + groupName + ".\n" +
        "To unsubscribe, contact your group administrator.";

    sendEmail(recipientEmail, subject, htmlBody, textBody, fromEmail);
  }

  private void sendEmail(String recipientEmail, String subject, String htmlBody, String textBody, String fromEmail) {
    try {
      SendEmailRequest request = SendEmailRequest.builder()
          .destination(Destination.builder().toAddresses(recipientEmail).build())
          .message(Message.builder()
              .subject(Content.builder().data(subject).build())
              .body(Body.builder()
                  .html(Content.builder().data(htmlBody).build())
                  .text(Content.builder().data(textBody).build())
                  .build())
              .build())
          .source(fromEmail)
          .build();

      log.info("Dispatching email via SES to {}", recipientEmail);
      SendEmailResponse response = sesClient.sendEmail(request);
      log.info("Email sent successfully to {}. MessageId: {}", recipientEmail, response.messageId());
    } catch (Exception e) {
      log.error("Failed to send email to {} via SES", recipientEmail, e);
      throw e;
    }
  }

  private void markEmailAsBounced(String email) {
    try {
      restTemplate.put(groupServiceUrl + "/api/groups/members/bounce", Map.of("email", email));
      log.info("Successfully marked email as bounced in group-service: {}", email);
    } catch (Exception e) {
      log.error("Failed to mark email as bounced in group-service: {}", email, e);
    }
  }

  private void processMemberAdded(com.prayerlink.common.event.MemberAddedEvent event) {
    try {
      // Fetch Group details to get group name
      GroupDTO group = restTemplate.getForObject(groupServiceUrl + "/api/groups/" + event.getGroupId(), GroupDTO.class);
      if (group == null) {
        log.warn("Group not found for ID: {}, skipping invitation.", event.getGroupId());
        return;
      }
      
      String scheme = appDomain.startsWith("localhost") ? "http://" : "https://";
      String actionUrl = scheme + appDomain + "/intercessor.html#register?email=" + java.net.URLEncoder.encode(event.getEmail(), "UTF-8");
      
      sendInvitationEmail(event.getEmail(), group.getName(), event.getName(), actionUrl);
    } catch (Exception e) {
      log.error("Failed to process member added event: {}", event.getEmail(), e);
    }
  }

  private void sendInvitationEmail(String recipientEmail, String groupName, String intercessorName, String actionUrl) {
    String subject = "You're Invited to Join Prayer Link";
    String fromEmail = "prayers@prayer-link.org";

    String htmlBody = "<div style=\"max-width: 600px; margin: 0 auto; font-family: Inter, sans-serif; color: #1a1a2e;\">\n" +
        "  <h2 style=\"color: #d4a574;\">Welcome to Prayer Link</h2>\n" +
        "  <p>Hello " + intercessorName + ",</p>\n" +
        "  <p>You have been added as an intercessor for the group <strong>" + groupName + "</strong> on Prayer Link.</p>\n" +
        "  <p>To view prayer requests and share your support, please register your account by clicking the link below:</p>\n" +
        "  <div style=\"text-align: center; margin: 32px 0;\">\n" +
        "    <a href=\"" + actionUrl + "\" style=\"display: inline-block; background: #d4a574; color: #1a1a2e; padding: 16px 40px; border-radius: 9999px; text-decoration: none; font-weight: 600; font-size: 16px;\">\n" +
        "      Join & Register Account 🙏\n" +
        "    </a>\n" +
        "  </div>\n" +
        "  <hr style=\"border: none; border-top: 1px solid #eee; margin: 32px 0;\">\n" +
        "  <p style=\"font-size: 12px; color: #888;\">\n" +
        "    If you didn't expect this invitation, you can ignore this email.\n" +
        "  </p>\n" +
        "</div>";

    String textBody = "Welcome to Prayer Link\n\n" +
        "Hello " + intercessorName + ",\n\n" +
        "You have been added as an intercessor for the group " + groupName + " on Prayer Link.\n\n" +
        "To view prayer requests and share your support, please register your account at:\n" +
        actionUrl + "\n\n" +
        "---\n" +
        "If you didn't expect this invitation, you can ignore this email.";

    sendEmail(recipientEmail, subject, htmlBody, textBody, fromEmail);
  }

  private String computeHmac(String data, String secretKey) {
    try {
      javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
      mac.init(new javax.crypto.spec.SecretKeySpec(secretKey.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] rawHmac = mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
    } catch (Exception e) {
      throw new RuntimeException("Failed to compute HMAC", e);
    }
  }
}
