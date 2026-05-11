package com.urlshortener.url_shortener.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.url_shortener.dto.*;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.*;

@DisplayName("E2E Tests")
class UrlShortenerE2ETest extends BaseE2ETest {

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String registerAndLogin(String email, String password) {
        RegisterRequest register = RegisterRequest.builder()
                .email(email)
                .password(password)
                .build();

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(register)
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(201);

        LoginRequest login = LoginRequest.builder()
                .email(email)
                .password(password)
                .build();

        return RestAssured.given()
                .contentType(ContentType.JSON)
                .body(login)
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("accessToken");
    }

    private String shorten(String token, String longUrl) {
        ShortenRequest request = ShortenRequest.builder()
                .longUrl(longUrl)
                .build();

        return RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/urls/shorten")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getString("shortCode");
    }

    private void waitForStats(String token, String shortCode, int expectedClicks) {
        Awaitility.await()
                .atMost(15, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        RestAssured.given()
                                .header("Authorization", "Bearer " + token)
                                .when()
                                .get("/api/v1/urls/{shortCode}/stats", shortCode)
                                .then()
                                .statusCode(200)
                                .body("totalClicks", equalTo(expectedClicks))
                );
    }

    // =========================================================================
    // Auth
    // =========================================================================

    @Test
    @DisplayName("user can register and login successfully")
    void registerAndLoginFlow() {
        RegisterRequest register = RegisterRequest.builder()
                .email("e2euser@example.com")
                .password("Password123!")
                .build();

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(register)
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(201);

        LoginRequest login = LoginRequest.builder()
                .email("e2euser@example.com")
                .password("Password123!")
                .build();

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(login)
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .body("accessToken", not(blankOrNullString()))
                .body("refreshToken", not(blankOrNullString()));
    }

    @Test
    @DisplayName("duplicate registration returns 409")
    void duplicateRegistrationReturnsConflict() {
        registerAndLogin("duplicate@example.com", "Password123!");

        RegisterRequest duplicate = RegisterRequest.builder()
                .email("duplicate@example.com")
                .password("Password123!")
                .build();

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(duplicate)
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(409);
    }

    @Test
    @DisplayName("login with invalid credentials returns 401")
    void invalidLoginReturnsUnauthorized() {
        registerAndLogin("invalid@example.com", "Password123!");

        LoginRequest login = LoginRequest.builder()
                .email("invalid@example.com")
                .password("WrongPassword!")
                .build();

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(login)
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(401);
    }

    // =========================================================================
    // URL Shortening
    // =========================================================================

    @Test
    @DisplayName("authenticated user can shorten URL")
    void authenticatedUserCanShortenUrl() {
        String token = registerAndLogin("shorten@example.com", "Password123!");

        ShortenRequest request = ShortenRequest.builder()
                .longUrl("https://google.com")
                .build();

        RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/urls/shorten")
                .then()
                .statusCode(201)
                .body("shortCode", not(blankOrNullString()))
                .body("longUrl", equalTo("https://google.com"));
    }

    @Test
    @DisplayName("unauthenticated shorten request returns 401")
    void unauthenticatedShortenReturns401() {
        ShortenRequest request = ShortenRequest.builder()
                .longUrl("https://google.com")
                .build();

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/urls/shorten")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("user can retrieve only their own URLs")
    void userCanRetrieveOwnUrlsOnly() throws InterruptedException {
        String aliceToken = registerAndLogin("alicee2e@example.com", "Password123!");
        shorten(aliceToken, "https://google.com");

        Thread.sleep(1200);

        shorten(aliceToken, "https://github.com");

        String bobToken = registerAndLogin("bobe2e@example.com", "Password123!");

        RestAssured.given()
                .header("Authorization", "Bearer " + bobToken)
                .when()
                .get("/api/v1/urls")
                .then()
                .statusCode(200)
                .body("$.size()", equalTo(0));

        Awaitility.await()
                .atMost(15, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        RestAssured.given()
                                .header("Authorization", "Bearer " + aliceToken)
                                .when()
                                .get("/api/v1/urls")
                                .then()
                                .statusCode(200)
                                .body("$.size()", equalTo(2))
                );
    }

    // =========================================================================
    // Redirect
    // =========================================================================

    @Test
    @DisplayName("short URL redirects to original URL")
    void shortUrlRedirectsCorrectly() {
        String token = registerAndLogin("redirecte2e@example.com", "Password123!");
        String shortCode = shorten(token, "https://google.com");

        RestAssured.given()
                .redirects().follow(false)
                .when()
                .get("/{shortCode}", shortCode)
                .then()
                .statusCode(302)
                .header("Location", equalTo("https://google.com"));
    }

    @Test
    @DisplayName("unknown short code returns 404")
    void unknownShortCodeReturns404() {
        RestAssured.given()
                .when()
                .get("/{shortCode}", "doesnotexist")
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("multiple redirects correctly update analytics")
    void redirectsUpdateAnalytics() {
        String token = registerAndLogin("analyticse2e@example.com", "Password123!");
        String shortCode = shorten(token, "https://google.com");

        for (int i = 0; i < 5; i++) {
            RestAssured.given()
                    .redirects().follow(false)
                    .when()
                    .get("/{shortCode}", shortCode)
                    .then()
                    .statusCode(302);
        }

        waitForStats(token, shortCode, 5);
    }

    // =========================================================================
    // Delete
    // =========================================================================

    @Test
    @DisplayName("owner can delete URL and it becomes inaccessible")
    void ownerCanDeleteUrl() {
        String token = registerAndLogin("deletee2e@example.com", "Password123!");
        String shortCode = shorten(token, "https://google.com");

        Awaitility.await()
                .atMost(15, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        RestAssured.given()
                                .header("Authorization", "Bearer " + token)
                                .when()
                                .delete("/api/v1/urls/{shortCode}", shortCode)
                                .then()
                                .statusCode(anyOf(equalTo(204), equalTo(404)))
                );

        RestAssured.given()
                .when()
                .get("/{shortCode}", shortCode)
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("non-owner cannot delete another user's URL")
    void nonOwnerCannotDeleteAnotherUsersUrl() {
        String aliceToken = registerAndLogin("alice-delete@example.com", "Password123!");
        String shortCode = shorten(aliceToken, "https://google.com");

        String bobToken = registerAndLogin("bob-delete@example.com", "Password123!");

        Awaitility.await()
                .atMost(15, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        RestAssured.given()
                                .header("Authorization", "Bearer " + bobToken)
                                .when()
                                .delete("/api/v1/urls/{shortCode}", shortCode)
                                .then()
                                .statusCode(403)
                );
    }

    // =========================================================================
    // Analytics
    // =========================================================================

    @Test
    @DisplayName("stats endpoint returns correct analytics")
    void statsEndpointReturnsCorrectAnalytics() {
        String token = registerAndLogin("stats-e2e@example.com", "Password123!");
        String shortCode = shorten(token, "https://google.com");

        for (int i = 0; i < 3; i++) {
            RestAssured.given()
                    .redirects().follow(false)
                    .when()
                    .get("/{shortCode}", shortCode)
                    .then()
                    .statusCode(302);
        }

        Awaitility.await()
                .atMost(15, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        RestAssured.given()
                                .header("Authorization", "Bearer " + token)
                                .when()
                                .get("/api/v1/urls/{shortCode}/stats", shortCode)
                                .then()
                                .statusCode(200)
                                .body("shortCode", equalTo(shortCode))
                                .body("totalClicks", equalTo(3))
                );
    }
}