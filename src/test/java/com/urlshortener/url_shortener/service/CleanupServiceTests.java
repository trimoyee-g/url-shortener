package com.urlshortener.url_shortener.service;

import com.urlshortener.url_shortener.repository.UrlRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CleanupServiceTests {

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @InjectMocks
    private CleanupService cleanupService;

    // expireUrls()

    @Nested
    @DisplayName("expireUrls")
    class ExpireUrlsTests {

        @Test
        @DisplayName("deactivates expired URLs and invalidates Redis cache keys")
        void deactivatesUrls_andInvalidatesCache() {

            // Arrange
            List<String> expiredCodes = List.of("abc123", "xyz789");

            when(urlRepository.findRecentlyExpiredCodes(any(), any()))
                    .thenReturn(expiredCodes);

            when(urlRepository.deactivateExpiredUrls(any()))
                    .thenReturn(2);

            // Act
            cleanupService.expireUrls();

            // Assert repository interactions
            verify(urlRepository).findRecentlyExpiredCodes(any(), any());
            verify(urlRepository).deactivateExpiredUrls(any());

            // Assert Redis invalidation
            verify(redisTemplate).delete("url:abc123");
            verify(redisTemplate).delete("url:xyz789");

            verify(redisTemplate, times(2)).delete(anyString());
        }

        @Test
        @DisplayName("does not invalidate Redis when no URLs are expired")
        void doesNothing_whenNoExpiredUrls() {

            // Arrange
            when(urlRepository.findRecentlyExpiredCodes(any(), any()))
                    .thenReturn(List.of());

            when(urlRepository.deactivateExpiredUrls(any()))
                    .thenReturn(0);

            // Act
            cleanupService.expireUrls();

            // Assert
            verify(urlRepository).findRecentlyExpiredCodes(any(), any());
            verify(urlRepository).deactivateExpiredUrls(any());

            verify(redisTemplate, never()).delete(anyString());
        }

        @Test
        @DisplayName("invalidates all returned cache keys")
        void invalidatesAllReturnedKeys() {

            // Arrange
            List<String> expiredCodes = List.of(
                    "code1",
                    "code2",
                    "code3",
                    "code4"
            );

            when(urlRepository.findRecentlyExpiredCodes(any(), any()))
                    .thenReturn(expiredCodes);

            when(urlRepository.deactivateExpiredUrls(any()))
                    .thenReturn(4);

            // Act
            cleanupService.expireUrls();

            // Assert
            verify(redisTemplate).delete("url:code1");
            verify(redisTemplate).delete("url:code2");
            verify(redisTemplate).delete("url:code3");
            verify(redisTemplate).delete("url:code4");

            verify(redisTemplate, times(4)).delete(anyString());
        }

        @Test
        @DisplayName("calls repository methods in correct order")
        void callsRepositoryMethodsInOrder() {

            // Arrange
            when(urlRepository.findRecentlyExpiredCodes(any(), any()))
                    .thenReturn(List.of("abc"));

            when(urlRepository.deactivateExpiredUrls(any()))
                    .thenReturn(1);

            // Act
            cleanupService.expireUrls();

            // Assert ordering
            InOrder inOrder = inOrder(urlRepository);

            inOrder.verify(urlRepository)
                    .findRecentlyExpiredCodes(any(), any());

            inOrder.verify(urlRepository)
                    .deactivateExpiredUrls(any());
        }

        @Test
        @DisplayName("does not fail when expired URL list is empty but rows were updated")
        void handlesEmptyCodeListGracefully() {

            // Arrange
            when(urlRepository.findRecentlyExpiredCodes(any(), any()))
                    .thenReturn(List.of());

            when(urlRepository.deactivateExpiredUrls(any()))
                    .thenReturn(2);

            // Act
            cleanupService.expireUrls();

            // Assert
            verify(redisTemplate, never()).delete(anyString());
        }

        @Test
        @DisplayName("invalidates cache keys using correct Redis key format")
        void usesCorrectRedisKeyFormat() {

            // Arrange
            when(urlRepository.findRecentlyExpiredCodes(any(), any()))
                    .thenReturn(List.of("my-code"));

            when(urlRepository.deactivateExpiredUrls(any()))
                    .thenReturn(1);

            // Act
            cleanupService.expireUrls();

            // Assert
            verify(redisTemplate).delete("url:my-code");
        }
    }
}