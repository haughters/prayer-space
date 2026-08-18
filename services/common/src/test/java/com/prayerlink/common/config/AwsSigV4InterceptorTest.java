package com.prayerlink.common.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

public class AwsSigV4InterceptorTest {

    @Test
    void testSigV4HeadersWithSessionCredentialsAndDoubleSlash() throws IOException {
        AwsSigV4Interceptor interceptor = new AwsSigV4Interceptor(
                "eu-west-1",
                StaticCredentialsProvider.create(AwsSessionCredentials.create(
                        "ASIATEMPKEY", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", "SESSIONTOKEN123")));

        // Intentionally test with double slash URL
        MockClientHttpRequest request = new MockClientHttpRequest(
                HttpMethod.GET,
                URI.create("https://qltqrgv27bov7jm2ivjbevyegy0lvqwk.lambda-url.eu-west-1.on.aws//api/groups"));

        ClientHttpRequestExecution execution = (req, body) -> {
            System.out.println("URI after intercept: " + req.getURI());
            System.out.println("Headers after intercept: " + req.getHeaders());
            assertEquals(
                    URI.create("https://qltqrgv27bov7jm2ivjbevyegy0lvqwk.lambda-url.eu-west-1.on.aws/api/groups"),
                    req.getURI());
            assertNotNull(req.getHeaders().getFirst("Authorization"));
            assertNotNull(req.getHeaders().getFirst("X-Amz-Date"));
            assertNotNull(req.getHeaders().getFirst("X-Amz-Security-Token"));
            assertTrue(req.getHeaders().getFirst("Authorization").contains("SignedHeaders="));
            return new MockClientHttpResponse(new byte[0], org.springframework.http.HttpStatus.OK);
        };

        interceptor.intercept(request, new byte[0], execution);
    }
}
