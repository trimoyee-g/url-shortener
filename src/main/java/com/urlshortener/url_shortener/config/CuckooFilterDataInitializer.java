package com.urlshortener.url_shortener.config;

import com.urlshortener.url_shortener.repository.UrlRepository;
import com.urlshortener.url_shortener.util.UrlCuckooFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.cuckoo-filter.enabled", matchIfMissing = true)
public class CuckooFilterDataInitializer implements ApplicationRunner {

    private final UrlRepository urlRepository;
    private final UrlCuckooFilter urlCuckooFilter;

    @Override
    public void run(ApplicationArguments args) {
        if (urlCuckooFilter.getCount() == 0) {
            log.info("Cuckoo Filter appears empty. Bootstrapping from database...");
            List<String> activeShortCodes = urlRepository.findAllActiveShortCodes();
            urlCuckooFilter.addAll(activeShortCodes);
        } else {
            log.info("Cuckoo Filter already contains {} entries. Skipping bootstrap.",
                    urlCuckooFilter.getCount());
        }
    }
}