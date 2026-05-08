package com.urlshortener.url_shortener.repository;

import com.urlshortener.url_shortener.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    @Test
    public void UserRepository_findByEmail_returnsIfUserExistsByEmail(){

        // Arrange
        User user = User.builder()
                .email("test@example.com")
                .password("password123")
                .build();

        userRepository.saveAndFlush(user);

        // Act
        Optional<User> foundUser = userRepository.findByEmail("test@example.com");

        // Assert
        assertThat(foundUser)
                .isPresent()
                        .hasValueSatisfying(u -> {
                                    assertThat(u.getEmail()).isEqualTo("test@example.com");
                                    assertThat(u.isActive()).isTrue();
                                });

    }

    @Test
    public void UserRepository_findByEmail_returnsEmptyIfUserNotFoundByEmail(){

        // Act
        Optional<User> foundUser = userRepository.findByEmail("notfound@example.com");

        // Assert
        assertThat(foundUser).isEmpty();
    }

    @Test
    public void UserRepository_existsByEmail_returnsTrueIfEmailExists(){

        // Arrange
        User user = User.builder()
                .email("test@example.com")
                .password("password123")
                .build();

        userRepository.saveAndFlush(user);

        // Act
        boolean exists = userRepository.existsByEmail("test@example.com");

        // Assert
        assertThat(exists).isTrue();
    }

    @Test
    public void UserRepository_existsByEmail_returnsFalseIfEmailDoesNotExist(){

        // Act
        boolean exists = userRepository.existsByEmail("notfound@example.com");

        // Assert
        assertThat(exists).isFalse();
    }

    @Test
    public void UserRepository_save_normalizesEmailToLowerCase(){

        // Arrange
        User user = User.builder()
                .email("TEST@Example.COM")
                .password("password123")
                .build();

        // Act
        userRepository.saveAndFlush(user);
        entityManager.clear();
        Optional<User> foundUser = userRepository.findByEmail("test@example.com");

        // Assert
        assertThat(foundUser)
                .isPresent()
                .hasValueSatisfying(u ->
                        assertThat(u.getEmail()).isEqualTo("test@example.com"));
    }

    @Test
    public void UserRepository_save_trimsAndNormalizesEmail(){

        // Arrange
        User user = User.builder()
                .email("  TEST@Example.COM  ")
                .password("password123")
                .build();

        // Act
        userRepository.saveAndFlush(user);
        entityManager.clear();

        Optional<User> foundUser = userRepository.findByEmail("test@example.com");
        // Assert
        assertThat(foundUser)
                .isPresent()
                .hasValueSatisfying(u ->
                        assertThat(u.getEmail()).isEqualTo("test@example.com")
                );
    }


    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void UserRepository_save_throwsException_whenEmailAlreadyExists(){

        // Arrange
        String email = "duplicate_" + System.nanoTime() + "@example.com";
        User user1 = User.builder()
                .email(email)
                .password("password123")
                .build();

        User user2 = User.builder()
                .email(email) // same email
                .password("password456")
                .build();

        userRepository.saveAndFlush(user1);

        // Act + Assert
        assertThatThrownBy(() -> {
            userRepository.saveAndFlush(user2);
        }).isInstanceOf(DataIntegrityViolationException.class);

    }
}
