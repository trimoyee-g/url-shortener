package com.urlshortener.url_shortener.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.url_shortener.dto.AuthResponse;
import com.urlshortener.url_shortener.dto.LoginRequest;
import com.urlshortener.url_shortener.dto.RegisterRequest;
import com.urlshortener.url_shortener.exception.GlobalExceptionHandler;
import org.springframework.security.authentication.BadCredentialsException;
import com.urlshortener.url_shortener.filter.JwtAuthFilter;
import com.urlshortener.url_shortener.service.AuthService;
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
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AuthController.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AuthController")
class AuthControllerTests {

    private static final String REGISTER_URL = "/api/v1/auth/register";
    private static final String LOGIN_URL    = "/api/v1/auth/login";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private AuthResponse buildAuthResponse() {
        return AuthResponse.builder()
                .accessToken("eyJhbGciOiJIUzI1NiJ9.test.accessToken")
                .refreshToken("eyJhbGciOiJIUzI1NiJ9.test.refreshToken")
                .tokenType("Bearer")
                .email("alice@example.com")
                .name("Alice")
                .build();
    }

    // =========================================================================
    // POST /register
    // =========================================================================

    @Nested
    @DisplayName("POST /register")
    class Register {

        @Test
        @DisplayName("201 Created — valid registration request returns token")
        void returnsTokenOnValidRegistration() throws Exception {

            RegisterRequest request = RegisterRequest.builder()
                    .email("alice@example.com")
                    .password("SecurePass123!")
                    .build();

            when(authService.register(any(RegisterRequest.class)))
                    .thenReturn(buildAuthResponse());

            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.accessToken").value("eyJhbGciOiJIUzI1NiJ9.test.accessToken"))
                    .andExpect(jsonPath("$.refreshToken").value("eyJhbGciOiJIUzI1NiJ9.test.refreshToken"))
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.email").value("alice@example.com"))
                    .andExpect(jsonPath("$.name").value("Alice"));

            verify(authService, times(1)).register(any(RegisterRequest.class));
        }

        @Test
        @DisplayName("400 Bad Request — null email fails validation")
        void rejectsNullEmail() throws Exception {

            RegisterRequest request = RegisterRequest.builder()
                    .email(null)
                    .password("SecurePass123!")
                    .build();

            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("400 Bad Request — blank email fails validation")
        void rejectsBlankEmail() throws Exception {

            RegisterRequest request = RegisterRequest.builder()
                    .email("   ")
                    .password("SecurePass123!")
                    .build();

            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("400 Bad Request — null password fails validation")
        void rejectsNullPassword() throws Exception {

            RegisterRequest request = RegisterRequest.builder()
                    .email("alice@example.com")
                    .password(null)
                    .build();

            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("409 Conflict — registering an already-existing email returns 409")
        void returnsConflictWhenEmailAlreadyExists() throws Exception {

            RegisterRequest request = RegisterRequest.builder()
                    .email("alice@example.com")
                    .password("SecurePass123!")
                    .build();

            when(authService.register(any(RegisterRequest.class)))
                    .thenThrow(new com.urlshortener.url_shortener.exception.AliasAlreadyExistsException(
                            "Email already registered: alice@example.com"));

            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());

            verify(authService, times(1)).register(any(RegisterRequest.class));
        }

        @Test
        @DisplayName("500 Internal Server Error — unexpected service exception is handled")
        void returnsInternalServerErrorOnUnexpectedException() throws Exception {

            RegisterRequest request = RegisterRequest.builder()
                    .email("alice@example.com")
                    .password("SecurePass123!")
                    .build();

            when(authService.register(any(RegisterRequest.class)))
                    .thenThrow(new RuntimeException("DB is down"));

            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isInternalServerError());
        }
    }

    // =========================================================================
    // POST /login
    // =========================================================================

    @Nested
    @DisplayName("POST /login")
    class Login {

        @Test
        @DisplayName("200 OK — valid credentials return token")
        void returnsTokenOnValidCredentials() throws Exception {

            LoginRequest request = LoginRequest.builder()
                    .email("alice@example.com")
                    .password("SecurePass123!")
                    .build();

            when(authService.login(any(LoginRequest.class)))
                    .thenReturn(buildAuthResponse());

            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("eyJhbGciOiJIUzI1NiJ9.test.accessToken"))
                    .andExpect(jsonPath("$.refreshToken").value("eyJhbGciOiJIUzI1NiJ9.test.refreshToken"))
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.email").value("alice@example.com"))
                    .andExpect(jsonPath("$.name").value("Alice"));

            verify(authService, times(1)).login(any(LoginRequest.class));
        }

        @Test
        @DisplayName("400 Bad Request — null email fails validation")
        void rejectsNullEmail() throws Exception {

            LoginRequest request = LoginRequest.builder()
                    .email(null)
                    .password("SecurePass123!")
                    .build();

            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("400 Bad Request — null password fails validation")
        void rejectsNullPassword() throws Exception {

            LoginRequest request = LoginRequest.builder()
                    .email("alice@example.com")
                    .password(null)
                    .build();

            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("401 Unauthorized — wrong credentials return 401")
        void returnsUnauthorizedOnBadCredentials() throws Exception {

            LoginRequest request = LoginRequest.builder()
                    .email("alice@example.com")
                    .password("WrongPassword!")
                    .build();

            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new BadCredentialsException("Invalid email or password"));

            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verify(authService, times(1)).login(any(LoginRequest.class));
        }

        @Test
        @DisplayName("500 Internal Server Error — unexpected service exception is handled")
        void returnsInternalServerErrorOnUnexpectedException() throws Exception {

            LoginRequest request = LoginRequest.builder()
                    .email("alice@example.com")
                    .password("SecurePass123!")
                    .build();

            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new RuntimeException("DB is down"));

            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isInternalServerError());
        }
    }
}