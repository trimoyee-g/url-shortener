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

    private static final String STATS_URL =
            "/api/v1/urls/{shortCode}/stats?days=7";

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

        AnalyticsResponse.CountryCount india =
                AnalyticsResponse.CountryCount.builder()
                        .country("India")
                        .clicks(15)
                        .build();

        AnalyticsResponse.CountryCount usa =
                AnalyticsResponse.CountryCount.builder()
                        .country("USA")
                        .clicks(10)
                        .build();

        AnalyticsResponse.ReferrerCount google =
                AnalyticsResponse.ReferrerCount.builder()
                        .referrer("google.com")
                        .clicks(18)
                        .build();

        AnalyticsResponse.ReferrerCount linkedin =
                AnalyticsResponse.ReferrerCount.builder()
                        .referrer("linkedin.com")
                        .clicks(7)
                        .build();

        Map<String, Long> clicksByDay = new LinkedHashMap<>();
        clicksByDay.put("2026-05-08", 5L);
        clicksByDay.put("2026-05-09", 8L);
        clicksByDay.put("2026-05-10", 12L);

        Map<String, Long> deviceBreakdown = new LinkedHashMap<>();
        deviceBreakdown.put("DESKTOP", 10L);
        deviceBreakdown.put("MOBILE", 12L);
        deviceBreakdown.put("TABLET", 3L);

        return AnalyticsResponse.builder()
                .shortCode("abc123")
                .shortUrl("http://localhost:8080/abc123")
                .totalClicks(25)
                .uniqueVisitors(12)
                .topCountries(List.of(india, usa))
                .topReferrers(List.of(google, linkedin))
                .deviceBreakdown(deviceBreakdown)
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

            when(analyticsService.getStats("abc123", 7))
                    .thenReturn(buildFullResponse());

            mockMvc.perform(get("/api/v1/urls/{shortCode}/stats", "abc123")
                            .param("days", "7"))
                    .andExpect(status().isOk())

                    // top-level fields
                    .andExpect(jsonPath("$.shortCode").value("abc123"))
                    .andExpect(jsonPath("$.shortUrl")
                            .value("http://localhost:8080/abc123"))
                    .andExpect(jsonPath("$.totalClicks").value(25))
                    .andExpect(jsonPath("$.uniqueVisitors").value(12))

                    // topCountries
                    .andExpect(jsonPath("$.topCountries[0].country")
                            .value("India"))
                    .andExpect(jsonPath("$.topCountries[0].clicks")
                            .value(15))

                    .andExpect(jsonPath("$.topCountries[1].country")
                            .value("USA"))
                    .andExpect(jsonPath("$.topCountries[1].clicks")
                            .value(10))

                    // topReferrers
                    .andExpect(jsonPath("$.topReferrers[0].referrer")
                            .value("google.com"))
                    .andExpect(jsonPath("$.topReferrers[0].clicks")
                            .value(18))

                    // deviceBreakdown
                    .andExpect(jsonPath("$.deviceBreakdown.DESKTOP")
                            .value(10))
                    .andExpect(jsonPath("$.deviceBreakdown.MOBILE")
                            .value(12))
                    .andExpect(jsonPath("$.deviceBreakdown.TABLET")
                            .value(3))

                    // clicksByDay
                    .andExpect(jsonPath("$.clicksByDay['2026-05-08']")
                            .value(5))
                    .andExpect(jsonPath("$.clicksByDay['2026-05-09']")
                            .value(8))
                    .andExpect(jsonPath("$.clicksByDay['2026-05-10']")
                            .value(12));

            verify(analyticsService, times(1))
                    .getStats("abc123", 7);
        }

        @Test
        @DisplayName("404 Not Found — unknown short code returns 404")
        void returnsNotFoundForUnknownShortCode() throws Exception {

            when(analyticsService.getStats("unknown", 7))
                    .thenThrow(new UrlNotFoundException("unknown"));

            mockMvc.perform(get("/api/v1/urls/{shortCode}/stats", "unknown")
                            .param("days", "7"))
                    .andExpect(status().isNotFound());

            verify(analyticsService, times(1))
                    .getStats("unknown", 7);
        }

        @Test
        @DisplayName("500 Internal Server Error — unexpected service exception is handled")
        void returnsInternalServerErrorOnUnexpectedException() throws Exception {

            when(analyticsService.getStats("abc123", 7))
                    .thenThrow(new RuntimeException("DB connection lost"));

            mockMvc.perform(get("/api/v1/urls/{shortCode}/stats", "abc123")
                            .param("days", "7"))
                    .andExpect(status().isInternalServerError());

            verify(analyticsService, times(1))
                    .getStats("abc123", 7);
        }

        @Test
        @DisplayName("200 OK — response with zero clicks is serialized correctly")
        void handlesZeroClicksGracefully() throws Exception {

            Map<String, Long> devices = new LinkedHashMap<>();
            devices.put("DESKTOP", 0L);
            devices.put("MOBILE", 0L);
            devices.put("TABLET", 0L);

            AnalyticsResponse emptyStats = AnalyticsResponse.builder()
                    .shortCode("newurl")
                    .shortUrl("http://localhost:8080/newurl")
                    .totalClicks(0)
                    .uniqueVisitors(0)
                    .topCountries(List.of())
                    .topReferrers(List.of())
                    .deviceBreakdown(devices)
                    .clicksByDay(new LinkedHashMap<>())
                    .build();

            when(analyticsService.getStats("newurl", 7))
                    .thenReturn(emptyStats);

            mockMvc.perform(get("/api/v1/urls/{shortCode}/stats", "newurl")
                            .param("days", "7"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalClicks").value(0))
                    .andExpect(jsonPath("$.uniqueVisitors").value(0))
                    .andExpect(jsonPath("$.topCountries").isEmpty())
                    .andExpect(jsonPath("$.topReferrers").isEmpty())
                    .andExpect(jsonPath("$.clicksByDay").isEmpty());

            verify(analyticsService, times(1))
                    .getStats("newurl", 7);
        }

        @Test
        @DisplayName("delegates to AnalyticsService with the exact short code from the path")
        void delegatesToServiceWithCorrectShortCode() throws Exception {

            AnalyticsResponse response = AnalyticsResponse.builder()
                    .shortCode("xyz789")
                    .shortUrl("http://localhost:8080/xyz789")
                    .totalClicks(1)
                    .uniqueVisitors(1)
                    .topCountries(List.of())
                    .topReferrers(List.of())
                    .deviceBreakdown(new LinkedHashMap<>())
                    .clicksByDay(new LinkedHashMap<>())
                    .build();

            when(analyticsService.getStats("xyz789", 7))
                    .thenReturn(response);

            mockMvc.perform(get("/api/v1/urls/{shortCode}/stats", "xyz789")
                            .param("days", "7"))
                    .andExpect(status().isOk());

            verify(analyticsService).getStats("xyz789", 7);
            verify(analyticsService, never())
                    .getStats("abc123", 7);
        }
    }
}