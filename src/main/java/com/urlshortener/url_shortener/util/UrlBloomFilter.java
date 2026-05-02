package com.urlshortener.url_shortener.util;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class UrlBloomFilter {

    private final RedissonClient redissonClient;
    private RBloomFilter<String> bloomFilter;

    @PostConstruct
    public void init() {
        // This is the distributed object stored in Redis
        bloomFilter = redissonClient.getBloomFilter("url_bloom_filter");

        // Initialize: Expected insertions = 1M, False Positive Probability = 0.01
        // tryInit returns false if it was already initialized previously
        bloomFilter.tryInit(1000000L, 0.01);
    }

    public void add(String shortCode) {
        bloomFilter.add(shortCode);
    }

    public void addAll(List<String> shortCodes) {
        if (shortCodes == null || shortCodes.isEmpty()) return;

        log.info("Batch inserting {} codes into Bloom Filter...", shortCodes.size());
        // Redisson handles the bit offsets for the list
        shortCodes.forEach(bloomFilter::add);
        log.info("Bloom Filter re-population complete.");
    }

    public long getCount() {
        return bloomFilter.count();
    }

    public boolean mightContain(String shortCode) {
        return bloomFilter.contains(shortCode);
    }

    public void reload(List<String> allActiveCodes) {
        log.info("Re-populating Bloom Filter with {} codes", allActiveCodes.size());
        allActiveCodes.forEach(this::add);
    }
}