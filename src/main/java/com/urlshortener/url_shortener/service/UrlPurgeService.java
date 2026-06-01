package com.urlshortener.url_shortener.service;

import com.urlshortener.url_shortener.repository.ClickEventRepository;
import com.urlshortener.url_shortener.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Handles a single batch of the hard-delete purge. Kept in its own bean so
 * that each batch runs in its own @Transactional scope — the lock is held only
 * for the duration of one batch, then released before the next begins.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UrlPurgeService {

    private static final int BATCH_SIZE = 500;

    private final UrlRepository urlRepository;
    private final ClickEventRepository clickEventRepository;

    /**
     * Deletes one batch of inactive URLs and their orphaned click events.
     *
     * @return number of URL rows deleted (0 signals the caller to stop looping)
     */
    @Transactional
    public int deleteNextBatch(Instant cutoff) {
        List<Long> ids = urlRepository.findInactiveBatch(cutoff, PageRequest.of(0, BATCH_SIZE));
        if (ids.isEmpty()) return 0;

        List<String> shortCodes = urlRepository.findShortCodesByIds(ids);

        // Delete click events first to avoid any constraint issues
        if (!shortCodes.isEmpty()) {
            clickEventRepository.deleteByShortCodeIn(shortCodes);
        }

        return urlRepository.deleteByIds(ids);
    }
}
