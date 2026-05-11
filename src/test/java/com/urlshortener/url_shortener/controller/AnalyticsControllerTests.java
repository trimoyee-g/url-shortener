package com.urlshortener.url_shortener.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.url_shortener.dto.AnalyticsResponse;
import com.urlshortener.url_shortener.exception.UrlNotFoundException;
import com.urlshortener.url_shortener.filter.JwtAuthFilter;
import com.urlshortener.url_shortener.service.AnalyticsService;
import com.urlshortener.url_shortener.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnalyticsController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AnalyticsController")
class AnalyticsControllerTests {

    private static final String STATS_URL = "/api/v1/urls/{shortCode}/stats";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AnalyticsService analyticsService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    // -------------------------------------------------------------------------
    // Test data helpers
    // -------------------------------------------------------------------------

    private AnalyticsResponse buildFullResponse() {
        AnalyticsResponse.CountryCount india = AnalyticsResponse.CountryCount.builder()
                .country("India")
                .clicks(15)
                .build();

        AnalyticsResponse.CountryCount usa = AnalyticsResponse.CountryCount.builder()
                .country("USA")
                .clicks(10)
                .build();

        // LinkedHashMap preserves insertion order → deterministic JSON assertions
        Map<String, Long> clicksByDay = new LinkedHashMap<>();
        clicksByDay.put("2026-05-08", 5L);
        clicksByDay.put("2026-05-09", 8L);
        clicksByDay.put("2026-05-10", 12L);

        return AnalyticsResponse.builder()
                .shortCode("abc123")
                .shortUrl("http://localhost:8080/abc123")
                .longUrl("https://google.com")
                .totalClicks(25)
                .uniqueIps(12)
                .topCountries(List.of(india, usa))
                .clicksByDay(clicksByDay)
                .build();
    }

    // =========================================================================
    // GET /{shortCode}/stats
    // =========================================================================

    @Nested
    @DisplayName("GET /{shortCode}/stats")
    class GetStats {

        @Test
        @DisplayName("200 OK — returns full analytics payload for a known short code")
        void returnsFullAnalyticsPayload() throws Exception {

            when(analyticsService.getStats("abc123")).thenReturn(buildFullResponse());

            mockMvc.perform(get(STATS_URL, "abc123"))
                    .andExpect(status().isOk())
                    // top-level fields
                    .andExpect(jsonPath("$.shortCode").value("abc123"))
                    .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/abc123"))
                    .andExpect(jsonPath("$.longUrl").value("https://google.com"))
                    .andExpect(jsonPath("$.totalClicks").value(25))
                    .andExpect(jsonPath("$.uniqueIps").value(12))
                    // topCountries array
                    .andExpect(jsonPath("$.topCountries[0].country").value("India"))
                    .andExpect(jsonPath("$.topCountries[0].clicks").value(15))
                    .andExpect(jsonPath("$.topCountries[1].country").value("USA"))
                    .andExpect(jsonPath("$.topCountries[1].clicks").value(10))
                    // clicksByDay map
                    .andExpect(jsonPath("$.clicksByDay['2026-05-08']").value(5))
                    .andExpect(jsonPath("$.clicksByDay['2026-05-09']").value(8))
                    .andExpect(jsonPath("$.clicksByDay['2026-05-10']").value(12));

            verify(analyticsService, times(1)).getStats("abc123");
        }

        @Test
        @DisplayName("404 Not Found — unknown short code returns 404")
        void returnsNotFoundForUnknownShortCode() throws Exception {

            when(analyticsService.getStats("unknown"))
                    .thenThrow(new UrlNotFoundException("Short code 'unknown' not found"));

            mockMvc.perform(get(STATS_URL, "unknown"))
                    .andExpect(status().isNotFound());

            verify(analyticsService, times(1)).getStats("unknown");
        }

        @Test
        @DisplayName("500 Internal Server Error — unexpected service exception is handled")
        void returnsInternalServerErrorOnUnexpectedException() throws Exception {

            when(analyticsService.getStats("abc123"))
                    .thenThrow(new RuntimeException("DB connection lost"));

            mockMvc.perform(get(STATS_URL, "abc123"))
                    .andExpect(status().isInternalServerError());

            verify(analyticsService, times(1)).getStats("abc123");
        }

        @Test
        @DisplayName("200 OK — response with zero clicks is serialized correctly")
        void handlesZeroClicksGracefully() throws Exception {

            AnalyticsResponse emptyStats = AnalyticsResponse.builder()
                    .shortCode("newurl")
                    .shortUrl("http://localhost:8080/newurl")
                    .longUrl("https://example.com")
                    .totalClicks(0)
                    .uniqueIps(0)
                    .topCountries(List.of())
                    .clicksByDay(new LinkedHashMap<>())
                    .build();

            when(analyticsService.getStats("newurl")).thenReturn(emptyStats);

            mockMvc.perform(get(STATS_URL, "newurl"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalClicks").value(0))
                    .andExpect(jsonPath("$.uniqueIps").value(0))
                    .andExpect(jsonPath("$.topCountries").isEmpty())
                    .andExpect(jsonPath("$.clicksByDay").isEmpty());

            verify(analyticsService, times(1)).getStats("newurl");
        }

        @Test
        @DisplayName("delegates to AnalyticsService with the exact short code from the path")
        void delegatesToServiceWithCorrectShortCode() throws Exception {

            when(analyticsService.getStats("xyz789")).thenReturn(
                    AnalyticsResponse.builder()
                            .shortCode("xyz789")
                            .shortUrl("http://localhost:8080/xyz789")
                            .longUrl("https://example.org")
                            .totalClicks(1)
                            .uniqueIps(1)
                            .topCountries(List.of())
                            .clicksByDay(new LinkedHashMap<>())
                            .build()
            );

            mockMvc.perform(get(STATS_URL, "xyz789"))
                    .andExpect(status().isOk());

            // Strict delegation check — wrong short code must never reach the service
            verify(analyticsService).getStats("xyz789");
            verify(analyticsService, never()).getStats("abc123");
        }
    }
}