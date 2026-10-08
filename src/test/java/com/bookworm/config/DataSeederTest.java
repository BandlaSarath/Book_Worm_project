package com.bookworm.config;

import com.bookworm.entity.*;
import com.bookworm.enums.UserRole;
import com.bookworm.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DataSeeder unit tests")
class DataSeederTest {

    @Mock private UserRepository         userRepository;
    @Mock private GiftPointsRepository   giftPointsRepository;
    @Mock private CategoryRepository     categoryRepository;
    @Mock private AuthorRepository       authorRepository;
    @Mock private BookRepository         bookRepository;
    @Mock private PasswordEncoder        passwordEncoder;
    @Mock private ApplicationArguments   appArgs;

    @InjectMocks
    private DataSeeder dataSeeder;

    // ── shared stubs ────────────────────────────────────────────────────────

    @BeforeEach
    void setUpCommonStubs() {
        // Category findByName: return empty so seeder creates; subsequent calls
        // (catByName helper) return a saved entity.
        when(categoryRepository.findByName(anyString())).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            // Simulate auto-generated id
            if (c.getId() == null) {
                Category withId = Category.builder()
                        .name(c.getName())
                        .parent(c.getParent())
                        .build();
                // Use reflection-free trick: re-stub findByName for this name
                when(categoryRepository.findByName(c.getName())).thenReturn(Optional.of(withId));
                return withId;
            }
            return c;
        });

        // Author findByName
        when(authorRepository.findByName(anyString())).thenReturn(Optional.empty());
        when(authorRepository.save(any(Author.class))).thenAnswer(inv -> {
            Author a = inv.getArgument(0);
            when(authorRepository.findByName(a.getName())).thenReturn(Optional.of(a));
            return a;
        });

        // Books: assume none exist yet (all ISBNs absent)
        when(bookRepository.existsByIsbn(anyString())).thenReturn(false);
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));

        // Dev user: does not exist yet
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(giftPointsRepository.save(any(GiftPoints.class))).thenAnswer(inv -> inv.getArgument(0));

        // Password encoder
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
    }

    // ── Dev user tests ───────────────────────────────────────────────────────

    @Test
    @DisplayName("run() – dev user is created with BCrypt-hashed password on first run")
    void run_devUserCreated_withHashedPassword() throws Exception {
        dataSeeder.run(appArgs);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(captor.capture());

        User saved = captor.getAllValues().stream()
                .filter(u -> DataSeeder.DEV_USER_EMAIL.equals(u.getEmail()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Dev user not saved"));

        assertThat(saved.getEmail()).isEqualTo(DataSeeder.DEV_USER_EMAIL);
        assertThat(saved.getPasswordHash()).isEqualTo("$2a$10$hashed");
        assertThat(saved.getPasswordHash()).doesNotContain(DataSeeder.DEV_USER_PASSWORD);
        assertThat(saved.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(saved.getFirstName()).isEqualTo(DataSeeder.DEV_USER_FIRSTNAME);
        assertThat(saved.getLastName()).isEqualTo(DataSeeder.DEV_USER_LASTNAME);
    }

    @Test
    @DisplayName("run() – dev user plaintext password is never persisted")
    void run_plaintextPasswordNeverPersisted() throws Exception {
        dataSeeder.run(appArgs);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(captor.capture());

        captor.getAllValues().forEach(u ->
            assertThat(u.getPasswordHash())
                .as("Plaintext password must not be stored")
                .doesNotContain(DataSeeder.DEV_USER_PASSWORD)
        );
    }

    @Test
    @DisplayName("run() – gift points wallet is created for dev user")
    void run_giftPointsWalletCreated() throws Exception {
        dataSeeder.run(appArgs);

        ArgumentCaptor<GiftPoints> captor = ArgumentCaptor.forClass(GiftPoints.class);
        verify(giftPointsRepository, atLeastOnce()).save(captor.capture());

        GiftPoints gp = captor.getAllValues().stream().findFirst()
                .orElseThrow(() -> new AssertionError("GiftPoints not saved"));
        assertThat(gp.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ── Idempotency tests ────────────────────────────────────────────────────

    @Test
    @DisplayName("run() – dev user is NOT created when email already exists (idempotent)")
    void run_devUser_idempotent() throws Exception {
        when(userRepository.existsByEmail(DataSeeder.DEV_USER_EMAIL)).thenReturn(true);

        dataSeeder.run(appArgs);

        // User.save() must NOT be called for the dev-user email
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeast(0)).save(captor.capture());
        boolean devUserSaved = captor.getAllValues().stream()
                .anyMatch(u -> DataSeeder.DEV_USER_EMAIL.equals(u.getEmail()));
        assertThat(devUserSaved).isFalse();
    }

    @Test
    @DisplayName("run() – books are NOT re-created when ISBNs already exist (idempotent)")
    void run_books_idempotent() throws Exception {
        when(bookRepository.existsByIsbn(anyString())).thenReturn(true);

        dataSeeder.run(appArgs);

        verify(bookRepository, never()).save(any(Book.class));
    }

    @Test
    @DisplayName("run() – categories are NOT re-created when names already exist (idempotent)")
    void run_categories_idempotent() throws Exception {
        // Return a pre-existing category for every findByName call
        when(categoryRepository.findByName(anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            return Optional.of(Category.builder().name(name).build());
        });

        dataSeeder.run(appArgs);

        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    @DisplayName("run() – authors are NOT re-created when names already exist (idempotent)")
    void run_authors_idempotent() throws Exception {
        when(authorRepository.findByName(anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            return Optional.of(Author.builder().name(name).build());
        });

        dataSeeder.run(appArgs);

        verify(authorRepository, never()).save(any(Author.class));
    }

    // ── Book count test ──────────────────────────────────────────────────────

    @Test
    @DisplayName("run() – saves exactly 13 books on first run")
    void run_savesExpectedNumberOfBooks() throws Exception {
        dataSeeder.run(appArgs);

        ArgumentCaptor<Book> captor = ArgumentCaptor.forClass(Book.class);
        verify(bookRepository, times(13)).save(captor.capture());

        List<Book> books = captor.getAllValues();
        assertThat(books).hasSize(13);
        // Verify all books have an ISBN, title and price
        books.forEach(b -> {
            assertThat(b.getIsbn()).isNotBlank();
            assertThat(b.getTitle()).isNotBlank();
            assertThat(b.getPrice()).isGreaterThan(BigDecimal.ZERO);
        });
    }

    @Test
    @DisplayName("run() – every book has at least one author and one category")
    void run_everyBookHasAuthorAndCategory() throws Exception {
        dataSeeder.run(appArgs);

        ArgumentCaptor<Book> captor = ArgumentCaptor.forClass(Book.class);
        verify(bookRepository, atLeastOnce()).save(captor.capture());

        captor.getAllValues().forEach(b -> {
            assertThat(b.getAuthors())
                    .as("Book '%s' must have at least one author", b.getTitle())
                    .isNotEmpty();
            assertThat(b.getCategories())
                    .as("Book '%s' must have at least one category", b.getTitle())
                    .isNotEmpty();
        });
    }

    // ── Category count test ──────────────────────────────────────────────────

    @Test
    @DisplayName("run() – saves exactly 20 categories on first run")
    void run_savesExpectedNumberOfCategories() throws Exception {
        dataSeeder.run(appArgs);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository, times(20)).save(captor.capture());
    }

    // ── Author count test ────────────────────────────────────────────────────

    @Test
    @DisplayName("run() – saves exactly 10 authors on first run")
    void run_savesExpectedNumberOfAuthors() throws Exception {
        dataSeeder.run(appArgs);

        ArgumentCaptor<Author> captor = ArgumentCaptor.forClass(Author.class);
        verify(authorRepository, times(10)).save(captor.capture());
    }
}
