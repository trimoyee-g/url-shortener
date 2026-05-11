package com.urlshortener.url_shortener.service;

import com.urlshortener.url_shortener.dto.AuthResponse;
import com.urlshortener.url_shortener.dto.LoginRequest;
import com.urlshortener.url_shortener.dto.RegisterRequest;
import com.urlshortener.url_shortener.entity.User;
import com.urlshortener.url_shortener.exception.AlreadyExistsException;
import com.urlshortener.url_shortener.repository.UserRepository;
import com.urlshortener.url_shortener.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    // Constants

    private static final String EMAIL = "test@example.com";
    private static final String PASSWORD = "password123";
    private static final String ENCODED_PASSWORD = "encodedPassword";
    private static final String NAME = "Trimoyee";

    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";

    // Helpers

    private RegisterRequest buildRegisterRequest() {
        return RegisterRequest.builder()
                .email(EMAIL)
                .password(PASSWORD)
                .name(NAME)
                .build();
    }

    private LoginRequest buildLoginRequest() {
        return LoginRequest.builder()
                .email(EMAIL)
                .password(PASSWORD)
                .build();
    }

    private User buildUser() {
        return User.builder()
                .id(1L)
                .email(EMAIL)
                .password(ENCODED_PASSWORD)
                .name(NAME)
                .active(true)
                .build();
    }

    // register()

    @Nested
    @DisplayName("register")
    class RegisterTests {

        @Test
        @DisplayName("registers user successfully and returns auth response")
        void registersSuccessfully() {

            // Arrange
            RegisterRequest request = buildRegisterRequest();

            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);

            when(jwtUtil.generateToken(EMAIL)).thenReturn(ACCESS_TOKEN);
            when(jwtUtil.generateRefreshToken(EMAIL)).thenReturn(REFRESH_TOKEN);

            // Act
            AuthResponse response = authService.register(request);

            // Assert response
            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(response.getRefreshToken()).isEqualTo(REFRESH_TOKEN);
            assertThat(response.getTokenType()).isEqualTo("Bearer");
            assertThat(response.getEmail()).isEqualTo(EMAIL);
            assertThat(response.getName()).isEqualTo(NAME);

            // Assert saved entity
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

            verify(userRepository).save(userCaptor.capture());

            User savedUser = userCaptor.getValue();

            assertThat(savedUser.getEmail()).isEqualTo(EMAIL);
            assertThat(savedUser.getPassword()).isEqualTo(ENCODED_PASSWORD);
            assertThat(savedUser.getName()).isEqualTo(NAME);

            verify(passwordEncoder).encode(PASSWORD);
            verify(jwtUtil).generateToken(EMAIL);
            verify(jwtUtil).generateRefreshToken(EMAIL);
        }

        @Test
        @DisplayName("throws exception when email already exists")
        void throwsWhenEmailAlreadyExists() {

            // Arrange
            RegisterRequest request = buildRegisterRequest();

            when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

            // Act + Assert
            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(AlreadyExistsException.class)
                    .hasMessageContaining("Email already registered");

            verify(userRepository, never()).save(any(User.class));
            verify(passwordEncoder, never()).encode(any());
        }

        @Test
        @DisplayName("encodes password before saving user")
        void encodesPasswordBeforeSaving() {

            // Arrange
            RegisterRequest request = buildRegisterRequest();

            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);

            when(jwtUtil.generateToken(any())).thenReturn(ACCESS_TOKEN);
            when(jwtUtil.generateRefreshToken(any())).thenReturn(REFRESH_TOKEN);

            // Act
            authService.register(request);

            // Assert
            verify(passwordEncoder).encode(PASSWORD);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());

            assertThat(captor.getValue().getPassword())
                    .isEqualTo(ENCODED_PASSWORD);
        }
    }

    // login()

    @Nested
    @DisplayName("login")
    class LoginTests {

        @Test
        @DisplayName("logs in successfully and returns auth response")
        void logsInSuccessfully() {

            // Arrange
            LoginRequest request = buildLoginRequest();
            User user = buildUser();

            when(userRepository.findByEmail(EMAIL))
                    .thenReturn(Optional.of(user));

            when(jwtUtil.generateToken(EMAIL))
                    .thenReturn(ACCESS_TOKEN);

            when(jwtUtil.generateRefreshToken(EMAIL))
                    .thenReturn(REFRESH_TOKEN);

            // Act
            AuthResponse response = authService.login(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(response.getRefreshToken()).isEqualTo(REFRESH_TOKEN);
            assertThat(response.getTokenType()).isEqualTo("Bearer");
            assertThat(response.getEmail()).isEqualTo(EMAIL);
            assertThat(response.getName()).isEqualTo(NAME);

            verify(authenticationManager).authenticate(
                    new UsernamePasswordAuthenticationToken(EMAIL, PASSWORD)
            );
        }

        @Test
        @DisplayName("throws BadCredentialsException when authentication fails")
        void throwsWhenAuthenticationFails() {

            // Arrange
            LoginRequest request = buildLoginRequest();

            doThrow(new BadCredentialsException("Invalid credentials"))
                    .when(authenticationManager)
                    .authenticate(any(UsernamePasswordAuthenticationToken.class));

            // Act + Assert
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessageContaining("Invalid credentials");

            verify(userRepository, never()).findByEmail(any());
        }

        @Test
        @DisplayName("throws exception when authenticated user is not found in database")
        void throwsWhenUserNotFoundAfterAuthentication() {

            // Arrange
            LoginRequest request = buildLoginRequest();

            when(userRepository.findByEmail(EMAIL))
                    .thenReturn(Optional.empty());

            // Act + Assert
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessageContaining("Invalid credentials");

            verify(authenticationManager).authenticate(
                    new UsernamePasswordAuthenticationToken(EMAIL, PASSWORD)
            );
        }

        @Test
        @DisplayName("generates access and refresh tokens during login")
        void generatesTokensDuringLogin() {

            // Arrange
            LoginRequest request = buildLoginRequest();
            User user = buildUser();

            when(userRepository.findByEmail(EMAIL))
                    .thenReturn(Optional.of(user));

            when(jwtUtil.generateToken(EMAIL))
                    .thenReturn(ACCESS_TOKEN);

            when(jwtUtil.generateRefreshToken(EMAIL))
                    .thenReturn(REFRESH_TOKEN);

            // Act
            authService.login(request);

            // Assert
            verify(jwtUtil).generateToken(EMAIL);
            verify(jwtUtil).generateRefreshToken(EMAIL);
        }
    }
}