package com.prayerlink.prayer.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class DynamoDbConfig {

    @Bean
    @Profile("local")
    public DynamoDbClient localDynamoDbClient(
            @Value("${aws.dynamodb.endpoint}") String endpoint,
            @Value("${aws.dynamodb.region:eu-west-1}") String region) {
        return DynamoDbClient.builder()
                .region(Region.of(region))
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
                .build();
    }

    @Bean
    @Profile("!local")
    public DynamoDbClient awsDynamoDbClient(@Value("${aws.dynamodb.region:eu-west-1}") String region) {
        return DynamoDbClient.builder().region(Region.of(region)).build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDbClient).build();
    }
}
