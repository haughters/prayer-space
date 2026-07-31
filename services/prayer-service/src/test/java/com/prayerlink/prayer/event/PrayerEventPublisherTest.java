package com.prayerlink.prayer.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prayerlink.prayer.service.event.PrayerEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class PrayerEventPublisherTest {

    @Mock
    private EventBridgeClient eventBridgeClient;

    @Mock
    private ObjectMapper objectMapper;

    private PrayerEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new PrayerEventPublisher(eventBridgeClient, objectMapper, "test-bus");
    }

    @Test
    void publishSuccessCallsEventBridge() throws Exception {
        Object detail = new Object();
        when(objectMapper.writeValueAsString(detail)).thenReturn("{\"key\":\"value\"}");

        PutEventsResponse mockResponse =
                PutEventsResponse.builder().failedEntryCount(0).build();
        when(eventBridgeClient.putEvents(any(PutEventsRequest.class))).thenReturn(mockResponse);

        publisher.publish("TestEvent", detail);

        ArgumentCaptor<PutEventsRequest> captor = ArgumentCaptor.forClass(PutEventsRequest.class);
        verify(eventBridgeClient).putEvents(captor.capture());

        PutEventsRequest captured = captor.getValue();
        assertEquals(1, captured.entries().size());
        assertEquals("TestEvent", captured.entries().get(0).detailType());
        assertEquals("com.prayerlink.prayer-service", captured.entries().get(0).source());
        assertEquals("test-bus", captured.entries().get(0).eventBusName());
        assertEquals("{\"key\":\"value\"}", captured.entries().get(0).detail());
    }

    @Test
    void publishHandlesSerializationFailure() throws Exception {
        Object detail = new Object();
        when(objectMapper.writeValueAsString(detail)).thenThrow(new RuntimeException("Serialization failed"));

        assertDoesNotThrow(() -> publisher.publish("TestEvent", detail));
    }

    @Test
    void publishHandlesEventBridgeFailure() throws Exception {
        Object detail = new Object();
        when(objectMapper.writeValueAsString(detail)).thenReturn("{}");
        when(eventBridgeClient.putEvents(any(PutEventsRequest.class)))
                .thenThrow(new RuntimeException("EventBridge unavailable"));

        assertDoesNotThrow(() -> publisher.publish("TestEvent", detail));
    }
}
