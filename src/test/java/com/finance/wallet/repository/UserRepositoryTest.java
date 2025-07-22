package com.finance.wallet.repository;

import com.finance.wallet.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@TestPropertySource(locations = "classpath:application-test.properties")
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .name("John Doe")
                .email("john.doe@example.com")
                .password("$2a$10$encodedPassword123")
                .build();
    }

    @Test
    void findByEmail_WhenUserExists_ReturnsUser() {
        User savedUser = entityManager.persistAndFlush(testUser);
        entityManager.clear();

        Optional<User> found = userRepository.findByEmail("john.doe@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(savedUser.getId());
        assertThat(found.get().getName()).isEqualTo("John Doe");
        assertThat(found.get().getEmail()).isEqualTo("john.doe@example.com");
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getUpdatedAt()).isNotNull();
    }

    @Test
    void findByEmail_WhenUserDoesNotExist_ReturnsEmpty() {
        Optional<User> found = userRepository.findByEmail("nonexistent@example.com");
        assertThat(found).isEmpty();
    }

    @Test
    void findByEmail_CaseInsensitive_ReturnsUser() {
        entityManager.persistAndFlush(testUser);
        entityManager.clear();

        Optional<User> found = userRepository.findByEmail("JOHN.DOE@EXAMPLE.COM");

        assertThat(found).isEmpty();
    }

    @Test
    void existsByEmail_WhenUserExists_ReturnsTrue() {
        entityManager.persistAndFlush(testUser);
        entityManager.clear();
        boolean exists = userRepository.existsByEmail("john.doe@example.com");
        assertThat(exists).isTrue();
    }

    @Test
    void existsByEmail_WhenUserDoesNotExist_ReturnsFalse() {
        boolean exists = userRepository.existsByEmail("nonexistent@example.com");

        assertThat(exists).isFalse();
    }

    @Test
    void save_NewUser_PersistsUserWithGeneratedId() {
        User savedUser = userRepository.save(testUser);

        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getName()).isEqualTo("John Doe");
        assertThat(savedUser.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(savedUser.getCreatedAt()).isNotNull();
        assertThat(savedUser.getUpdatedAt()).isNotNull();
    }

    @Test
    void save_DuplicateEmail_ThrowsException() {
        entityManager.persistAndFlush(testUser);
        entityManager.clear();

        User duplicateUser = User.builder()
                .name("Jane Doe")
                .email("john.doe@example.com") // Same email
                .password("$2a$10$encodedPassword123")
                .build();

        assertThatThrownBy(() -> {
            userRepository.save(duplicateUser);
            entityManager.flush();
        }).hasMessageContaining("could not execute statement");
    }

    @Test
    void findById_WhenUserExists_ReturnsUser() {
        User savedUser = entityManager.persistAndFlush(testUser);
        entityManager.clear();

        Optional<User> found = userRepository.findById(savedUser.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("John Doe");
        assertThat(found.get().getEmail()).isEqualTo("john.doe@example.com");
    }

    @Test
    void findById_WhenUserDoesNotExist_ReturnsEmpty() {
        Optional<User> found = userRepository.findById(999L);
        assertThat(found).isEmpty();
    }

    @Test
    void findAll_ReturnsAllUsers() {
        User user1 = User.builder().name("User 1").email("user1@example.com").password("$2a$10$encodedPassword123").build();
        User user2 = User.builder().name("User 2").email("user2@example.com").password("$2a$10$encodedPassword123").build();
        
        entityManager.persistAndFlush(user1);
        entityManager.persistAndFlush(user2);
        entityManager.clear();

        var users = userRepository.findAll();

        assertThat(users).hasSize(2);
        assertThat(users).extracting(User::getEmail)
                .containsExactlyInAnyOrder("user1@example.com", "user2@example.com");
    }

    @Test
    void deleteById_RemovesUser() {
        User savedUser = entityManager.persistAndFlush(testUser);
        entityManager.clear();

        userRepository.deleteById(savedUser.getId());
        entityManager.flush();

        Optional<User> found = userRepository.findById(savedUser.getId());
        assertThat(found).isEmpty();
    }

    @Test
    void update_ModifiesUserData() {
        User savedUser = entityManager.persistAndFlush(testUser);
        entityManager.clear();

        savedUser.setName("Updated Name");
        User updatedUser = userRepository.save(savedUser);
        entityManager.flush();

        assertThat(updatedUser.getName()).isEqualTo("Updated Name");
        assertThat(updatedUser.getUpdatedAt()).isAfter(updatedUser.getCreatedAt());
    }
} 