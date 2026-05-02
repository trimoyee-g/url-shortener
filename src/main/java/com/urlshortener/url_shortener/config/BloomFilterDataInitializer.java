package com.urlshortener.url_shortener.config;

import com.urlshortener.url_shortener.repository.UrlRepository;
import com.urlshortener.url_shortener.util.UrlBloomFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BloomFilterDataInitializer implements ApplicationRunner {

    private final UrlRepository urlRepository;
    private final UrlBloomFilter urlBloomFilter;

    @Override
    public void run(ApplicationArguments args) {
        // Check if the filter is empty (e.g., brand new Redis instance)
        if (urlBloomFilter.getCount() == 0) {
            log.info("Bloom Filter appears empty. Starting bootstrap from Database...");

            // 1. Fetch only active short codes from DB
            // Note: Add this method to your UrlRepository
            List<String> activeShortCodes = urlRepository.findAllActiveShortCodes();

            // 2. Load into filter
            urlBloomFilter.addAll(activeShortCodes);
        } else {
            log.info("Bloom Filter already contains {} entries. Skipping bootstrap.",
                    urlBloomFilter.getCount());
        }
    }
}