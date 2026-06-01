package com.urlshortener.url_shortener.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.url_shortener.dto.PagedResponse;
import com.urlshortener.url_shortener.dto.ShortenRequest;
import com.urlshortener.url_shortener.dto.UrlResponse;
import com.urlshortener.url_shortener.exception.ForbiddenException;
import com.urlshortener.url_shortener.exception.UrlNotFoundException;
import com.urlshortener.url_shortener.filter.JwtAuthFilter;
import com.urlshortener.url_shortener.service.UrlService;
import com.urlshortener.url_shortener.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UrlController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("UrlController")
class UrlControllerTests {

    private static final String BASE_URL       = "/api/v1/urls";
    private static final String SHORTEN_URL    = "/api/v1/urls/shorten";
    private static final String SHORT_CODE_URL = "/api/v1/urls/{shortCode}";
    private static final String QR_URL         = "/api/v1/urls/{shortCode}/qr";

    private static final String TEST_USER       = "alice@example.com";
    private static final String TEST_SHORT_CODE = "abc123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UrlService urlService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private UrlResponse buildUrlResponse() {
        return UrlResponse.builder()
                .shortCode(TEST_SHORT_CODE)
                .shortUrl("http://localhost:8080/" + TEST_SHORT_CODE)
                .longUrl("https://google.com")
                .build();
    }

    // =========================================================================
    // POST /shorten
    // =========================================================================

    @Nested
    @DisplayName("POST /shorten")
    class Shorten {

        @Test
        @WithMockUser(username = TEST_USER)
        @DisplayName("201 Created — valid request returns shortened URL")
        void returnsShortenedUrl() throws Exception {

            ShortenRequest request = ShortenRequest.builder()
                    .longUrl("https://google.com")
                    .build();

            when(urlService.shorten(any(ShortenRequest.class), eq(TEST_USER), anyString()))
                    .thenReturn(buildUrlResponse());

            mockMvc.perform(post(SHORTEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.shortCode").value(TEST_SHORT_CODE))
                    .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/" + TEST_SHORT_CODE))
                    .andExpect(jsonPath("$.longUrl").value("https://google.com"));

            verify(urlService, times(1)).shorten(any(ShortenRequest.class), eq(TEST_USER), anyString());
        }

        @Test
        @WithMockUser(username = TEST_USER)
        @DisplayName("201 Created — passes remote IP from X-Forwarded-For to service")
        void passesRemoteIpToService() throws Exception {

            ShortenRequest request = ShortenRequest.builder()
                    .longUrl("https://google.com")
                    .build();

            when(urlService.shorten(any(ShortenRequest.class), eq(TEST_USER), eq("203.0.113.5")))
                    .thenReturn(buildUrlResponse());

            mockMvc.perform(post(SHORTEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .header("X-Forwarded-For", "203.0.113.5"))
                    .andExpect(status().isCreated());

            verify(urlService, times(1))
                    .shorten(any(ShortenRequest.class), eq(TEST_USER), eq("203.0.113.5"));
        }

        @Test
        @WithMockUser(username = TEST_USER)
        @DisplayName("201 Created — uses first IP when X-Forwarded-For has multiple addresses")
        void extractsFirstIpFromXForwardedFor() throws Exception {

            ShortenRequest request = ShortenRequest.builder()
                    .longUrl("https://google.com")
                    .build();

            when(urlService.shorten(any(ShortenRequest.class), eq(TEST_USER), eq("10.0.0.1")))
                    .thenReturn(buildUrlResponse());

            mockMvc.perform(post(SHORTEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .header("X-Forwarded-For", "10.0.0.1, 172.16.0.1, 192.168.1.1"))
                    .andExpect(status().isCreated());

            verify(urlService, times(1))
                    .shorten(any(ShortenRequest.class), eq(TEST_USER), eq("10.0.0.1"));
        }

        @Test
        @WithMockUser(username = TEST_USER)
        @DisplayName("400 Bad Request — null URL field fails validation")
        void rejectsMissingUrl() throws Exception {

            ShortenRequest request = ShortenRequest.builder()
                    .longUrl(null)
                    .build();

            mockMvc.perform(post(SHORTEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(urlService);
        }

        @Test
        @WithMockUser(username = TEST_USER)
        @DisplayName("400 Bad Request — blank URL field fails validation")
        void rejectsBlankUrl() throws Exception {

            ShortenRequest request = ShortenRequest.builder()
                    .longUrl("   ")
                    .build();

            mockMvc.perform(post(SHORTEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(urlService);
        }

        @Test
        @WithMockUser(username = TEST_USER)
        @DisplayName("500 Internal Server Error — unexpected service exception is handled")
        void returnsInternalServerErrorOnUnexpectedException() throws Exception {

            ShortenRequest request = ShortenRequest.builder()
                    .longUrl("https://google.com")
                    .build();

            when(urlService.shorten(any(ShortenRequest.class), anyString(), anyString()))
                    .thenThrow(new RuntimeException("DB is down"));

            mockMvc.perform(post(SHORTEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isInternalServerError());
        }
    }

    // =========================================================================
    // GET /
    // =========================================================================

    @Nested
    @DisplayName("GET /")
    class MyUrls {

        @Test
        @WithMockUser(username = TEST_USER)
        @DisplayName("200 OK — returns list of URLs for the authenticated user")
        void returnsUserUrls() throws Exception {

            UrlResponse second = UrlResponse.builder()
                    .shortCode("xyz789")
                    .shortUrl("http://localhost:8080/xyz789")
                    .longUrl("https://github.com")
                    .build();

            PagedResponse<UrlResponse> paged = PagedResponse.<UrlResponse>builder()
                    .content(List.of(buildUrlResponse(), second))
                    .page(0).pageSize(10).totalElements(2).totalPages(1).last(true)
                    .build();

            when(urlService.getUserUrls(TEST_USER, 0)).thenReturn(paged);

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].shortCode").value(TEST_SHORT_CODE))
                    .andExpect(jsonPath("$.content[1].shortCode").value("xyz789"));

            verify(urlService, times(1)).getUserUrls(TEST_USER, 0);
        }

        @Test
        @WithMockUser(username = TEST_USER)
        @DisplayName("200 OK — returns empty list when user has no URLs")
        void returnsEmptyListWhenNoUrls() throws Exception {

            PagedResponse<UrlResponse> empty = PagedResponse.<UrlResponse>builder()
                    .content(List.of()).page(0).pageSize(10).totalElements(0).totalPages(0).last(true)
                    .build();

            when(urlService.getUserUrls(TEST_USER, 0)).thenReturn(empty);

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0));

            verify(urlService, times(1)).getUserUrls(TEST_USER, 0);
        }

        @Test
        @WithMockUser(username = "bob@example.com")
        @DisplayName("delegates to service using authenticated user's username only")
        void delegatesWithCorrectUsername() throws Exception {

            PagedResponse<UrlResponse> empty = PagedResponse.<UrlResponse>builder()
                    .content(List.of()).page(0).pageSize(10).totalElements(0).totalPages(0).last(true)
                    .build();

            when(urlService.getUserUrls("bob@example.com", 0)).thenReturn(empty);

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk());

            verify(urlService).getUserUrls("bob@example.com", 0);
            verify(urlService, never()).getUserUrls(TEST_USER, 0);
        }
    }

    // =========================================================================
    // DELETE /{shortCode}
    // =========================================================================

    @Nested
    @DisplayName("DELETE /{shortCode}")
    class Delete {

        @Test
        @WithMockUser(username = TEST_USER)
        @DisplayName("204 No Content — successfully deletes own short URL")
        void deletesOwnUrl() throws Exception {

            mockMvc.perform(delete(SHORT_CODE_URL, TEST_SHORT_CODE))
                    .andExpect(status().isNoContent());

            verify(urlService, times(1)).delete(TEST_SHORT_CODE, TEST_USER);
        }

        @Test
        @WithMockUser(username = TEST_USER)
        @DisplayName("404 Not Found — deleting a non-existent short code returns 404")
        void returnsNotFoundForUnknownShortCode() throws Exception {

            doThrow(new UrlNotFoundException(TEST_SHORT_CODE))
                    .when(urlService).delete(TEST_SHORT_CODE, TEST_USER);

            mockMvc.perform(delete(SHORT_CODE_URL, TEST_SHORT_CODE))
                    .andExpect(status().isNotFound());

            verify(urlService, times(1)).delete(TEST_SHORT_CODE, TEST_USER);
        }

        @Test
        @WithMockUser(username = TEST_USER)
        @DisplayName("403 Forbidden — deleting another user's URL returns 403")
        void returnsForbiddenWhenDeletingAnotherUsersUrl() throws Exception {

            doThrow(new ForbiddenException("You do not own this URL"))
                    .when(urlService).delete(TEST_SHORT_CODE, TEST_USER);

            mockMvc.perform(delete(SHORT_CODE_URL, TEST_SHORT_CODE))
                    .andExpect(status().isForbidden());

            verify(urlService, times(1)).delete(TEST_SHORT_CODE, TEST_USER);
        }
    }

    // =========================================================================
    // GET /{shortCode}/qr
    // =========================================================================

    @Nested
    @DisplayName("GET /{shortCode}/qr")
    class Qr {

        @Test
        @WithMockUser(username = TEST_USER)
        @DisplayName("200 OK — returns PNG bytes with correct content type")
        void returnsPngWithCorrectContentType() throws Exception {

            byte[] fakePng = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}; // PNG magic bytes
            when(urlService.generateQr(TEST_SHORT_CODE, 300)).thenReturn(fakePng);

            mockMvc.perform(get(QR_URL, TEST_SHORT_CODE))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.IMAGE_PNG))
                    .andExpect(content().bytes(fakePng));

            verify(urlService, times(1)).generateQr(TEST_SHORT_CODE, 300);
        }

        @Test
        @WithMockUser(username = TEST_USER)
        @DisplayName("200 OK — custom size parameter is forwarded to service")
        void forwardsCustomSizeToService() throws Exception {

            byte[] fakePng = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};
            when(urlService.generateQr(TEST_SHORT_CODE, 600)).thenReturn(fakePng);

            mockMvc.perform(get(QR_URL, TEST_SHORT_CODE)
                            .param("size", "600"))
                    .andExpect(status().isOk());

            verify(urlService, times(1)).generateQr(TEST_SHORT_CODE, 600);
        }

        @Test
        @WithMockUser(username = TEST_USER)
        @DisplayName("200 OK — defaults to size 300 when no size param provided")
        void defaultsSizeTo300() throws Exception {

            byte[] fakePng = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};
            when(urlService.generateQr(TEST_SHORT_CODE, 300)).thenReturn(fakePng);

            mockMvc.perform(get(QR_URL, TEST_SHORT_CODE))
                    .andExpect(status().isOk());

            verify(urlService, times(1)).generateQr(TEST_SHORT_CODE, 300);
            verify(urlService, never()).generateQr(eq(TEST_SHORT_CODE), intThat(s -> s != 300));
        }

        @Test
        @WithMockUser(username = TEST_USER)
        @DisplayName("404 Not Found — QR for unknown short code returns 404")
        void returnsNotFoundForUnknownShortCode() throws Exception {

            when(urlService.generateQr(TEST_SHORT_CODE, 300))
                    .thenThrow(new UrlNotFoundException(TEST_SHORT_CODE));

            mockMvc.perform(get(QR_URL, TEST_SHORT_CODE))
                    .andExpect(status().isNotFound());

            verify(urlService, times(1)).generateQr(TEST_SHORT_CODE, 300);
        }
    }
}