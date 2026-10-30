package com.example.demo.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryBalanceResponse {
    private Long id;
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private Long productId;
    private String productSku;
    private String productName;
    private Integer onHandQty;
    private Integer reservedQty;
    private Integer availableQty;
    private LocalDateTime updatedAt;
}