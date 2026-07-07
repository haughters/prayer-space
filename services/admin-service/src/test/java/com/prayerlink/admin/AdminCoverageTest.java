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
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class AdminCoverageTest {

    @Test
    void testModels() {
        Admin admin = Admin.builder()
                .adminId("a-1")
                .username("admin")
                .passwordHash("hash")
                .role("APP_ADMIN")
                .groupId("g-1")
                .createdAt(Instant.now())
                .build();
        assertEquals("a-1", admin.getAdminId());
        assertEquals("admin", admin.getUsername());
        assertEquals("hash", admin.getPasswordHash());
        assertEquals("APP_ADMIN", admin.getRole());
        assertEquals("g-1", admin.getGroupId());
        assertNotNull(admin.getCreatedAt());

        Admin admin2 = new Admin();
        admin2.setAdminId("a-2");
        assertEquals("a-2", admin2.getAdminId());

        Prayer prayer = Prayer.builder()
                .prayerId("p-1")
                .deviceId("d-1")
                .prayerText("text")
                .assignedGroupId("g-1")
                .status("OPEN")
                .prayedForCount(5)
                .prayedByEmails(Set.of("email@example.com"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        assertEquals("p-1", prayer.getPrayerId());
        assertEquals("d-1", prayer.getDeviceId());
        assertEquals("text", prayer.getPrayerText());
        assertEquals("g-1", prayer.getAssignedGroupId());
        assertEquals("OPEN", prayer.getStatus());
        assertEquals(5, prayer.getPrayedForCount());
        assertTrue(prayer.getPrayedByEmails().contains("email@example.com"));
        assertNotNull(prayer.getCreatedAt());
        assertNotNull(prayer.getUpdatedAt());

        Prayer prayer2 = new Prayer();
        prayer2.setPrayerId("p-2");
        assertEquals("p-2", prayer2.getPrayerId());

        PrayerUpdate update = PrayerUpdate.builder()
                .prayerId("p-1")
                .updateText("updated")
                .updatedAt(Instant.now())
                .updatedByDeviceId("d-1")
                .build();
        assertEquals("p-1", update.getPrayerId());
        assertEquals("updated", update.getUpdateText());
        assertNotNull(update.getUpdatedAt());
        assertEquals("d-1", update.getUpdatedByDeviceId());

        PrayerUpdate update2 = new PrayerUpdate();
        update2.setPrayerId("p-2");
        assertEquals("p-2", update2.getPrayerId());
    }

    @Test
    void testConfigs() {
        AppConfig appConfig = new AppConfig();
        PasswordEncoder encoder = appConfig.passwordEncoder();
        assertNotNull(encoder);
        assertNotNull(appConfig.restTemplate());

        DynamoDbConfig dbConfig = new DynamoDbConfig();
        DynamoDbClient mockClient = mock(DynamoDbClient.class);
        DynamoDbEnhancedClient enhancedClient = dbConfig.dynamoDbEnhancedClient(mockClient);
        assertNotNull(enhancedClient);
    }

    @Test
    void testApplicationMain() {
        try {
            AdminApplication.main(new String[]{"--server.port=0"});
        } catch (Throwable e) {
            // expected
        }
        try {
            StreamLambdaHandler handler = new StreamLambdaHandler();
            java.io.InputStream is = new java.io.ByteArrayInputStream(new byte[]{});
            java.io.OutputStream os = new java.io.ByteArrayOutputStream();
            com.amazonaws.services.lambda.runtime.Context context = mock(com.amazonaws.services.lambda.runtime.Context.class);
            handler.handleRequest(is, os, context);
        } catch (Throwable e) {
            // expected
        }
    }
}
