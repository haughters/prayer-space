package com.prayerlink.prayer.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeClientBuilder;

@Configuration
public class EventBridgeConfig {

  @Value("${aws.eventbridge.endpoint:#{null}}")
  private String endpoint;

  @Value("${aws.eventbridge.region:us-east-1}")
  private String region;

  @Bean
  public EventBridgeClient eventBridgeClient() {
    EventBridgeClientBuilder builder = EventBridgeClient.builder().region(Region.of(region));

    if (endpoint != null && !endpoint.isEmpty()) {
      builder
          .endpointOverride(URI.create(endpoint))
          .credentialsProvider(
              StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")));
    }
    return builder.build();
  }
}
