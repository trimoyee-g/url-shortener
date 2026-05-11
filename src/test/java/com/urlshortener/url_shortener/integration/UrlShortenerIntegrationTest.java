package com.urlshortener.url_shortener.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.url_shortener.dto.AuthResponse;
import com.urlshortener.url_shortener.dto.LoginRequest;
import com.urlshortener.url_shortener.dto.RegisterRequest;
import com.urlshortener.url_shortener.dto.ShortenRequest;
import com.urlshortener.url_shortener.dto.UrlResponse;
import com.urlshortener.url_shortener.repository.ClickEventRepository;
import com.urlshortener.url_shortener.repository.UrlRepository;
import com.urlshortener.url_shortener.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Integration Tests")
class UrlShortenerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClickEventRepository clickEventRepository;

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private UserRepository userRepository;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String registerAndLogin(String email, String password) throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email(email)
                .password(password)
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated());

        LoginRequest login = LoginRequest.builder()
                .email(email)
                .password(password)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse auth = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        return auth.getAccessToken();
    }

    private String shorten(String token, String longUrl) throws Exception {
        ShortenRequest request = ShortenRequest.builder()
                .longUrl(longUrl)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/urls/shorten")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        UrlResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), UrlResponse.class);
        return response.getShortCode();
    }

    /**
     * Waits until the Kafka consumer has persisted the URL to DB.
     * Required for any test that queries the DB directly after shorten()
     * (e.g. stats, delete, QR) since the DB write is async via Kafka.
     */
    private void waitForUrlInDb(String shortCode) {
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(urlRepository.findByShortCodeAndActiveTrue(shortCode)).isPresent()
        );
    }

    @BeforeEach
    void cleanUp() {
        clickEventRepository.deleteAll();
        urlRepository.deleteAll();
        userRepository.deleteAll();
    }

    // =========================================================================
    // Auth flows
    // =========================================================================

    @Nested
    @DisplayName("Auth")
    class Auth {

        @Test
        @DisplayName("register then login returns a valid JWT")
        void registerThenLoginReturnsJwt() throws Exception {
            String token = registerAndLogin("user@example.com", "Password123!");
            assertThat(token).isNotBlank();
        }

        @Test
        @DisplayName("duplicate registration returns 409")
        void duplicateRegistrationReturnsConflict() throws Exception {
            registerAndLogin("dup@example.com", "Password123!");

            RegisterRequest duplicate = RegisterRequest.builder()
                    .email("dup@example.com")
                    .password("Password123!")
                    .build();

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(duplicate)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("login with wrong password returns 401")
        void loginWithWrongPasswordReturnsUnauthorized() throws Exception {
            registerAndLogin("auth@example.com", "Password123!");

            LoginRequest bad = LoginRequest.builder()
                    .email("auth@example.com")
                    .password("WrongPassword!")
                    .build();

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(bad)))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // Shorten flows
    // =========================================================================

    @Nested
    @DisplayName("Shorten")
    class Shorten {

        @Test
        @DisplayName("authenticated user can shorten a URL and it is eventually persisted to DB")
        void shortenPersistsUrl() throws Exception {
            String token = registerAndLogin("shorten@example.com", "Password123!");
            String shortCode = shorten(token, "https://google.com");

            assertThat(shortCode).isNotBlank();
            // DB write is async via Kafka — wait for consumer
            waitForUrlInDb(shortCode);
        }

        @Test
        @DisplayName("unauthenticated shorten request returns 401")
        void unauthenticatedShortenReturnsUnauthorized() throws Exception {
            ShortenRequest request = ShortenRequest.builder()
                    .longUrl("https://google.com")
                    .build();

            mockMvc.perform(post("/api/v1/urls/shorten")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("user can list their own shortened URLs")
        void userCanListOwnUrls() throws Exception {
            String token = registerAndLogin("list@example.com", "Password123!");

            shorten(token, "https://google.com");
            Thread.sleep(1100); // avoid rate limiter
            shorten(token, "https://github.com");

            // Wait for both to be in DB before listing
            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(urlRepository.findByUserEmailAndActiveTrue("list@example.com"))
                            .hasSize(2)
            );

            mockMvc.perform(get("/api/v1/urls")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("user cannot see another user's URLs")
        void userCannotSeeOtherUsersUrls() throws Exception {
            String aliceToken = registerAndLogin("alice@example.com", "Password123!");
            shorten(aliceToken, "https://google.com");

            String bobToken = registerAndLogin("bob@example.com", "Password123!");

            mockMvc.perform(get("/api/v1/urls")
                            .header("Authorization", "Bearer " + bobToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    // =========================================================================
    // Redirect flows
    // =========================================================================

    @Nested
    @DisplayName("Redirect")
    class Redirect {

        @Test
        @DisplayName("valid short code returns 302 with correct Location header")
        void redirectsToLongUrl() throws Exception {
            String token = registerAndLogin("redirect@example.com", "Password123!");
            String shortCode = shorten(token, "https://google.com");

            // Redis is populated immediately after shorten() — no DB wait needed
            mockMvc.perform(get("/{shortCode}", shortCode))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", "https://google.com"));
        }

        @Test
        @DisplayName("unknown short code returns 404")
        void unknownShortCodeReturnsNotFound() throws Exception {
            mockMvc.perform(get("/{shortCode}", "notexist"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("redirect publishes Kafka event and click is eventually persisted")
        void redirectEventIsConsumedAndPersisted() throws Exception {
            String token = registerAndLogin("kafka@example.com", "Password123!");
            String shortCode = shorten(token, "https://google.com");

            mockMvc.perform(get("/{shortCode}", shortCode)
                            .header("User-Agent", "Mozilla/5.0"))
                    .andExpect(status().isFound());

            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(clickEventRepository.countByShortCode(shortCode)).isEqualTo(1)
            );
        }

        @Test
        @DisplayName("multiple redirects accumulate click count correctly")
        void multipleRedirectsAccumulateClicks() throws Exception {
            String token = registerAndLogin("clicks@example.com", "Password123!");
            String shortCode = shorten(token, "https://google.com");

            for (int i = 0; i < 3; i++) {
                mockMvc.perform(get("/{shortCode}", shortCode))
                        .andExpect(status().isFound());
            }

            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(clickEventRepository.countByShortCode(shortCode)).isEqualTo(3)
            );
        }
    }

    // =========================================================================
    // Delete flows
    // =========================================================================

    @Nested
    @DisplayName("Delete")
    class Delete {

        @Test
        @DisplayName("owner can delete their own URL and it is no longer resolvable")
        void ownerCanDeleteUrl() throws Exception {
            String token = registerAndLogin("delete@example.com", "Password123!");
            String shortCode = shorten(token, "https://google.com");

            // Delete needs the URL in DB first
            waitForUrlInDb(shortCode);

            mockMvc.perform(delete("/api/v1/urls/{shortCode}", shortCode)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNoContent());

            // After delete, Redis cache is evicted — should 404
            mockMvc.perform(get("/{shortCode}", shortCode))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("non-owner cannot delete another user's URL")
        void nonOwnerCannotDeleteUrl() throws Exception {
            String aliceToken = registerAndLogin("alice2@example.com", "Password123!");
            String shortCode = shorten(aliceToken, "https://google.com");

            waitForUrlInDb(shortCode);

            String bobToken = registerAndLogin("bob2@example.com", "Password123!");

            mockMvc.perform(delete("/api/v1/urls/{shortCode}", shortCode)
                            .header("Authorization", "Bearer " + bobToken))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // Analytics flows
    // =========================================================================

    @Nested
    @DisplayName("Analytics")
    class Analytics {

        @Test
        @DisplayName("stats for a URL with no clicks returns zero counts")
        void statsWithNoClicksReturnsZeroes() throws Exception {
            String token = registerAndLogin("stats@example.com", "Password123!");
            String shortCode = shorten(token, "https://google.com");

            // Stats queries DB directly — wait for Kafka consumer to persist
            waitForUrlInDb(shortCode);

            mockMvc.perform(get("/api/v1/urls/{shortCode}/stats", shortCode)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.shortCode").value(shortCode))
                    .andExpect(jsonPath("$.totalClicks").value(0));
        }

        @Test
        @DisplayName("stats reflect clicks after redirects are consumed from Kafka")
        void statsReflectClicksAfterRedirect() throws Exception {
            String token = registerAndLogin("statsclick@example.com", "Password123!");
            String shortCode = shorten(token, "https://google.com");

            // Wait for URL to be in DB before redirecting
            waitForUrlInDb(shortCode);

            mockMvc.perform(get("/{shortCode}", shortCode)).andExpect(status().isFound());
            mockMvc.perform(get("/{shortCode}", shortCode)).andExpect(status().isFound());

            // Wait for both click events to be consumed and persisted
            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                    mockMvc.perform(get("/api/v1/urls/{shortCode}/stats", shortCode)
                                    .header("Authorization", "Bearer " + token))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.totalClicks").value(2))
            );
        }

        @Test
        @DisplayName("stats for unknown short code returns 404")
        void statsForUnknownShortCodeReturnsNotFound() throws Exception {
            String token = registerAndLogin("stats404@example.com", "Password123!");

            mockMvc.perform(get("/api/v1/urls/{shortCode}/stats", "notexist")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound());
        }
    }
}