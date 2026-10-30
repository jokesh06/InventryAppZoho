
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
public class PurchaseOrder {

    // Catalyst system ROWID
    private Long id;

    private String poNumber;

    private Supplier supplier;

    private Warehouse destinationWarehouse;

    @Builder.Default
    private POStatus status = POStatus.DRAFT;

    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    private User createdBy;

    @Builder.Default
    private List<PurchaseOrderItem> items = new ArrayList<>();

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public enum POStatus {
        DRAFT,
        PENDING,
        SUBMITTED,
        PARTIALLY_RECEIVED,
        RECEIVED,
        CANCELLED
    }
}
