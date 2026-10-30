package com.example.demo.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderItemResponse {
    private Long id;
    private Long productId;
    private String productSku;
    private String productName;
    private Integer orderedQty;
    private Integer receivedQty;
    private Integer remainingQty;
    private BigDecimal unitCost;
    private BigDecimal lineTotal;
}