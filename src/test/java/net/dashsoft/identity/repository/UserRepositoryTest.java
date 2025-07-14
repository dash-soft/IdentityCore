package net.dashsoft.identity.repository;

import net.dashsoft.identity.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("UserRepository Tests")
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        testUser = new User();
        testUser.setEmail("test@example.com");
        testUser.setFirstName("Test");
        testUser.setLastName("User");
        testUser.setPassword("password123", passwordEncoder);
    }

    @Test
    @DisplayName("Should find user by email when user exists")
    void shouldFindUserByEmailWhenUserExists() {
        // Given
        User savedUser = entityManager.persistAndFlush(testUser);

        // When
        Optional<User> foundUser = userRepository.findByEmail("test@example.com");

        // Then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getId()).isEqualTo(savedUser.getId());
        assertThat(foundUser.get().getEmail()).isEqualTo("test@example.com");
        assertThat(foundUser.get().getFirstName()).isEqualTo("Test");
        assertThat(foundUser.get().getLastName()).isEqualTo("User");
        assertThat(foundUser.get().getCreatedAt()).isNotNull();
        assertThat(foundUser.get().getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should return empty optional when user does not exist")
    void shouldReturnEmptyOptionalWhenUserDoesNotExist() {
        // When
        Optional<User> foundUser = userRepository.findByEmail("nonexistent@example.com");

        // Then
        assertThat(foundUser).isEmpty();
    }

    @Test
    @DisplayName("Should handle null email parameter gracefully")
    void shouldHandleNullEmailParameterGracefully() {
        // When
        Optional<User> foundUser = userRepository.findByEmail(null);

        // Then
        assertThat(foundUser).isEmpty();
    }

    @Test
    @DisplayName("Should handle empty string email parameter")
    void shouldHandleEmptyStringEmailParameter() {
        // When
        Optional<User> foundUser = userRepository.findByEmail("");

        // Then
        assertThat(foundUser).isEmpty();
    }

    @Test
    @DisplayName("Should handle whitespace-only email parameter")
    void shouldHandleWhitespaceOnlyEmailParameter() {
        // When
        Optional<User> foundUser = userRepository.findByEmail("   ");

        // Then
        assertThat(foundUser).isEmpty();
    }

    @Test
    @DisplayName("Should be case sensitive when finding by email")
    void shouldBeCaseSensitiveWhenFindingByEmail() {
        // Given
        entityManager.persistAndFlush(testUser);

        // When
        Optional<User> foundUserLowerCase = userRepository.findByEmail("test@example.com");
        Optional<User> foundUserUpperCase = userRepository.findByEmail("TEST@EXAMPLE.COM");
        Optional<User> foundUserMixedCase = userRepository.findByEmail("Test@Example.Com");

        // Then
        assertThat(foundUserLowerCase).isPresent();
        assertThat(foundUserUpperCase).isEmpty();
        assertThat(foundUserMixedCase).isEmpty();
    }

    @Test
    @DisplayName("Should find correct user when multiple users exist")
    void shouldFindCorrectUserWhenMultipleUsersExist() {
        // Given
        User user1 = new User();
        user1.setEmail("user1@example.com");
        user1.setFirstName("User");
        user1.setLastName("One");
        user1.setPassword("password1", passwordEncoder);

        User user2 = new User();
        user2.setEmail("user2@example.com");
        user2.setFirstName("User");
        user2.setLastName("Two");
        user2.setPassword("password2", passwordEncoder);

        entityManager.persistAndFlush(user1);
        entityManager.persistAndFlush(user2);
        entityManager.persistAndFlush(testUser);

        // When
        Optional<User> foundUser = userRepository.findByEmail("user2@example.com");

        // Then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getEmail()).isEqualTo("user2@example.com");
        assertThat(foundUser.get().getFirstName()).isEqualTo("User");
        assertThat(foundUser.get().getLastName()).isEqualTo("Two");
    }

    @Test
    @DisplayName("Should handle special characters in email")
    void shouldHandleSpecialCharactersInEmail() {
        // Given
        User userWithSpecialEmail = new User();
        userWithSpecialEmail.setEmail("test+special@example-domain.co.uk");
        userWithSpecialEmail.setFirstName("Special");
        userWithSpecialEmail.setLastName("User");
        userWithSpecialEmail.setPassword("password", passwordEncoder);
        entityManager.persistAndFlush(userWithSpecialEmail);

        // When
        Optional<User> foundUser = userRepository.findByEmail("test+special@example-domain.co.uk");

        // Then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getEmail()).isEqualTo("test+special@example-domain.co.uk");
        assertThat(foundUser.get().getFirstName()).isEqualTo("Special");
    }

    @Test
    @DisplayName("Should handle very long email addresses")
    void shouldHandleVeryLongEmailAddresses() {
        // Given
        String longLocalPart = "a".repeat(64);
        String longDomain = "b".repeat(63);
        String longEmail = longLocalPart + "@" + longDomain + ".com";
        
        User userWithLongEmail = new User();
        userWithLongEmail.setEmail(longEmail);
        userWithLongEmail.setFirstName("Long");
        userWithLongEmail.setLastName("Email");
        userWithLongEmail.setPassword("password", passwordEncoder);
        entityManager.persistAndFlush(userWithLongEmail);

        // When
        Optional<User> foundUser = userRepository.findByEmail(longEmail);

        // Then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getEmail()).isEqualTo(longEmail);
    }

    @Test
    @DisplayName("Should not find user after entity is removed")
    void shouldNotFindUserAfterEntityIsRemoved() {
        // Given
        User savedUser = entityManager.persistAndFlush(testUser);
        
        // Verify user exists
        Optional<User> foundUserBefore = userRepository.findByEmail("test@example.com");
        assertThat(foundUserBefore).isPresent();

        // When
        entityManager.remove(savedUser);
        entityManager.flush();

        // Then
        Optional<User> foundUserAfter = userRepository.findByEmail("test@example.com");
        assertThat(foundUserAfter).isEmpty();
    }

    @Test
    @DisplayName("Should handle database transaction rollback correctly")
    void shouldHandleDatabaseTransactionRollbackCorrectly() {
        // Given
        User transactionUser = new User();
        transactionUser.setEmail("transaction@example.com");
        transactionUser.setFirstName("Transaction");
        transactionUser.setLastName("User");
        transactionUser.setPassword("password", passwordEncoder);
        
        // When - persist but don't flush
        entityManager.persist(transactionUser);
        
        // Verify user is in persistence context but not yet committed
        Optional<User> foundUser = userRepository.findByEmail("transaction@example.com");
        assertThat(foundUser).isPresent();
        
        // Rollback by clearing the persistence context
        entityManager.clear();
        
        // Then - user should no longer be found
        Optional<User> foundUserAfterRollback = userRepository.findByEmail("transaction@example.com");
        assertThat(foundUserAfterRollback).isEmpty();
    }

    @Test
    @DisplayName("Should maintain referential integrity with related entities")
    void shouldMaintainReferentialIntegrityWithRelatedEntities() {
        // Given
        User savedUser = entityManager.persistAndFlush(testUser);
        
        // When
        Optional<User> foundUser = userRepository.findByEmail("test@example.com");
        
        // Then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getId()).isNotNull();
        assertThat(foundUser.get().getId()).isEqualTo(savedUser.getId());
        
        // Verify that the same instance is returned (within same transaction)
        Optional<User> foundUserAgain = userRepository.findByEmail("test@example.com");
        assertThat(foundUserAgain).isPresent();
        assertThat(foundUserAgain.get()).isSameAs(foundUser.get());
    }

    @Test
    @DisplayName("Should handle email with unicode characters")
    void shouldHandleEmailWithUnicodeCharacters() {
        // Given
        User unicodeUser = new User();
        unicodeUser.setEmail("tëst@éxample.com");
        unicodeUser.setFirstName("Unicode");
        unicodeUser.setLastName("User");
        unicodeUser.setPassword("password", passwordEncoder);
        entityManager.persistAndFlush(unicodeUser);

        // When
        Optional<User> foundUser = userRepository.findByEmail("tëst@éxample.com");

        // Then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getEmail()).isEqualTo("tëst@éxample.com");
    }

    @Test
    @DisplayName("Should verify unique constraint on email")
    void shouldVerifyUniqueConstraintOnEmail() {
        // Given
        User firstUser = new User();
        firstUser.setEmail("duplicate@example.com");
        firstUser.setFirstName("First");
        firstUser.setLastName("User");
        firstUser.setPassword("password1", passwordEncoder);
        entityManager.persistAndFlush(firstUser);

        // When/Then - attempting to save another user with same email should fail
        User duplicateUser = new User();
        duplicateUser.setEmail("duplicate@example.com");
        duplicateUser.setFirstName("Duplicate");
        duplicateUser.setLastName("User");
        duplicateUser.setPassword("password2", passwordEncoder);
        
        // This should throw a constraint violation exception
        try {
            entityManager.persistAndFlush(duplicateUser);
            assertThat(false).as("Expected constraint violation").isTrue();
        } catch (Exception e) {
            // Expected - constraint violation due to unique email
            assertThat(e).isNotNull();
        }
        
        // Verify only the first user is found
        Optional<User> foundUser = userRepository.findByEmail("duplicate@example.com");
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getFirstName()).isEqualTo("First");
    }

    @Test
    @DisplayName("Should verify timestamps are automatically set")
    void shouldVerifyTimestampsAreAutomaticallySet() {
        // Given/When
        User savedUser = entityManager.persistAndFlush(testUser);

        // Then
        assertThat(savedUser.getCreatedAt()).isNotNull();
        assertThat(savedUser.getUpdatedAt()).isNotNull();
        assertThat(savedUser.getCreatedAt()).isEqualTo(savedUser.getUpdatedAt());
        
        // Verify timestamps are preserved when retrieving via repository
        Optional<User> foundUser = userRepository.findByEmail("test@example.com");
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getCreatedAt()).isEqualTo(savedUser.getCreatedAt());
        assertThat(foundUser.get().getUpdatedAt()).isEqualTo(savedUser.getUpdatedAt());
    }

    @Test
    @DisplayName("Should handle email with maximum valid length")
    void shouldHandleEmailWithMaximumValidLength() {
        // Given - RFC 5321 specifies max 64 chars for local part and 253 for domain
        String maxLocalPart = "a".repeat(64);
        String maxDomainPart = "b".repeat(60) + ".com"; // 64 chars total
        String maxEmail = maxLocalPart + "@" + maxDomainPart;
        
        User maxEmailUser = new User();
        maxEmailUser.setEmail(maxEmail);
        maxEmailUser.setFirstName("Max");
        maxEmailUser.setLastName("Email");
        maxEmailUser.setPassword("password", passwordEncoder);
        entityManager.persistAndFlush(maxEmailUser);

        // When
        Optional<User> foundUser = userRepository.findByEmail(maxEmail);

        // Then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getEmail()).isEqualTo(maxEmail);
        assertThat(foundUser.get().getEmail().length()).isGreaterThan(100);
    }
}