package com.prayerlink.common.config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;

/**
 * Spring {@link ClientHttpRequestInterceptor} that signs outgoing HTTP requests
 * with AWS SigV4 using the Lambda execution role's credentials.
 *
 * <p>Only signs requests whose host matches {@code *.lambda-url.*.on.aws}.
 * All other requests (e.g. {@code localhost} during local dev) pass through unsigned.
 */
public class AwsSigV4Interceptor implements ClientHttpRequestInterceptor {

    private Aws4Signer signer;
    private AwsCredentialsProvider credentialsProvider;
    private final Region region;

    public AwsSigV4Interceptor(String region) {
        this.region = Region.of(region);
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
            ClientHttpRequestExecution execution) throws IOException {

        URI uri = request.getURI();
        String host = uri.getHost();

        // Only sign requests to Lambda Function URLs
        if (host == null || !host.contains(".lambda-url.") || !host.endsWith(".on.aws")) {
            return execution.execute(request, body);
        }

        // Lazy initialization to avoid GraalVM startup reflection crashes
        if (this.signer == null) {
            this.signer = Aws4Signer.create();
            this.credentialsProvider = software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider.create();
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

        if (body != null && body.length > 0) {
            sdkBuilder.contentStreamProvider(() -> new ByteArrayInputStream(body));
        }

        // Sign the request
        Aws4SignerParams params = Aws4SignerParams.builder()
                .signingName("lambda")
                .signingRegion(this.region)
                .awsCredentials(credentialsProvider.resolveCredentials())
                .build();

        SdkHttpFullRequest signed = signer.sign(sdkBuilder.build(), params);

        // Copy all headers from the signed request (originals + Authorization, X-Amz-Date, etc.)
        HttpHeaders signedHeaders = new HttpHeaders();
        signed.headers().forEach((name, values) ->
                signedHeaders.addAll(name, new ArrayList<>(values)));

        HttpRequest signedRequest = new HttpRequestWrapper(request) {
            @Override
            public HttpHeaders getHeaders() {
                return signedHeaders;
            }
        };

        return execution.execute(signedRequest, body);
    }
}
