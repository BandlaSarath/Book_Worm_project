package com.bookworm.mapper;

import com.bookworm.dto.response.*;
import com.bookworm.entity.*;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class BookWormMapper {

    // ── User ────────────────────────────────────────────────────────────────
    public UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build();
    }

    // ── Author ───────────────────────────────────────────────────────────────
    public AuthorResponse toAuthorResponse(Author author) {
        return AuthorResponse.builder()
                .id(author.getId())
                .name(author.getName())
                .bio(author.getBio())
                .build();
    }

    // ── Category ─────────────────────────────────────────────────────────────
    public CategoryResponse toCategoryResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .children(category.getChildren().stream()
                        .map(this::toCategoryResponse)
                        .collect(Collectors.toList()))
                .build();
    }

    // ── Book ─────────────────────────────────────────────────────────────────
    public BookResponse toBookResponse(Book book) {
        return BookResponse.builder()
                .id(book.getId())
                .title(book.getTitle())
                .authors(book.getAuthors().stream().map(this::toAuthorResponse).collect(Collectors.toList()))
                .categories(book.getCategories().stream().map(this::toCategoryResponse).collect(Collectors.toList()))
                .price(book.getPrice())
                .format(book.getFormat())
                .language(book.getLanguage())
                .coverImageUrl(book.getCoverImageUrl())
                .isbn(book.getIsbn())
                .description(book.getDescription())
                .publisher(book.getPublisher())
                .publishedDate(book.getPublishedDate())
                .stock(book.getStock())
                .availability(book.getAvailability())
                .deliveryInfo(book.getDeliveryInfo())
                .build();
    }

    // ── Cart ─────────────────────────────────────────────────────────────────
    public CartItemResponse toCartItemResponse(CartItem item) {
        return CartItemResponse.builder()
                .id(item.getId())
                .book(toBookResponse(item.getBook()))
                .quantity(item.getQuantity())
                .priceSnapshot(item.getPriceSnapshot())
                .lineTotal(item.getLineTotal())
                .build();
    }

    public CartResponse toCartResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream()
                .map(this::toCartItemResponse)
                .collect(Collectors.toList());
        return CartResponse.builder()
                .id(cart.getId())
                .items(items)
                .subtotal(cart.getSubtotal())
                .itemCount(items.size())
                .build();
    }

    // ── Address ──────────────────────────────────────────────────────────────
    public AddressResponse toAddressResponse(Address address) {
        return AddressResponse.builder()
                .id(address.getId())
                .line1(address.getLine1())
                .line2(address.getLine2())
                .city(address.getCity())
                .state(address.getState())
                .postalCode(address.getPostalCode())
                .country(address.getCountry())
                .isDefault(address.getIsDefault())
                .build();
    }

    // ── Order ─────────────────────────────────────────────────────────────────
    public OrderItemResponse toOrderItemResponse(OrderItem item) {
        return OrderItemResponse.builder()
                .id(item.getId())
                .book(toBookResponse(item.getBook()))
                .quantity(item.getQuantity())
                .priceAtPurchase(item.getPriceAtPurchase())
                .lineTotal(item.getLineTotal())
                .build();
    }

    public OrderResponse toOrderResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .items(order.getItems().stream().map(this::toOrderItemResponse).collect(Collectors.toList()))
                .deliveryAddress(order.getDeliveryAddress() != null
                        ? toAddressResponse(order.getDeliveryAddress()) : null)
                .status(order.getStatus())
                .subtotal(order.getSubtotal())
                .giftPointsRedeemed(order.getGiftPointsRedeemed())
                .totalAmount(order.getTotalAmount())
                .paymentStatus(order.getPayment() != null
                        ? order.getPayment().getStatus() : null)
                .placedAt(order.getPlacedAt())
                .cancelledAt(order.getCancelledAt())
                .cancellationEligible(order.isCancellationEligible())
                .build();
    }

    // ── Payment ──────────────────────────────────────────────────────────────
    public PaymentResponse toPaymentResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrder().getId())
                .method(payment.getMethod())
                .status(payment.getStatus())
                .transactionRef(payment.getTransactionRef())
                .amount(payment.getAmount())
                .paidAt(payment.getPaidAt())
                .build();
    }

    // ── Wishlist ─────────────────────────────────────────────────────────────
    public WishlistItemResponse toWishlistItemResponse(WishlistItem item) {
        return WishlistItemResponse.builder()
                .id(item.getId())
                .book(toBookResponse(item.getBook()))
                .addedAt(item.getAddedAt())
                .build();
    }

    // ── Gift Points ──────────────────────────────────────────────────────────
    public GiftPointTransactionResponse toGiftPointTransactionResponse(GiftPointsTransaction tx) {
        return GiftPointTransactionResponse.builder()
                .id(tx.getId())
                .delta(tx.getDelta())
                .reason(tx.getReason())
                .createdAt(tx.getCreatedAt())
                .build();
    }

    // ── Pagination ────────────────────────────────────────────────────────────
    public <T> PagedResponse<T> toPagedResponse(Page<T> page) {
        return PagedResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }
}
