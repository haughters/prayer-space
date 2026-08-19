package com.prayerlink.notification.filter;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.prayerlink.notification.listener.NotificationListener;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class SqsLambdaEventFilterTest {

    @Mock
    private NotificationListener notificationListener;

    @Mock
    private FilterChain filterChain;

    private SqsLambdaEventFilter filter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        filter = new SqsLambdaEventFilter(notificationListener, objectMapper);
    }

    @Test
    void doFilter_withSqsNotificationRecord_dispatchesToListenToNotifications() throws Exception {
        String sqsPayload = """
                {
                    "Records": [
                        {
                            "messageId": "msg-1",
                            "body": "{\\"detail-type\\":\\"MemberAdded\\",\\"detail\\":{\\"email\\":\\"test@example.com\\"}}",
                            "eventSourceARN": "arn:aws:sqs:eu-west-1:123456789012:test-notification-queue"
                        }
                    ]
                }
                """;

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(sqsPayload.getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(notificationListener, times(1)).listenToNotifications(anyString());
        verify(filterChain, never()).doFilter(request, response);
        assert response.getStatus() == 200;
        assert response.getContentAsString().contains("batchItemFailures");
    }

    @Test
    void doFilter_withSqsBounceRecord_dispatchesToListenToBounces() throws Exception {
        String sqsPayload = """
                {
                    "Records": [
                        {
                            "messageId": "msg-2",
                            "body": "{\\"notificationType\\":\\"Bounce\\"}",
                            "eventSourceARN": "arn:aws:sqs:eu-west-1:123456789012:test-bounce-queue"
                        }
                    ]
                }
                """;

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(sqsPayload.getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(notificationListener, times(1)).listenToBounces(anyString());
        verify(filterChain, never()).doFilter(request, response);
        assert response.getStatus() == 200;
    }

    @Test
    void doFilter_withStandardHttpRequest_passesThroughFilterChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.setContent(new byte[0]);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(notificationListener, never()).listenToNotifications(anyString());
        verify(notificationListener, never()).listenToBounces(anyString());
        verify(filterChain, times(1)).doFilter(request, response);
    }
}
