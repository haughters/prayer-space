package com.prayerlink.common.util;

import java.net.URI;
import org.springframework.web.util.UriComponentsBuilder;

public final class UrlUtils {

    private UrlUtils() {}

    /**
     * Sanitizes a base URL by removing any trailing slashes.
     *
     * @param url the base URL to sanitize
     * @return the sanitized URL without trailing slashes, or empty string if null
     */
    public static String cleanBaseUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return "";
        }
        return url.trim().replaceAll("/+$", "");
    }

    /**
     * Builds a normalized URI from a base URL and relative path, preventing double slashes.
     *
     * @param baseUrl the base service URL
     * @param path the path relative to the base URL
     * @param uriVariables optional URI template variables to expand
     * @return the normalized URI
     */
    public static URI buildUri(String baseUrl, String path, Object... uriVariables) {
        String cleanBase = cleanBaseUrl(baseUrl);
        String cleanPath = path == null ? "" : (path.startsWith("/") ? path : "/" + path);
        return UriComponentsBuilder.fromUriString(cleanBase)
                .path(cleanPath)
                .buildAndExpand(uriVariables)
                .encode()
                .toUri();
    }
}
