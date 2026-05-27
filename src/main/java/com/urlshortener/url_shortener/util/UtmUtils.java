package com.urlshortener.url_shortener.util;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Utility for appending UTM tracking parameters to destination URLs.
 *
 * <p>Existing query parameters are preserved. UTM params are only appended
 * when a non-blank value is provided; null/blank values are skipped entirely
 * so we never produce empty {@code utm_source=} keys in the URL.
 */
public final class UtmUtils {

    private UtmUtils() {}

    /**
     * Appends any provided UTM parameters to {@code url}.
     *
     * @param url       the destination URL (must be a valid URL)
     * @param source    utm_source value; skipped if null or blank
     * @param medium    utm_medium value; skipped if null or blank
     * @param campaign  utm_campaign value; skipped if null or blank
     * @return the URL with UTM params appended, or the original URL if none were provided
     * @throws IllegalArgumentException if the base URL cannot be parsed
     */
    public static String appendUtmParams(String url, String source, String medium, String campaign) {
        if (isBlank(source) && isBlank(medium) && isBlank(campaign)) {
            return url;
        }

        try {
            URI uri = new URI(url);
            String existingQuery = uri.getRawQuery();
            StringBuilder query = new StringBuilder(existingQuery != null ? existingQuery : "");

            appendParam(query, "utm_source",   source);
            appendParam(query, "utm_medium",   medium);
            appendParam(query, "utm_campaign", campaign);

            return new URI(
                    uri.getScheme(),
                    uri.getAuthority(),
                    uri.getPath(),
                    query.length() > 0 ? query.toString() : null,
                    uri.getFragment()
            ).toString();

        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Cannot parse destination URL: " + url, e);
        }
    }

    private static void appendParam(StringBuilder query, String key, String value) {
        if (isBlank(value)) return;
        if (query.length() > 0) query.append('&');
        query.append(key).append('=').append(encode(value));
    }

    private static String encode(String value) {
        // Percent-encode only the characters that would break a query string.
        // We keep it simple rather than pulling in a full URLEncoder (which encodes '+' as '%2B').
        return value
                .replace("%",  "%25")
                .replace(" ",  "%20")
                .replace("&",  "%26")
                .replace("=",  "%3D")
                .replace("+",  "%2B")
                .replace("#",  "%23");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
