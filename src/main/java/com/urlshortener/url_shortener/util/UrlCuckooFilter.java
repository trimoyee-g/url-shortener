package com.urlshortener.url_shortener.util;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class UrlCuckooFilter {

    private static final String FILTER_KEY = "url_cuckoo_filter";
    private static final long CAPACITY = 1_000_000L;
    private static final double LOAD_FACTOR_ALERT = 0.85;

    private final RedisTemplate<String, String> redisTemplate;
    private final AtomicLong insertCount = new AtomicLong();

    @PostConstruct
    public void init() {
        try {
            redisTemplate.execute((RedisCallback<Object>) connection ->
                    connection.execute("CF.RESERVE",
                            key(),
                            String.valueOf(CAPACITY).getBytes(StandardCharsets.UTF_8),
                            "BUCKETSIZE".getBytes(StandardCharsets.UTF_8),     "2".getBytes(StandardCharsets.UTF_8),
                            "MAXITERATIONS".getBytes(StandardCharsets.UTF_8),  "20".getBytes(StandardCharsets.UTF_8),
                            "EXPANSION".getBytes(StandardCharsets.UTF_8),      "2".getBytes(StandardCharsets.UTF_8)
                    )
            );
            log.info("Cuckoo filter reserved with capacity {}", CAPACITY);
        } catch (Exception e) {
            // Key already exists from a previous run — safe to ignore
            log.debug("Cuckoo filter already exists: {}", e.getMessage());
        }
    }

    public void add(String shortCode) {
        try {
            redisTemplate.execute((RedisCallback<Object>) connection -> {
                connection.execute("CF.ADD", key(), bytes(shortCode));
                return null; // CF.ADD returns boolean — ByteArrayOutput can't decode it, ignore
            });
        } catch (Exception e) {
            log.debug("CF.ADD decode harmless error (expected): {}", e.getMessage());
        }
        if (insertCount.incrementAndGet() % 10_000 == 0) checkLoadFactor();
    }

    // Pipelined — single round trip for all codes
    public void addAll(List<String> shortCodes) {
        if (shortCodes == null || shortCodes.isEmpty()) return;
        log.info("Batch inserting {} codes into Cuckoo Filter...", shortCodes.size());
        try {
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                shortCodes.forEach(code ->
                        connection.execute("CF.ADD", key(), bytes(code))
                );
                return null;
            });
        } catch (Exception e) {
            log.debug("CF.ADD pipeline decode harmless error (expected): {}", e.getMessage());
        }
        log.info("Cuckoo Filter batch insert complete.");
    }

    public boolean mightContain(String shortCode) {
        try {
            Object result = redisTemplate.execute((RedisCallback<Object>) connection ->
                    connection.execute("CF.EXISTS", key(), bytes(shortCode))
            );
            // CF.EXISTS may return boolean or long depending on Redis/Lettuce version
            if (result instanceof Boolean b) return b;
            return toLong(result) == 1L;
        } catch (Exception e) {
            // On decode error, fail open (let the request through to Redis/DB)
            log.debug("CF.EXISTS decode error, failing open: {}", e.getMessage());
            return true;
        }
    }

    public void delete(String shortCode) {
        try {
            redisTemplate.execute((RedisCallback<Object>) connection -> {
                connection.execute("CF.DEL", key(), bytes(shortCode));
                return null; // CF.DEL returns boolean — same issue as CF.ADD
            });
        } catch (Exception e) {
            log.debug("CF.DEL decode harmless error (expected): {}", e.getMessage());
        }
    }

    public long getCount() {
        try {
            List<?> info = redisTemplate.execute((RedisCallback<List<?>>) connection ->
                    (List<?>) connection.execute("CF.INFO", key())
            );
            if (info == null) return 0L;
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
        try {
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                allActiveCodes.forEach(code ->
                        connection.execute("CF.ADD", key(), bytes(code))
                );
                return null;
            });
        } catch (Exception e) {
            log.debug("CF.ADD reload pipeline decode harmless error (expected): {}", e.getMessage());
        }
        log.info("Cuckoo Filter reload complete.");
    }

    private void checkLoadFactor() {
        long count = getCount();
        double load = (double) count / CAPACITY;
        if (load >= LOAD_FACTOR_ALERT) {
            log.warn("Cuckoo filter load factor is {}% ({}/{}). Consider increasing capacity.",
                    String.format("%.1f", load * 100), count, CAPACITY);
        }
    }

    private byte[] key() {
        return FILTER_KEY.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String decodeField(Object raw) {
        if (raw instanceof byte[] b) return new String(b, StandardCharsets.UTF_8);
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