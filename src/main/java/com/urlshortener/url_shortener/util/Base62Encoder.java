package com.urlshortener.url_shortener.util;


import org.springframework.stereotype.Component;

@Component
public class Base62Encoder {

    private static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = 62;
    private static final int CODE_LENGTH = 7;

    /**
     * Encodes a positive long ID into a Base62 string.
     * IDs from DB auto-increment guarantee uniqueness — no collision possible.
     */
    public String encode(long id) {
        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            sb.append(ALPHABET.charAt((int)(id % BASE)));
            id /= BASE;
        }
        // Pad to CODE_LENGTH for consistent URL length
        while (sb.length() < CODE_LENGTH) {
            sb.append('0');
        }
        return sb.reverse().toString();
    }

    public boolean isValidCode(String code) {
        if (code == null || code.isBlank()) return false;
        for (char c : code.toCharArray()) {
            if (ALPHABET.indexOf(c) < 0) return false;
        }
        return true;
    }
}