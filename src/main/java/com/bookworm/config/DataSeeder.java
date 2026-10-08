package com.bookworm.config;

import com.bookworm.entity.*;
import com.bookworm.enums.BookAvailability;
import com.bookworm.enums.BookFormat;
import com.bookworm.enums.UserRole;
import com.bookworm.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Development seed data loader.
 *
 * <p>Runs once on application startup via {@link ApplicationRunner}.
 * Every insert is guarded by an existence check, so the process is
 * <strong>fully idempotent</strong> – restarting the application never
 * creates duplicate records.
 *
 * <p>Disabled in the {@code test} Spring profile so unit / integration
 * tests are not affected.
 *
 * <p><strong>Development test user credentials</strong>
 * <pre>
 *   Email    : dev@bookworm.com
 *   Password : DevPass123!
 * </pre>
 * Change or remove this user before any non-development deployment.
 */
@Component
@Profile("!test")
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    // ── Dev user ─────────────────────────────────────────────────────────────
    static final String DEV_USER_EMAIL     = "dev@bookworm.com";
    static final String DEV_USER_PASSWORD  = "DevPass123!";
    static final String DEV_USER_FIRSTNAME = "Dev";
    static final String DEV_USER_LASTNAME  = "User";

    private final UserRepository       userRepository;
    private final GiftPointsRepository giftPointsRepository;
    private final CategoryRepository   categoryRepository;
    private final AuthorRepository     authorRepository;
    private final BookRepository       bookRepository;
    private final PasswordEncoder      passwordEncoder;

    public DataSeeder(UserRepository userRepository,
                      GiftPointsRepository giftPointsRepository,
                      CategoryRepository categoryRepository,
                      AuthorRepository authorRepository,
                      BookRepository bookRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository       = userRepository;
        this.giftPointsRepository = giftPointsRepository;
        this.categoryRepository   = categoryRepository;
        this.authorRepository     = authorRepository;
        this.bookRepository       = bookRepository;
        this.passwordEncoder      = passwordEncoder;
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("DataSeeder: starting seed check …");
        seedCategories();
        seedAuthors();
        seedBooks();
        seedDevUser();
        log.info("DataSeeder: seed check complete.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CATEGORIES
    // ─────────────────────────────────────────────────────────────────────────

    private void seedCategories() {
        // Top-level
        Category fiction    = findOrCreateCategory("Fiction",     null);
        Category nonFiction = findOrCreateCategory("Non-Fiction", null);
        Category technology = findOrCreateCategory("Technology",  null);
        Category children   = findOrCreateCategory("Children",    null);
        Category selfHelp   = findOrCreateCategory("Self-Help",   null);

        // Fiction sub-categories
        findOrCreateCategory("Science Fiction", fiction);
        findOrCreateCategory("Fantasy",         fiction);
        findOrCreateCategory("Mystery",         fiction);
        findOrCreateCategory("Romance",         fiction);
        findOrCreateCategory("Thriller",        fiction);

        // Non-Fiction sub-categories
        findOrCreateCategory("Biography",       nonFiction);
        findOrCreateCategory("History",         nonFiction);
        findOrCreateCategory("Travel",          nonFiction);

        // Technology sub-categories
        findOrCreateCategory("Programming",              technology);
        findOrCreateCategory("Data Science",             technology);
        findOrCreateCategory("Artificial Intelligence",  technology);

        // Children sub-categories
        findOrCreateCategory("Picture Books", children);
        findOrCreateCategory("Young Adult",   children);

        // Self-Help sub-categories
        findOrCreateCategory("Productivity", selfHelp);
        findOrCreateCategory("Finance",      selfHelp);

        log.info("DataSeeder: categories seeded.");
    }

    private Category findOrCreateCategory(String name, Category parent) {
        return categoryRepository.findByName(name).orElseGet(() -> {
            Category c = Category.builder().name(name).parent(parent).build();
            Category saved = categoryRepository.save(c);
            log.debug("DataSeeder: created category '{}'", name);
            return saved;
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  AUTHORS
    // ─────────────────────────────────────────────────────────────────────────

    private void seedAuthors() {
        findOrCreateAuthor("J.K. Rowling",
                "British author best known for the Harry Potter fantasy series, " +
                "one of the best-selling book series in history.");
        findOrCreateAuthor("George Orwell",
                "English novelist and essayist, best known for Animal Farm and Nineteen Eighty-Four.");
        findOrCreateAuthor("Frank Herbert",
                "American science fiction author best known for the Dune saga.");
        findOrCreateAuthor("Yuval Noah Harari",
                "Israeli historian and professor, author of the popular science bestsellers Sapiens and Homo Deus.");
        findOrCreateAuthor("Robert C. Martin",
                "American software engineer known as 'Uncle Bob', author of Clean Code and The Clean Coder.");
        findOrCreateAuthor("Agatha Christie",
                "English crime writer known for Hercule Poirot and Miss Marple mysteries.");
        findOrCreateAuthor("Toni Morrison",
                "American novelist and Nobel laureate, author of Beloved and Song of Solomon.");
        findOrCreateAuthor("Walter Isaacson",
                "American author and journalist, known for biographies of Steve Jobs, Einstein, and Elon Musk.");
        findOrCreateAuthor("James Clear",
                "American author and entrepreneur best known for Atomic Habits.");
        findOrCreateAuthor("Andy Weir",
                "American novelist best known for The Martian and Project Hail Mary.");

        log.info("DataSeeder: authors seeded.");
    }

    private Author findOrCreateAuthor(String name, String bio) {
        return authorRepository.findByName(name).orElseGet(() -> {
            Author a = Author.builder().name(name).bio(bio).build();
            Author saved = authorRepository.save(a);
            log.debug("DataSeeder: created author '{}'", name);
            return saved;
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BOOKS  (12 books)
    // ─────────────────────────────────────────────────────────────────────────

    private void seedBooks() {
        // Reload managed references so JPA can track them correctly
        Category sciFi      = catByName("Science Fiction");
        Category fantasy    = catByName("Fantasy");
        Category nonFiction = catByName("Non-Fiction");
        Category technology = catByName("Technology");
        Category programming = catByName("Programming");
        Category ai         = catByName("Artificial Intelligence");
        Category mystery    = catByName("Mystery");
        Category biography  = catByName("Biography");
        Category selfHelp   = catByName("Self-Help");
        Category productivity = catByName("Productivity");

        Author rowling   = authorByName("J.K. Rowling");
        Author orwell    = authorByName("George Orwell");
        Author herbert   = authorByName("Frank Herbert");
        Author harari    = authorByName("Yuval Noah Harari");
        Author unclebob  = authorByName("Robert C. Martin");
        Author christie  = authorByName("Agatha Christie");
        Author morrison  = authorByName("Toni Morrison");
        Author isaacson  = authorByName("Walter Isaacson");
        Author jamesClear = authorByName("James Clear");
        Author andyWeir  = authorByName("Andy Weir");

        // 1 ── Harry Potter and the Philosopher's Stone
        createBookIfAbsent(
            "9780439708180",
            "Harry Potter and the Philosopher's Stone",
            "The first novel in the Harry Potter series. Harry Potter discovers his magical heritage " +
            "on his eleventh birthday and enters Hogwarts School of Witchcraft and Wizardry.",
            rowling, "Bloomsbury Publishing", BookFormat.PAPERBACK, "English",
            LocalDate.of(1997, 6, 26), new BigDecimal("12.99"), 120,
            "Ships in 1-2 business days", List.of(rowling), List.of(fantasy)
        );

        // 2 ── Harry Potter and the Chamber of Secrets
        createBookIfAbsent(
            "9780439064873",
            "Harry Potter and the Chamber of Secrets",
            "Harry returns to Hogwarts for his second year and faces events linked to the " +
            "mysterious Chamber of Secrets.",
            rowling, "Bloomsbury Publishing", BookFormat.PAPERBACK, "English",
            LocalDate.of(1998, 7, 2), new BigDecimal("12.99"), 100,
            "Ships in 1-2 business days", List.of(rowling), List.of(fantasy)
        );

        // 3 ── 1984
        createBookIfAbsent(
            "9780451524935",
            "Nineteen Eighty-Four (1984)",
            "A dystopian novel set in Airstrip One, a province of the superstate Oceania in a world " +
            "of perpetual war, omnipresent government surveillance, and propaganda.",
            orwell, "Secker & Warburg", BookFormat.PAPERBACK, "English",
            LocalDate.of(1949, 6, 8), new BigDecimal("9.99"), 85,
            "Ships in 1-2 business days", List.of(orwell), List.of(sciFi)
        );

        // 4 ── Dune
        createBookIfAbsent(
            "9780441013593",
            "Dune",
            "Set in the distant future, Dune tells the story of young Paul Atreides whose family " +
            "accepts stewardship of the desert planet Arrakis, the only source of the most precious " +
            "substance in the universe.",
            herbert, "Chilton Books", BookFormat.PAPERBACK, "English",
            LocalDate.of(1965, 8, 1), new BigDecimal("14.99"), 75,
            "Ships in 1-2 business days", List.of(herbert), List.of(sciFi)
        );

        // 5 ── Sapiens
        createBookIfAbsent(
            "9780062316097",
            "Sapiens: A Brief History of Humankind",
            "A brief history of humankind from the Stone Age to the twenty-first century, " +
            "exploring how biology and history have defined us.",
            harari, "Harper", BookFormat.HARDCOVER, "English",
            LocalDate.of(2011, 1, 1), new BigDecimal("18.99"), 60,
            "Ships in 2-3 business days", List.of(harari), List.of(nonFiction)
        );

        // 6 ── Clean Code
        createBookIfAbsent(
            "9780132350884",
            "Clean Code: A Handbook of Agile Software Craftsmanship",
            "Even bad code can function. But if code isn't clean, it can bring a development " +
            "organisation to its knees. This book teaches you to write clean, maintainable code.",
            unclebob, "Prentice Hall", BookFormat.PAPERBACK, "English",
            LocalDate.of(2008, 8, 1), new BigDecimal("39.99"), 50,
            "Ships in 1-2 business days", List.of(unclebob), List.of(technology, programming)
        );

        // 7 ── And Then There Were None
        createBookIfAbsent(
            "9780007527526",
            "And Then There Were None",
            "Ten people are lured to an isolated island and one by one they are accused of murder " +
            "and one by one they die. Considered the world's best-selling mystery novel.",
            christie, "Collins Crime Club", BookFormat.PAPERBACK, "English",
            LocalDate.of(1939, 11, 6), new BigDecimal("8.99"), 90,
            "Ships in 1-2 business days", List.of(christie), List.of(mystery)
        );

        // 8 ── Beloved
        createBookIfAbsent(
            "9781400033416",
            "Beloved",
            "Set after the American Civil War, this Pulitzer Prize-winning novel explores the " +
            "trauma of slavery through the story of Sethe and her haunted home.",
            morrison, "Alfred A. Knopf", BookFormat.PAPERBACK, "English",
            LocalDate.of(1987, 9, 2), new BigDecimal("11.99"), 65,
            "Ships in 1-2 business days", List.of(morrison), List.of(nonFiction)
        );

        // 9 ── Elon Musk
        createBookIfAbsent(
            "9781982181284",
            "Elon Musk",
            "An in-depth biography of Elon Musk exploring his childhood, turbulent personal life, " +
            "and his quests to electrify cars, shoot rockets to Mars, and acquire Twitter.",
            isaacson, "Simon & Schuster", BookFormat.HARDCOVER, "English",
            LocalDate.of(2023, 9, 12), new BigDecimal("32.99"), 40,
            "Ships in 2-3 business days", List.of(isaacson), List.of(nonFiction, biography)
        );

        // 10 ── Homo Deus
        createBookIfAbsent(
            "9781442370142",
            "Homo Deus: A Brief History of Tomorrow",
            "Harari examines what might happen when old myths couple with new godlike technologies " +
            "such as artificial intelligence and genetic engineering.",
            harari, "Harvill Secker", BookFormat.HARDCOVER, "English",
            LocalDate.of(2015, 9, 4), new BigDecimal("19.99"), 55,
            "Ships in 2-3 business days", List.of(harari), List.of(nonFiction, ai)
        );

        // 11 ── Steve Jobs
        createBookIfAbsent(
            "9780743269513",
            "Steve Jobs",
            "Based on more than forty interviews with Steve Jobs over two years, as well as interviews " +
            "with over one hundred family members, friends, adversaries, competitors, and colleagues.",
            isaacson, "Simon & Schuster", BookFormat.PAPERBACK, "English",
            LocalDate.of(2011, 10, 24), new BigDecimal("16.99"), 70,
            "Ships in 1-2 business days", List.of(isaacson), List.of(nonFiction, biography)
        );

        // 12 ── Atomic Habits
        createBookIfAbsent(
            "9780735211292",
            "Atomic Habits",
            "A proven framework for improving every day. James Clear reveals practical strategies " +
            "on how to form good habits, break bad ones, and master the tiny behaviours that lead " +
            "to remarkable results.",
            jamesClear, "Avery", BookFormat.PAPERBACK, "English",
            LocalDate.of(2018, 10, 16), new BigDecimal("16.99"), 95,
            "Ships in 1-2 business days", List.of(jamesClear), List.of(selfHelp, productivity)
        );

        // 13 ── The Martian  (bonus – demonstrates multiple format options)
        createBookIfAbsent(
            "9780804139021",
            "The Martian",
            "An Apollo 13-like technical survival story set on Mars. Astronaut Mark Watney is stranded " +
            "on Mars after his crew is forced to evacuate and must use ingenuity to survive.",
            andyWeir, "Crown Publishers", BookFormat.EBOOK, "English",
            LocalDate.of(2014, 2, 11), new BigDecimal("10.99"), 200,
            "Instant digital delivery", List.of(andyWeir), List.of(sciFi)
        );

        log.info("DataSeeder: books seeded.");
    }

    /**
     * Creates a book only if no book with the given ISBN already exists.
     * Idempotent: safe to call on every startup.
     *
     * @param authors    all authors to associate with the book (already-persisted entities)
     * @param categories all categories to associate with the book
     */
    @SuppressWarnings("SameParameterValue")
    private void createBookIfAbsent(String isbn, String title, String description,
                                    @SuppressWarnings("unused") Author primaryAuthorUnused,
                                    String publisher,
                                    BookFormat format, String language,
                                    LocalDate publishedDate, BigDecimal price,
                                    int stock, String deliveryInfo,
                                    List<Author> authors, List<Category> categories) {
        if (bookRepository.existsByIsbn(isbn)) {
            return;
        }
        Book book = Book.builder()
                .isbn(isbn)
                .title(title)
                .description(description)
                .publisher(publisher)
                .format(format)
                .language(language)
                .publishedDate(publishedDate)
                .price(price)
                .stock(stock)
                .availability(stock > 0 ? BookAvailability.IN_STOCK : BookAvailability.OUT_OF_STOCK)
                .deliveryInfo(deliveryInfo)
                .build();

        book.getAuthors().addAll(authors);
        book.getCategories().addAll(categories);

        bookRepository.save(book);
        log.debug("DataSeeder: created book '{}' (isbn={})", title, isbn);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  DEV USER
    // ─────────────────────────────────────────────────────────────────────────

    private void seedDevUser() {
        if (userRepository.existsByEmail(DEV_USER_EMAIL)) {
            log.debug("DataSeeder: dev user already exists, skipping.");
            return;
        }
        User dev = User.builder()
                .email(DEV_USER_EMAIL)
                .passwordHash(passwordEncoder.encode(DEV_USER_PASSWORD))
                .firstName(DEV_USER_FIRSTNAME)
                .lastName(DEV_USER_LASTNAME)
                .role(UserRole.CUSTOMER)
                .build();
        dev = userRepository.save(dev);

        giftPointsRepository.save(GiftPoints.builder()
                .user(dev)
                .balance(BigDecimal.ZERO)
                .build());

        log.info("DataSeeder: dev user created — email={}", DEV_USER_EMAIL);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private Category catByName(String name) {
        return categoryRepository.findByName(name)
                .orElseThrow(() -> new IllegalStateException(
                        "Seed error: category not found after insert: " + name));
    }

    private Author authorByName(String name) {
        return authorRepository.findByName(name)
                .orElseThrow(() -> new IllegalStateException(
                        "Seed error: author not found after insert: " + name));
    }
}
