package com.bookworm.service;

import com.bookworm.dto.response.AuthorResponse;
import com.bookworm.dto.response.BookResponse;
import com.bookworm.dto.response.CategoryResponse;
import com.bookworm.dto.response.PagedResponse;
import com.bookworm.entity.Author;
import com.bookworm.entity.Book;
import com.bookworm.entity.Category;
import com.bookworm.enums.BookFormat;
import com.bookworm.exception.ResourceNotFoundException;
import com.bookworm.mapper.BookWormMapper;
import com.bookworm.repository.AuthorRepository;
import com.bookworm.repository.BookRepository;
import com.bookworm.repository.CategoryRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CatalogueService {

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final CategoryRepository categoryRepository;
    private final BookWormMapper mapper;

    public CatalogueService(BookRepository bookRepository,
                             AuthorRepository authorRepository,
                             CategoryRepository categoryRepository,
                             BookWormMapper mapper) {
        this.bookRepository = bookRepository;
        this.authorRepository = authorRepository;
        this.categoryRepository = categoryRepository;
        this.mapper = mapper;
    }

    // ── Books ──────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public PagedResponse<BookResponse> searchBooks(String q, Long categoryId, Long authorId,
                                                   String language, BookFormat format,
                                                   BigDecimal minPrice, BigDecimal maxPrice,
                                                   Pageable pageable) {
        Page<Book> page;
        if (categoryId != null) {
            page = bookRepository.findByCategoryId(categoryId, language, format, minPrice, maxPrice, pageable);
        } else if (authorId != null) {
            page = bookRepository.findByAuthorIdFiltered(authorId, language, format, minPrice, maxPrice, pageable);
        } else {
            // Use Specification-based dynamic query to avoid Hibernate 6 + PostgreSQL
            // lower(bytea) type-inference error caused by nullable JPQL parameters.
            page = bookRepository.findAll(buildSearchSpec(q, language, format, minPrice, maxPrice), pageable);
        }
        Page<BookResponse> mapped = page.map(mapper::toBookResponse);
        return mapper.toPagedResponse(mapped);
    }

    /**
     * Builds a JPA Specification for the book search endpoint.
     * Each predicate is only added when the corresponding filter value is present,
     * so PostgreSQL never receives a null inside a LOWER() call.
     */
    private Specification<Book> buildSearchSpec(String q, String language, BookFormat format,
                                                BigDecimal minPrice, BigDecimal maxPrice) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Full-text search: match title, author name, isbn, or description
            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.toLowerCase() + "%";
                Join<Object, Object> authors = root.join("authors", JoinType.LEFT);
                Predicate titleMatch  = cb.like(cb.lower(root.get("title")),       pattern);
                Predicate authorMatch = cb.like(cb.lower(authors.get("name")),     pattern);
                Predicate isbnMatch   = cb.like(cb.lower(root.get("isbn")),        pattern);
                Predicate descMatch   = cb.like(cb.lower(root.get("description")), pattern);
                predicates.add(cb.or(titleMatch, authorMatch, isbnMatch, descMatch));
                query.distinct(true);
            }

            if (language != null) {
                predicates.add(cb.equal(root.get("language"), language));
            }
            if (format != null) {
                predicates.add(cb.equal(root.get("format"), format));
            }
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Transactional(readOnly = true)
    public BookResponse getBookById(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found: " + id));
        return mapper.toBookResponse(book);
    }

    @Transactional(readOnly = true)
    public List<BookResponse> getRelatedBooks(Long bookId, int limit) {
        if (!bookRepository.existsById(bookId)) {
            throw new ResourceNotFoundException("Book not found: " + bookId);
        }
        return bookRepository.findRelated(bookId, Pageable.ofSize(limit))
                .stream().map(mapper::toBookResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BookResponse> getRecommendations(Long userId, int limit) {
        return bookRepository.findRecommendations(userId, Pageable.ofSize(limit))
                .stream().map(mapper::toBookResponse).collect(Collectors.toList());
    }

    // ── Categories ─────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return categoryRepository.findByParentIsNull()
                .stream().map(mapper::toCategoryResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {
        Category cat = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
        return mapper.toCategoryResponse(cat);
    }

    @Transactional(readOnly = true)
    public PagedResponse<BookResponse> getBooksByCategory(Long categoryId, String language,
                                                          BookFormat format, BigDecimal minPrice,
                                                          BigDecimal maxPrice, Pageable pageable) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category not found: " + categoryId);
        }
        Page<Book> page = bookRepository.findByCategoryId(categoryId, language, format, minPrice, maxPrice, pageable);
        return mapper.toPagedResponse(page.map(mapper::toBookResponse));
    }

    // ── Authors ─────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public PagedResponse<AuthorResponse> listAuthors(String q, Pageable pageable) {
        Page<Author> page = (q != null && !q.isBlank())
                ? authorRepository.findByNameContainingIgnoreCase(q, pageable)
                : authorRepository.findAll(pageable);
        return mapper.toPagedResponse(page.map(mapper::toAuthorResponse));
    }

    @Transactional(readOnly = true)
    public AuthorResponse getAuthorById(Long id) {
        Author author = authorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Author not found: " + id));
        return mapper.toAuthorResponse(author);
    }

    @Transactional(readOnly = true)
    public PagedResponse<BookResponse> getBooksByAuthor(Long authorId, Pageable pageable) {
        if (!authorRepository.existsById(authorId)) {
            throw new ResourceNotFoundException("Author not found: " + authorId);
        }
        Page<Book> page = bookRepository.findByAuthorId(authorId, pageable);
        return mapper.toPagedResponse(page.map(mapper::toBookResponse));
    }
}
