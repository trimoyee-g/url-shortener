package com.urlshortener.url_shortener.util;

/**
 * Classifies a raw User-Agent string into one of three device categories:
 * MOBILE, TABLET, or DESKTOP.
 *
 * Rules (applied in order):
 *  1. Tablet  — iPad, Android without "Mobile" keyword, or explicit "Tablet" token
 *  2. Mobile  — any remaining UA that contains "Mobile", "iPhone", "iPod",
 *               "BlackBerry", "Windows Phone", or "webOS"
 *  3. Desktop — everything else
 *
 * This intentionally has no external dependencies; a single regex per branch
 * is fast enough given the call site (inside the async analytics consumer).
 */
public final class DeviceParser {

    public enum DeviceType {
        MOBILE, TABLET, DESKTOP
    }

    // Matches tablet indicators. "Android" alone (without "Mobile") = tablet convention.
    private static final java.util.regex.Pattern TABLET_PATTERN =
            java.util.regex.Pattern.compile(
                    "iPad|Tablet|Kindle|Silk|PlayBook|(Android(?!.*Mobile))",
                    java.util.regex.Pattern.CASE_INSENSITIVE);

    // Matches phone / handheld indicators.
    private static final java.util.regex.Pattern MOBILE_PATTERN =
            java.util.regex.Pattern.compile(
                    "Mobile|iPhone|iPod|BlackBerry|Windows Phone|webOS|IEMobile|Opera Mini",
                    java.util.regex.Pattern.CASE_INSENSITIVE);

    private DeviceParser() {}

    /**
     * @param userAgent raw User-Agent header value; may be null or blank.
     * @return DeviceType — never null; returns DESKTOP for null/blank input.
     */
    public static DeviceType parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return DeviceType.DESKTOP;
        }
        if (TABLET_PATTERN.matcher(userAgent).find()) {
            return DeviceType.TABLET;
        }
        if (MOBILE_PATTERN.matcher(userAgent).find()) {
            return DeviceType.MOBILE;
        }
        return DeviceType.DESKTOP;
    }
}
