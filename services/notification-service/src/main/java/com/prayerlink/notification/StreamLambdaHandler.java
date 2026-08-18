package com.prayerlink.notification;

import com.amazonaws.serverless.exceptions.ContainerInitializationException;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.model.HttpApiV2ProxyRequest;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.prayerlink.notification.config.ApplicationContextProvider;
import com.prayerlink.notification.listener.NotificationListener;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class StreamLambdaHandler implements RequestStreamHandler {
    private static final Logger log = LoggerFactory.getLogger(StreamLambdaHandler.class);
    private static SpringBootLambdaContainerHandler<HttpApiV2ProxyRequest, AwsProxyResponse> handler;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        try {
            handler = SpringBootLambdaContainerHandler.getHttpApiV2ProxyHandler(NotificationApplication.class);
        } catch (ContainerInitializationException e) {
            e.printStackTrace();
            throw new RuntimeException("Could not initialize Spring Boot application", e);
        }
    }

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
        byte[] inputBytes = inputStream.readAllBytes();
        if (inputBytes.length == 0) {
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(inputBytes);
            if (root.has("Records")) {
                log.info(
                        "Processing SQS event with {} records",
                        root.path("Records").size());
                NotificationListener listener = ApplicationContextProvider.getBean(NotificationListener.class);
                for (JsonNode record : root.path("Records")) {
                    String body = record.path("body").asText();
                    String eventSourceArn = record.path("eventSourceARN").asText();
                    if (eventSourceArn.contains("bounce")) {
                        listener.listenToBounces(body);
                    } else {
                        listener.listenToNotifications(body);
                    }
                }
                outputStream.write("{\"batchItemFailures\":[]}".getBytes(StandardCharsets.UTF_8));
                return;
            }
        } catch (Exception e) {
            log.warn("Not an SQS event or failed parsing SQS records: {}", e.getMessage());
        }

        handler.proxyStream(new ByteArrayInputStream(inputBytes), outputStream, context);
    }
}
