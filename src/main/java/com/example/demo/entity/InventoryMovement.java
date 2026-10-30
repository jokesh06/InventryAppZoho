
package com.example.demo.entity;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryMovement {

    // Catalyst ROWID is used as the record ID
    private Long id;

    private MovementType movementType;

    private Product product;

    private Warehouse warehouse;

    private Integer onHandChange;

    private Integer reservedChange;

    private String referenceDocument;

    private String reason;

    private User user;

    private LocalDateTime timestamp;

    public enum MovementType {
        PURCHASE_RECEIPT,
        SALE_CONFIRMATION,
        SALE_SHIPMENT,
        SALE_CANCELLATION,
        TRANSFER_OUT,
        TRANSFER_IN,
        MANUAL_ADJUSTMENT
    }
}
