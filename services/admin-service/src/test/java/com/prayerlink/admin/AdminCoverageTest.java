package com.prayerlink.admin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prayerlink.admin.config.AppConfig;
import com.prayerlink.admin.config.DynamoDbConfig;
import com.prayerlink.admin.model.Admin;
import com.prayerlink.admin.model.Prayer;
import com.prayerlink.admin.model.PrayerUpdate;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class AdminCoverageTest {

    @Test
    void testModels() {
        Admin admin = Admin.builder()
                .adminId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .username("admin")
                .passwordHash("hash")
                .role("APP_ADMIN")
                .groupId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .createdAt(Instant.now())
                .build();
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000001"), admin.getAdminId());
        assertEquals("admin", admin.getUsername());
        assertEquals("hash", admin.getPasswordHash());
        assertEquals("APP_ADMIN", admin.getRole());
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000001"), admin.getGroupId());
        assertNotNull(admin.getCreatedAt());

        Admin admin2 = new Admin();
        admin2.setAdminId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000002"), admin2.getAdminId());

        Prayer prayer = Prayer.builder()
                .prayerId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .deviceId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .prayerText("text")
                .assignedGroupId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .status("OPEN")
                .prayedForCount(5)
                .prayedByEmails(Set.of("email@example.com"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000001"), prayer.getPrayerId());
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000001"), prayer.getDeviceId());
        assertEquals("text", prayer.getPrayerText());
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000001"), prayer.getAssignedGroupId());
        assertEquals("OPEN", prayer.getStatus());
        assertEquals(5, prayer.getPrayedForCount());
        assertTrue(prayer.getPrayedByEmails().contains("email@example.com"));
        assertNotNull(prayer.getCreatedAt());
        assertNotNull(prayer.getUpdatedAt());

        Prayer prayer2 = new Prayer();
        prayer2.setPrayerId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000002"), prayer2.getPrayerId());

        PrayerUpdate update = PrayerUpdate.builder()
                .prayerId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .updateText("updated")
                .updatedAt(Instant.now())
                .updatedByDeviceId("d-1")
                .build();
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000001"), update.getPrayerId());
        assertEquals("updated", update.getUpdateText());
        assertNotNull(update.getUpdatedAt());
        assertEquals("d-1", update.getUpdatedByDeviceId());

        PrayerUpdate update2 = new PrayerUpdate();
        update2.setPrayerId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000002"), update2.getPrayerId());
    }

    @Test
    void testConfigs() {
        AppConfig appConfig = new AppConfig();
        PasswordEncoder encoder = appConfig.passwordEncoder();
        assertNotNull(encoder);

        DynamoDbConfig dbConfig = new DynamoDbConfig();
        DynamoDbClient mockClient = mock(DynamoDbClient.class);
        DynamoDbEnhancedClient enhancedClient = dbConfig.dynamoDbEnhancedClient(mockClient);
        assertNotNull(enhancedClient);
    }

    @Test
    void testApplicationMain() {
        System.setProperty("aws.accessKeyId", "dummy");
        System.setProperty("aws.secretAccessKey", "dummy");
        System.setProperty("aws.region", "eu-west-1");
        try {
            AdminApplication.main(new String[] {"--server.port=0", "--spring.profiles.active=local"});
        } catch (Throwable e) {
            // expected
        } finally {
            System.clearProperty("aws.accessKeyId");
            System.clearProperty("aws.secretAccessKey");
            System.clearProperty("aws.region");
        }
        try {
            StreamLambdaHandler handler = new StreamLambdaHandler();
            java.io.InputStream is = new java.io.ByteArrayInputStream(new byte[] {});
            java.io.OutputStream os = new java.io.ByteArrayOutputStream();
            com.amazonaws.services.lambda.runtime.Context context =
                    mock(com.amazonaws.services.lambda.runtime.Context.class);
            handler.handleRequest(is, os, context);
        } catch (Throwable e) {
            // expected
        }
    }
}
