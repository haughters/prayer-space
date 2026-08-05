package com.prayerlink.common.config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.SignedRequest;
import software.amazon.awssdk.regions.Region;

/**
 * Spring {@link ClientHttpRequestInterceptor} that signs outgoing HTTP requests
 * with AWS SigV4 using the Lambda execution role's credentials.
 *
 * <p>Only signs requests whose host matches {@code *.lambda-url.*.on.aws}.
 * All other requests (e.g. {@code localhost} during local dev) pass through unsigned.
 */
public class AwsSigV4Interceptor implements ClientHttpRequestInterceptor {

    private AwsV4HttpSigner signer;
    private AwsCredentialsProvider credentialsProvider;
    private final Region region;

    public AwsSigV4Interceptor(String region) {
        this.region = Region.of(region);
    }

    @Override
    @NullMarked
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {

        URI uri = request.getURI();
        String host = uri.getHost();

        if (host == null || !host.contains(".lambda-url.") || !host.endsWith(".on.aws")) {
            return execution.execute(request, body);
        }

        // Lazy initialization to avoid GraalVM startup reflection crashes
        if (this.signer == null) {
            this.signer = AwsV4HttpSigner.create();
            this.credentialsProvider = EnvironmentVariableCredentialsProvider.create();
        }

        // Convert Spring HttpRequest → AWS SDK SdkHttpFullRequest
        SdkHttpFullRequest.Builder sdkBuilder = SdkHttpFullRequest.builder()
                .uri(uri)
                .method(SdkHttpMethod.fromValue(request.getMethod().name()));

        request.getHeaders().forEach((name, values) -> {
            for (String value : values) {
                sdkBuilder.appendHeader(name, value);
            }
        });

        if (body.length > 0) {
            sdkBuilder.contentStreamProvider(() -> new ByteArrayInputStream(body));
        }

        SdkHttpFullRequest builtRequest = sdkBuilder.build();
        SignedRequest signedRequest = signer.sign(r -> r.identity(credentialsProvider.resolveCredentials())
                .request(builtRequest)
                .payload(builtRequest.contentStreamProvider().orElse(null))
                .putProperty(AwsV4HttpSigner.SERVICE_SIGNING_NAME, "lambda")
                .putProperty(AwsV4HttpSigner.REGION_NAME, this.region.id()));

        SdkHttpRequest signed = signedRequest.request();
        HttpHeaders signedHeaders = new HttpHeaders();
        signed.headers().forEach((name, values) -> signedHeaders.addAll(name, new ArrayList<>(values)));

        HttpRequest wrappedRequest = new HttpRequestWrapper(request) {
            @Override
            public HttpHeaders getHeaders() {
                return signedHeaders;
            }
        };

        return execution.execute(wrappedRequest, body);
    }
}
