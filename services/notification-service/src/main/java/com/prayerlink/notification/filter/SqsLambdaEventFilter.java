package com.prayerlink.notification.filter;

import com.prayerlink.notification.listener.NotificationListener;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SqsLambdaEventFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(SqsLambdaEventFilter.class);

    private final NotificationListener notificationListener;
    private final ObjectMapper objectMapper;

    public SqsLambdaEventFilter(NotificationListener notificationListener, ObjectMapper objectMapper) {
        this.notificationListener = notificationListener;
        this.objectMapper = objectMapper;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // If HTTP method is null or empty, this is a non-HTTP Lambda invocation (e.g. SQS Event Source Mapping)
        if (httpRequest.getMethod() == null || httpRequest.getMethod().isBlank()) {
            log.info("SqsLambdaEventFilter: Intercepting non-HTTP Lambda event (null HTTP method)");
            try {
                byte[] bodyBytes = httpRequest.getInputStream().readAllBytes();
                if (bodyBytes != null && bodyBytes.length > 0) {
                    JsonNode root = objectMapper.readTree(bodyBytes);
                    if (root != null
                            && root.has("Records")
                            && root.path("Records").isArray()
                            && !root.path("Records").isEmpty()) {
                        log.info(
                                "SqsLambdaEventFilter: Processing SQS event with {} records",
                                root.path("Records").size());
                        for (JsonNode record : root.path("Records")) {
                            String body = record.path("body").asText();
                            String eventSourceArn =
                                    record.path("eventSourceARN").asText();
                            if (eventSourceArn != null && eventSourceArn.contains("bounce")) {
                                notificationListener.listenToBounces(body);
                            } else {
                                notificationListener.listenToNotifications(body);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("SqsLambdaEventFilter: Error inspecting Lambda event body: {}", e.getMessage());
            }

            // Immediately complete response with 200 OK so FrameworkServlet is NEVER invoked with null method
            httpResponse.setStatus(HttpServletResponse.SC_OK);
            httpResponse.setContentType("application/json");
            httpResponse.getOutputStream().write("{\"batchItemFailures\":[]}".getBytes(StandardCharsets.UTF_8));
            return;
        }

        chain.doFilter(request, response);
    }
}
