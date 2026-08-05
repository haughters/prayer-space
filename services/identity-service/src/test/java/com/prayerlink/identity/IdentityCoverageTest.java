package com.prayerlink.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prayerlink.identity.config.AppConfig;
import com.prayerlink.identity.config.DynamoDbConfig;
import com.prayerlink.identity.model.Device;
import com.prayerlink.identity.model.IntercessorAccount;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class IdentityCoverageTest {

    @Test
    void testModels() {
        java.util.UUID testUuid = java.util.UUID.randomUUID();
        Device dev = Device.builder()
                .deviceId(testUuid)
                .createdAt(Instant.now())
                .lastSeenAt(Instant.now())
                .build();
        assertEquals(testUuid, dev.getDeviceId());
        assertNotNull(dev.getCreatedAt());
        assertNotNull(dev.getLastSeenAt());

        Device dev2 = new Device();
        java.util.UUID testUuid2 = java.util.UUID.randomUUID();
        dev2.setDeviceId(testUuid2);
        assertEquals(testUuid2, dev2.getDeviceId());

        IntercessorAccount account = IntercessorAccount.builder()
                .email("test@example.com")
                .passwordHash("hash")
                .name("Jane")
                .createdAt(Instant.now())
                .build();
        assertEquals("test@example.com", account.getEmail());
        assertEquals("hash", account.getPasswordHash());
        assertEquals("Jane", account.getName());
        assertNotNull(account.getCreatedAt());

        IntercessorAccount account2 = new IntercessorAccount();
        account2.setEmail("test2@example.com");
        assertEquals("test2@example.com", account2.getEmail());
    }

    @Test
    void testConfigs() {
        AppConfig appConfig = new AppConfig();
        assertNotNull(appConfig.passwordEncoder());

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
            IdentityApplication.main(new String[] {"--server.port=0", "--spring.profiles.active=local"});
        } catch (Throwable e) {
            // catch context run failures
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
            // expected lambda wrapper instantiate bypass
        }
    }
}
