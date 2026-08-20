package com.prayerlink.notification;

import com.prayerlink.common.config.RestTemplateConfig;
import com.prayerlink.notification.listener.NotificationListener;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootApplication(scanBasePackages = "com.prayerlink")
@Import(RestTemplateConfig.class)
public class NotificationApplication implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(NotificationApplication.class);

    private final NotificationListener notificationListener;
    private final ObjectMapper objectMapper;

    public NotificationApplication(NotificationListener notificationListener, ObjectMapper objectMapper) {
        this.notificationListener = notificationListener;
        this.objectMapper = objectMapper;
    }

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }

    @Override
    public void run(String... args) {
        String runtimeApi = System.getenv("AWS_LAMBDA_RUNTIME_API");
        if (runtimeApi == null || runtimeApi.isBlank()) {
            return;
        }

        log.info("Starting native AWS Lambda SQS runtime event loop on {}", runtimeApi);
        HttpClient client = HttpClient.newHttpClient();
        String nextUrl = "http://" + runtimeApi + "/2018-06-01/runtime/invocation/next";

        while (true) {
            String requestId = null;
            try {
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(nextUrl)).GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                requestId = response.headers().firstValue("Lambda-Runtime-Aws-Request-Id").orElse(null);
                String body = response.body();

                if (body != null && !body.isBlank()) {
                    log.info("Processing Lambda invocation {}: {}", requestId, body);
                    JsonNode root = objectMapper.readTree(body);
                    if (root.has("Records")) {
                        for (JsonNode record : root.path("Records")) {
                            String recordBody = record.path("body").asText();
                            String eventSourceArn = record.path("eventSourceARN").asText();
                            if (eventSourceArn != null && eventSourceArn.contains("bounce")) {
                                notificationListener.listenToBounces(recordBody);
                            } else {
                                notificationListener.listenToNotifications(recordBody);
                            }
                        }
                    }
                }

                if (requestId != null) {
                    String responseUrl = "http://" + runtimeApi + "/2018-06-01/runtime/invocation/" + requestId + "/response";
                    HttpRequest responseReq = HttpRequest.newBuilder()
                            .uri(URI.create(responseUrl))
                            .POST(HttpRequest.BodyPublishers.ofString("{\"batchItemFailures\":[]}"))
                            .build();
                    client.send(responseReq, HttpResponse.BodyHandlers.discarding());
                }
            } catch (Exception e) {
                log.error("Error processing Lambda event {}: {}", requestId, e.getMessage(), e);
                if (requestId != null) {
                    try {
                        String errorUrl = "http://" + runtimeApi + "/2018-06-01/runtime/invocation/" + requestId + "/error";
                        HttpRequest errorReq = HttpRequest.newBuilder()
                                .uri(URI.create(errorUrl))
                                .POST(HttpRequest.BodyPublishers.ofString("{\"errorMessage\":\"" + e.getMessage() + "\"}"))
                                .build();
                        client.send(errorReq, HttpResponse.BodyHandlers.discarding());
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }
}
