package com.urlshortener.url_shortener.service;

import com.urlshortener.url_shortener.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CleanupService {

    private final UrlRepository urlRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final UrlPurgeService urlPurgeService;

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

    /**
     * Runs daily at 3am. Hard-deletes URLs inactive for 90+ days and their orphaned
     * click events, in batches of 500 with a 100ms pause between each so the DB
     * is never locked for more than a small chunk at a time.
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void purgeOldInactiveUrls() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(90));
        int totalPurged = 0;
        int batch;

        do {
            batch = urlPurgeService.deleteNextBatch(cutoff);
            totalPurged += batch;
            if (batch > 0) {
                try { Thread.sleep(100); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } while (batch > 0);

        if (totalPurged > 0) {
            log.info("Purge complete: hard-deleted {} URLs (and their click events) inactive for 90+ days", totalPurged);
        }
    }
}