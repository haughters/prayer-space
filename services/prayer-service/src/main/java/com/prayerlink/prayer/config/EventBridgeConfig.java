package com.prayerlink.prayer.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeClientBuilder;

@Configuration
public class EventBridgeConfig {

    @Bean
    @Profile("local")
    public EventBridgeClient localEventBridgeClient(
            @Value("${aws.eventbridge.endpoint}") String endpoint,
            @Value("${aws.eventbridge.region:eu-west-1}") String region) {
        EventBridgeClientBuilder builder = EventBridgeClient.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
                .region(Region.of(region));
        if (endpoint != null && !endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Bean
    @Profile("!local")
    public EventBridgeClient awsEventBridgeClient(@Value("${aws.eventbridge.region:eu-west-1}") String region) {
        return EventBridgeClient.builder().region(Region.of(region)).build();
    }
}
