
package com.example.demo.entity;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesOrder {

    // Catalyst system ROWID
    private Long id;

    private String orderNumber;

    private String customerName;

    private String customerEmail;

    private Warehouse warehouse;

    @Builder.Default
    private OrderStatus status = OrderStatus.DRAFT;

    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    private User createdBy;

    @Builder.Default
    private List<SalesOrderItem> items = new ArrayList<>();

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String carrier;

    private String trackingNumber;

    public enum OrderStatus {
        DRAFT,
        CONFIRMED,
        SHIPPED,
        DELIVERED,
        CANCELLED
    }
}
