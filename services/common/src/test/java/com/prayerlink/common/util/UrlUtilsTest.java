package com.prayerlink.common.util;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import org.junit.jupiter.api.Test;

public class UrlUtilsTest {

    @Test
    void testCleanBaseUrl() {
        assertEquals("", UrlUtils.cleanBaseUrl(null));
        assertEquals("", UrlUtils.cleanBaseUrl("   "));
        assertEquals("http://localhost:8083", UrlUtils.cleanBaseUrl("http://localhost:8083"));
        assertEquals("http://localhost:8083", UrlUtils.cleanBaseUrl("http://localhost:8083/"));
        assertEquals("http://localhost:8083", UrlUtils.cleanBaseUrl("http://localhost:8083///"));
        assertEquals(
                "https://xyz.lambda-url.eu-west-1.on.aws",
                UrlUtils.cleanBaseUrl("https://xyz.lambda-url.eu-west-1.on.aws/"));
    }

    @Test
    void testBuildUri() {
        URI uri1 = UrlUtils.buildUri("http://localhost:8083/", "/api/groups");
        assertEquals("http://localhost:8083/api/groups", uri1.toString());

        URI uri2 = UrlUtils.buildUri("http://localhost:8083", "api/groups");
        assertEquals("http://localhost:8083/api/groups", uri2.toString());

        URI uri3 = UrlUtils.buildUri("https://xyz.lambda-url.eu-west-1.on.aws/", "/api/groups/{id}", "group-123");
        assertEquals("https://xyz.lambda-url.eu-west-1.on.aws/api/groups/group-123", uri3.toString());
    }
}
