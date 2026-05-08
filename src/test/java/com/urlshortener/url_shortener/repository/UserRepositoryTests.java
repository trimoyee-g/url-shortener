package com.urlshortener.url_shortener.repository;

import com.urlshortener.url_shortener.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Transactional
@ActiveProfiles("test")
public class UserRepositoryTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    // Constants

    private static final String EMAIL = "test@example.com";
    private static final String PASSWORD = "password123";


    // Helper

    private User buildUser(String email, String password) {
        return User.builder()
                .email(email)
                .password(password)
                .build();
    }


    // findByEmail

    @Test
    @DisplayName("findByEmail returns user when email exists")
    public void UserRepository_findByEmail_returnsIfUserExistsByEmail() {

        // Arrange
        userRepository.saveAndFlush(buildUser(EMAIL, PASSWORD));
        entityManager.clear();

        // Act
        Optional<User> foundUser = userRepository.findByEmail(EMAIL);

        // Assert
        assertThat(foundUser)
                .isPresent()
                .hasValueSatisfying(u -> {
                    assertThat(u.getEmail()).isEqualTo(EMAIL);
                    assertThat(u.isActive()).isTrue();
                });
    }

    @Test
    @DisplayName("findByEmail returns empty when user not found")
    public void UserRepository_findByEmail_returnsEmptyIfUserNotFoundByEmail() {

        // Act
        Optional<User> foundUser =
                userRepository.findByEmail("notfound@example.com");

        // Assert
        assertThat(foundUser).isEmpty();
    }


    // existsByEmail

    @Test
    @DisplayName("existsByEmail returns true when email exists")
    public void UserRepository_existsByEmail_returnsTrueIfEmailExists() {

        // Arrange
        userRepository.saveAndFlush(buildUser(EMAIL, PASSWORD));

        // Act
        boolean exists = userRepository.existsByEmail(EMAIL);

        // Assert
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByEmail returns false when email does not exist")
    public void UserRepository_existsByEmail_returnsFalseIfEmailDoesNotExist() {

        // Act
        boolean exists =
                userRepository.existsByEmail("notfound@example.com");

        // Assert
        assertThat(exists).isFalse();
    }


    // Email normalization

    @Test
    @DisplayName("save normalizes email to lowercase")
    public void UserRepository_save_normalizesEmailToLowerCase() {

        // Arrange
        userRepository.saveAndFlush(
                buildUser("TEST@Example.COM", PASSWORD)
        );

        entityManager.clear();

        // Act
        Optional<User> foundUser = userRepository.findByEmail(EMAIL);

        // Assert
        assertThat(foundUser)
                .isPresent()
                .hasValueSatisfying(u ->
                        assertThat(u.getEmail()).isEqualTo(EMAIL)
                );
    }

    @Test
    @DisplayName("save trims and normalizes email")
    public void UserRepository_save_trimsAndNormalizesEmail() {

        // Arrange
        userRepository.saveAndFlush(
                buildUser("  TEST@Example.COM  ", PASSWORD)
        );

        entityManager.clear();

        // Act
        Optional<User> foundUser = userRepository.findByEmail(EMAIL);

        // Assert
        assertThat(foundUser)
                .isPresent()
                .hasValueSatisfying(u ->
                        assertThat(u.getEmail()).isEqualTo(EMAIL)
                );
    }


    // Duplicate email constraint

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("save throws exception when email already exists")
    public void UserRepository_save_throwsException_whenEmailAlreadyExists() {

        // Arrange
        String email = "duplicate_" + System.nanoTime() + "@example.com";

        userRepository.saveAndFlush(buildUser(email, PASSWORD));

        User duplicateUser = buildUser(email, "password456");

        // Act + Assert
        assertThatThrownBy(() ->
                userRepository.saveAndFlush(duplicateUser)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }


    // Bean validation tests

    @Test
    @DisplayName("save throws exception when email is null")
    public void UserRepository_save_throwsException_whenEmailIsNull() {

        // Arrange
        User user = buildUser(null, PASSWORD);

        // Act + Assert
        assertThatThrownBy(() ->
                userRepository.saveAndFlush(user)
        ).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @DisplayName("save throws exception when password is null")
    public void UserRepository_save_throwsException_whenPasswordIsNull() {

        // Arrange
        User user = buildUser(EMAIL, null);

        // Act + Assert
        assertThatThrownBy(() ->
                userRepository.saveAndFlush(user)
        ).isInstanceOf(ConstraintViolationException.class);
    }
}