package com.urlshortener.url_shortener.controller;

import com.urlshortener.url_shortener.dto.UrlClickEvent;
import com.urlshortener.url_shortener.exception.GlobalExceptionHandler;
import com.urlshortener.url_shortener.exception.UrlNotFoundException;
import com.urlshortener.url_shortener.filter.JwtAuthFilter;
import com.urlshortener.url_shortener.service.AnalyticsProducer;
import com.urlshortener.url_shortener.service.UrlService;
import com.urlshortener.url_shortener.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({RedirectController.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("RedirectController")
class RedirectControllerTests {

    private static final String REDIRECT_URL    = "/{shortCode}";
    private static final String TEST_SHORT_CODE = "abc123";
    private static final String TEST_LONG_URL   = "https://google.com";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UrlService urlService;

    @MockBean
    private AnalyticsProducer analyticsProducer;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    // =========================================================================
    // GET /{shortCode}
    // =========================================================================

    @Nested
    @DisplayName("GET /{shortCode}")
    class Redirect {

        @Test
        @DisplayName("302 Found — valid short code redirects to long URL")
        void redirectsToLongUrl() throws Exception {

            when(urlService.resolve(TEST_SHORT_CODE)).thenReturn(TEST_LONG_URL);

            mockMvc.perform(get(REDIRECT_URL, TEST_SHORT_CODE))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", TEST_LONG_URL));

            verify(urlService, times(1)).resolve(TEST_SHORT_CODE);
        }

        @Test
        @DisplayName("302 Found — Kafka click event is published after successful resolve")
        void publishesClickEventToKafka() throws Exception {

            when(urlService.resolve(TEST_SHORT_CODE)).thenReturn(TEST_LONG_URL);

            mockMvc.perform(get(REDIRECT_URL, TEST_SHORT_CODE)
                            .header("User-Agent", "Mozilla/5.0")
                            .header("Referer", "https://referrer.com"))
                    .andExpect(status().isFound());

            verify(analyticsProducer, times(1)).recordClick(any(UrlClickEvent.class));
        }

        @Test
        @DisplayName("302 Found — click event contains correct shortCode, User-Agent and Referer")
        void clickEventContainsCorrectFields() throws Exception {

            when(urlService.resolve(TEST_SHORT_CODE)).thenReturn(TEST_LONG_URL);

            mockMvc.perform(get(REDIRECT_URL, TEST_SHORT_CODE)
                            .header("User-Agent", "Mozilla/5.0")
                            .header("Referer", "https://referrer.com"))
                    .andExpect(status().isFound());

            ArgumentCaptor<UrlClickEvent> captor = ArgumentCaptor.forClass(UrlClickEvent.class);
            verify(analyticsProducer).recordClick(captor.capture());

            UrlClickEvent event = captor.getValue();
            assertThat(event.getShortCode()).isEqualTo(TEST_SHORT_CODE);
            assertThat(event.getUserAgent()).isEqualTo("Mozilla/5.0");
            assertThat(event.getReferer()).isEqualTo("https://referrer.com");
            assertThat(event.getIpAddress()).isNotNull();
        }

        @Test
        @DisplayName("302 Found — click event is published even when User-Agent and Referer are absent")
        void publishesClickEventWithNullHeaders() throws Exception {

            when(urlService.resolve(TEST_SHORT_CODE)).thenReturn(TEST_LONG_URL);

            // No User-Agent or Referer headers
            mockMvc.perform(get(REDIRECT_URL, TEST_SHORT_CODE))
                    .andExpect(status().isFound());

            ArgumentCaptor<UrlClickEvent> captor = ArgumentCaptor.forClass(UrlClickEvent.class);
            verify(analyticsProducer).recordClick(captor.capture());

            UrlClickEvent event = captor.getValue();
            assertThat(event.getUserAgent()).isNull();
            assertThat(event.getReferer()).isNull();
        }

        @Test
        @DisplayName("404 Not Found — unknown short code returns 404")
        void returnsNotFoundForUnknownShortCode() throws Exception {

            when(urlService.resolve(TEST_SHORT_CODE))
                    .thenThrow(new UrlNotFoundException(TEST_SHORT_CODE));

            mockMvc.perform(get(REDIRECT_URL, TEST_SHORT_CODE))
                    .andExpect(status().isNotFound());

            verify(urlService, times(1)).resolve(TEST_SHORT_CODE);
        }

        @Test
        @DisplayName("404 Not Found — Kafka event is NOT published when short code is not found")
        void doesNotPublishClickEventWhenNotFound() throws Exception {

            when(urlService.resolve(TEST_SHORT_CODE))
                    .thenThrow(new UrlNotFoundException(TEST_SHORT_CODE));

            mockMvc.perform(get(REDIRECT_URL, TEST_SHORT_CODE))
                    .andExpect(status().isNotFound());

            verifyNoInteractions(analyticsProducer);
        }

        @Test
        @DisplayName("500 Internal Server Error — unexpected service exception is handled")
        void returnsInternalServerErrorOnUnexpectedException() throws Exception {

            when(urlService.resolve(TEST_SHORT_CODE))
                    .thenThrow(new RuntimeException("Redis is down"));

            mockMvc.perform(get(REDIRECT_URL, TEST_SHORT_CODE))
                    .andExpect(status().isInternalServerError());

            verifyNoInteractions(analyticsProducer);
        }
    }
}