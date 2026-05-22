package com.urlshortener.url_shortener.util;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class UrlCuckooFilter {

    private static final String FILTER_KEY = "url_cuckoo_filter";
    private static final long CAPACITY = 1_000_000L;
    private static final double LOAD_FACTOR_ALERT = 0.85;

    private final RedisTemplate<String, String> redisTemplate;

    @PostConstruct
    public void init() {
        try {
            redisTemplate.execute((RedisCallback<Object>) connection ->
                    connection.execute("CF.RESERVE",
                            key(),
                            String.valueOf(CAPACITY).getBytes(StandardCharsets.UTF_8),
                            "BUCKETSIZE".getBytes(StandardCharsets.UTF_8), "2".getBytes(StandardCharsets.UTF_8),
                            "MAXITERATIONS".getBytes(StandardCharsets.UTF_8), "20".getBytes(StandardCharsets.UTF_8),
                            "EXPANSION".getBytes(StandardCharsets.UTF_8), "2".getBytes(StandardCharsets.UTF_8)
                    )
            );
            log.info("Cuckoo filter reserved with capacity {}", CAPACITY);
        } catch (Exception e) {
            log.debug("Cuckoo filter already exists: {}", e.getMessage());
        }
    }

    public void add(String shortCode) {
        redisTemplate.execute((RedisCallback<Object>) connection ->
                connection.execute("CF.ADD", key(), bytes(shortCode))
        );
        checkLoadFactor();
    }

    // Pipelined — single round trip for all codes
    public void addAll(List<String> shortCodes) {
        if (shortCodes == null || shortCodes.isEmpty()) return;
        log.info("Batch inserting {} codes into Cuckoo Filter...", shortCodes.size());
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            shortCodes.forEach(code ->
                    connection.execute("CF.ADD", key(), bytes(code))
            );
            return null;
        });
        log.info("Cuckoo Filter batch insert complete.");
    }

    public boolean mightContain(String shortCode) {
        Object result = redisTemplate.execute((RedisCallback<Object>) connection ->
                connection.execute("CF.EXISTS", key(), bytes(shortCode))
        );
        return toLong(result) == 1L;
    }

    public void delete(String shortCode) {
        Object result = redisTemplate.execute((RedisCallback<Object>) connection ->
                connection.execute("CF.DEL", key(), bytes(shortCode))
        );
        if (toLong(result) != 1L) {
            log.warn("Cuckoo filter: '{}' was not present during delete (possible false-positive or double-delete)", shortCode);
        }
    }

    public long getCount() {
        try {
            List<?> info = redisTemplate.execute((RedisCallback<List<?>>) connection ->
                    (List<?>) connection.execute("CF.INFO", key())
            );
            if (info == null) return 0L;
            // CF.INFO returns a flat [field, value, field, value...] list
            for (int i = 0; i + 1 < info.size(); i += 2) {
                if ("Number of items inserted".equals(decodeField(info.get(i)))) {
                    return toLong(info.get(i + 1));
                }
            }
        } catch (Exception e) {
            log.debug("Cuckoo filter info unavailable: {}", e.getMessage());
        }
        return 0L;
    }

    // Pipelined — same fix as addAll
    public void reload(List<String> allActiveCodes) {
        if (allActiveCodes == null || allActiveCodes.isEmpty()) return;
        log.info("Re-populating Cuckoo Filter with {} codes", allActiveCodes.size());
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            allActiveCodes.forEach(code ->
                    connection.execute("CF.ADD", key(), bytes(code))
            );
            return null;
        });
        log.info("Cuckoo Filter reload complete.");
    }

    private void checkLoadFactor() {
        long count = getCount();
        double load = (double) count / CAPACITY;
        if (load >= LOAD_FACTOR_ALERT) {
            log.warn("Cuckoo filter load factor is {:.1%} ({}/{}). Consider increasing capacity.",
                    load, count, CAPACITY);
        }
    }

    private byte[] key() {
        return FILTER_KEY.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String decodeField(Object raw) {
        if (raw instanceof byte[]) return new String((byte[]) raw, StandardCharsets.UTF_8);
        if (raw instanceof String s) return s;
        return "";
    }

    private static long toLong(Object raw) {
        if (raw instanceof Long l) return l;
        if (raw instanceof Integer i) return i.longValue();
        if (raw instanceof byte[] b) return Long.parseLong(new String(b, StandardCharsets.UTF_8));
        if (raw instanceof String s) return Long.parseLong(s);
        return 0L;
    }
}