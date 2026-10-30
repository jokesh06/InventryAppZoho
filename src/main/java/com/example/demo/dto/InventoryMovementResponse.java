package com.example.demo.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryMovementResponse {
    private Long id;
    private String movementType;
    private Long productId;
    private String productSku;
    private String productName;
    private Long warehouseId;
    private String warehouseCode;
    private Integer onHandChange;
    private Integer reservedChange;
    private String referenceDocument;
    private String reason;
    private String performedByUsername;
    private LocalDateTime timestamp;
}