package com.urlshortener.url_shortener.service;

import com.urlshortener.url_shortener.entity.User;
import com.urlshortener.url_shortener.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTests {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .email("test@example.com")
                .password("encodedPassword")
                .name("Test User")
                .active(true)
                .build();
    }

    @Nested
    @DisplayName("loadUserByUsername")
    class LoadUserByUsername {

        @Test
        @DisplayName("returns UserDetails when user exists")
        void returnsUserDetails_whenUserExists() {

            // Arrange
            when(userRepository.findByEmail("test@example.com"))
                    .thenReturn(Optional.of(user));

            // Act
            UserDetails userDetails =
                    customUserDetailsService.loadUserByUsername("test@example.com");

            // Assert
            assertThat(userDetails).isNotNull();

            assertThat(userDetails.getUsername())
                    .isEqualTo("test@example.com");

            assertThat(userDetails.getPassword())
                    .isEqualTo("encodedPassword");

            assertThat(userDetails.getAuthorities())
                    .isEmpty();

            verify(userRepository).findByEmail("test@example.com");
        }

        @Test
        @DisplayName("throws UsernameNotFoundException when user does not exist")
        void throwsException_whenUserNotFound() {

            // Arrange
            String email = "missing@example.com";

            when(userRepository.findByEmail(email))
                    .thenReturn(Optional.empty());

            // Act + Assert
            assertThatThrownBy(() ->
                    customUserDetailsService.loadUserByUsername(email)
            )
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessage("User not found with email: " + email);

            verify(userRepository).findByEmail(email);
        }

        @Test
        @DisplayName("loads correct user email and password")
        void loadsCorrectCredentials() {

            // Arrange
            when(userRepository.findByEmail(user.getEmail()))
                    .thenReturn(Optional.of(user));

            // Act
            UserDetails result =
                    customUserDetailsService.loadUserByUsername(user.getEmail());

            // Assert
            assertThat(result.getUsername())
                    .isEqualTo(user.getEmail());

            assertThat(result.getPassword())
                    .isEqualTo(user.getPassword());
        }

        @Test
        @DisplayName("returns empty authorities list")
        void returnsEmptyAuthorities() {

            // Arrange
            when(userRepository.findByEmail(user.getEmail()))
                    .thenReturn(Optional.of(user));

            // Act
            UserDetails result =
                    customUserDetailsService.loadUserByUsername(user.getEmail());

            // Assert
            assertThat(result.getAuthorities())
                    .isEmpty();
        }
    }
}