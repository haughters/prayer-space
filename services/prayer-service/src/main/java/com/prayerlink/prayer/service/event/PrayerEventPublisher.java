package com.prayerlink.prayer.service.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Publishes domain events to AWS EventBridge. Failures are logged but never propagated — events are
 * fire-and-forget.
 */
@Slf4j
@Component
public class PrayerEventPublisher {

    private final EventBridgeClient eventBridgeClient;
    private final ObjectMapper objectMapper;
    private final String eventBusName;

    public PrayerEventPublisher(
            EventBridgeClient eventBridgeClient,
            ObjectMapper objectMapper,
            @Value("${aws.eventbridge.bus}") String eventBusName) {
        this.eventBridgeClient = eventBridgeClient;
        this.objectMapper = objectMapper;
        this.eventBusName = eventBusName;
    }

    public void publish(String detailType, Object detail) {
        try {
            String detailJson = objectMapper.writeValueAsString(detail);
            PutEventsRequestEntry entry = PutEventsRequestEntry.builder()
                    .source("com.prayerlink.prayer-service")
                    .detailType(detailType)
                    .detail(detailJson)
                    .eventBusName(eventBusName)
                    .build();

            PutEventsRequest request = PutEventsRequest.builder().entries(entry).build();

            PutEventsResponse response = eventBridgeClient.putEvents(request);
            log.info(
                    "Published event {} to EventBridge. Response status: {}",
                    detailType,
                    response.sdkHttpResponse().statusCode());
        } catch (Exception e) {
            log.error("Failed to publish event to EventBridge: {}", detailType, e);
        }
    }
}
