package com.urlshortener.url_shortener.service;

import com.urlshortener.url_shortener.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CleanupService {

    private final UrlRepository urlRepository;
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Runs every 5 minutes. Soft-deletes expired URLs, then invalidates their Redis keys.
     * Uses the partial index on expires_at for efficiency.
     */
    @Scheduled(fixedRateString = "PT5M")
    @Transactional
    public void expireUrls() {
        Instant now = Instant.now();
        Instant cutoff = now.minusSeconds(6 * 60); // window slightly wider than schedule interval

        // Fetch codes before deactivation so we can invalidate cache
        List<String> codes = urlRepository.findRecentlyExpiredCodes(now, cutoff);

        int deactivated = urlRepository.deactivateExpiredUrls(now);

        if (deactivated > 0) {
            // Bulk cache invalidation
            codes.stream()
                    .map(code -> "url:" + code)
                    .forEach(redisTemplate::delete);
            log.info("Cleanup: deactivated {} expired URLs, invalidated {} cache keys",
                    deactivated, codes.size());
        }
    }
}