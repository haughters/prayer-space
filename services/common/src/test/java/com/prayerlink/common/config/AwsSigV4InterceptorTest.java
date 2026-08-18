package com.prayerlink.common.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
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

    @Test
    void testSigV4WithQueryParamsAndMultipleSlashes() throws IOException {
        AwsSigV4Interceptor interceptor = new AwsSigV4Interceptor(
                "eu-west-1",
                StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        "AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")));

        MockClientHttpRequest request = new MockClientHttpRequest(
                HttpMethod.GET,
                URI.create(
                        "https://qltqrgv27bov7jm2ivjbevyegy0lvqwk.lambda-url.eu-west-1.on.aws///api///groups?status=OPEN&page=0"));

        ClientHttpRequestExecution execution = (req, body) -> {
            assertEquals(
                    URI.create(
                            "https://qltqrgv27bov7jm2ivjbevyegy0lvqwk.lambda-url.eu-west-1.on.aws/api/groups?status=OPEN&page=0"),
                    req.getURI());
            assertNotNull(req.getHeaders().getFirst("Authorization"));
            assertNotNull(req.getHeaders().getFirst("X-Amz-Date"));
            return new MockClientHttpResponse(new byte[0], org.springframework.http.HttpStatus.OK);
        };

        interceptor.intercept(request, new byte[0], execution);
    }

    @Test
    void testSigV4WithHttpPostAndJsonBody() throws IOException {
        AwsSigV4Interceptor interceptor = new AwsSigV4Interceptor(
                "eu-west-1",
                StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        "AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")));

        MockClientHttpRequest request = new MockClientHttpRequest(
                HttpMethod.POST,
                URI.create("https://qltqrgv27bov7jm2ivjbevyegy0lvqwk.lambda-url.eu-west-1.on.aws/api/groups"));
        request.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
        byte[] body = "{\"name\":\"Prayer Circle\"}".getBytes(StandardCharsets.UTF_8);

        ClientHttpRequestExecution execution = (req, reqBody) -> {
            assertNotNull(req.getHeaders().getFirst("Authorization"));
            assertNotNull(req.getHeaders().getFirst("X-Amz-Date"));
            assertNotNull(req.getHeaders().getFirst("x-amz-content-sha256"));
            return new MockClientHttpResponse(new byte[0], org.springframework.http.HttpStatus.CREATED);
        };

        interceptor.intercept(request, body, execution);
    }

    @Test
    void testBypassSigningForLocalhost() throws IOException {
        AwsSigV4Interceptor interceptor = new AwsSigV4Interceptor("eu-west-1");

        MockClientHttpRequest request =
                new MockClientHttpRequest(HttpMethod.GET, URI.create("http://localhost:8083/api/groups"));

        ClientHttpRequestExecution execution = (req, body) -> {
            assertNull(req.getHeaders().getFirst("Authorization"));
            assertNull(req.getHeaders().getFirst("X-Amz-Date"));
            return new MockClientHttpResponse(new byte[0], org.springframework.http.HttpStatus.OK);
        };

        interceptor.intercept(request, new byte[0], execution);
    }
}
