
package com.example.demo.entity;

import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransfer {

    // Catalyst system ROWID
    private Long id;

    private String transferNumber;

    private Warehouse sourceWarehouse;

    private Warehouse destinationWarehouse;

    @Builder.Default
    private TransferStatus status = TransferStatus.DRAFT;

    private User createdBy;

    @Builder.Default
    private List<StockTransferItem> items = new ArrayList<>();

    private LocalDateTime dispatchedAt;

    private LocalDateTime receivedAt;

    private LocalDateTime createdAt;

    public enum TransferStatus {
        DRAFT,
        DISPATCHED,
        RECEIVED,
        CANCELLED
    }
}
