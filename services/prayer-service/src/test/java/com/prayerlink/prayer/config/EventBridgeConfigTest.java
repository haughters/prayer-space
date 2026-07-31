package com.prayerlink.prayer.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;

public class EventBridgeConfigTest {

    @Test
    void localEventBridgeClientCreatedWhenEndpointIsNull() {
        EventBridgeConfig config = new EventBridgeConfig();

        EventBridgeClient client = config.localEventBridgeClient(null, "eu-west-1");

        assertNotNull(client, "EventBridgeClient should be created successfully");
    }

    @Test
    void localEventBridgeClientCreatedWhenAllDetailsSupplied() {
        EventBridgeConfig config = new EventBridgeConfig();

        EventBridgeClient client = config.localEventBridgeClient("http://localhost:4566", "us-east-1");

        assertNotNull(client, "EventBridgeClient should be created successfully with endpoint override");
    }

    @Test
    void awsEventBridgeClientCreated() {
        EventBridgeConfig config = new EventBridgeConfig();

        EventBridgeClient client = config.awsEventBridgeClient("us-east-1");

        assertNotNull(config, "EventBridgeClient should be created successfully");
    }
}
