package com.bookworm.repository;

import com.bookworm.entity.Book;
import com.bookworm.enums.BookFormat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    boolean existsByIsbn(String isbn);



    // search() removed — replaced by Specification-based findAll() in CatalogueService
    // to avoid lower(bytea) type-inference failures in Hibernate 6 + PostgreSQL 18.

    @Query("""
        SELECT DISTINCT b FROM Book b
        JOIN b.categories c
        WHERE c.id = :categoryId
          AND (:language IS NULL OR b.language = :language)
          AND (:format IS NULL OR b.format = :format)
          AND (:minPrice IS NULL OR b.price >= :minPrice)
          AND (:maxPrice IS NULL OR b.price <= :maxPrice)
        """)
    Page<Book> findByCategoryId(
        @Param("categoryId") Long categoryId,
        @Param("language") String language,
        @Param("format") BookFormat format,
        @Param("minPrice") BigDecimal minPrice,
        @Param("maxPrice") BigDecimal maxPrice,
        Pageable pageable);

    @Query("SELECT DISTINCT b FROM Book b JOIN b.authors a WHERE a.id = :authorId")
    Page<Book> findByAuthorId(@Param("authorId") Long authorId, Pageable pageable);

    @Query("""
        SELECT DISTINCT b FROM Book b
        JOIN b.authors a
        WHERE a.id = :authorId
          AND (:language IS NULL OR b.language = :language)
          AND (:format IS NULL OR b.format = :format)
          AND (:minPrice IS NULL OR b.price >= :minPrice)
          AND (:maxPrice IS NULL OR b.price <= :maxPrice)
        """)
    Page<Book> findByAuthorIdFiltered(
        @Param("authorId") Long authorId,
        @Param("language") String language,
        @Param("format") BookFormat format,
        @Param("minPrice") BigDecimal minPrice,
        @Param("maxPrice") BigDecimal maxPrice,
        Pageable pageable);

    @Query("""
        SELECT DISTINCT b FROM Book b
        JOIN b.categories c
        WHERE c.id IN (SELECT c2.id FROM Book b2 JOIN b2.categories c2 WHERE b2.id = :bookId)
          AND b.id <> :bookId
        """)
    List<Book> findRelated(@Param("bookId") Long bookId, Pageable pageable);

    @Query("""
        SELECT DISTINCT b FROM Book b
        JOIN b.categories c
        WHERE c.id IN (
            SELECT DISTINCT c2.id FROM OrderItem oi
            JOIN oi.order o
            JOIN oi.book bk
            JOIN bk.categories c2
            WHERE o.user.id = :userId
        )
        AND b.id NOT IN (
            SELECT oi2.book.id FROM OrderItem oi2
            JOIN oi2.order o2
            WHERE o2.user.id = :userId
        )
        ORDER BY b.price ASC
        """)
    List<Book> findRecommendations(@Param("userId") Long userId, Pageable pageable);
}
