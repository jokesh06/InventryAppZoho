package com.example.demo.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransferItemResponse {
    private Long id;
    private Long productId;
    private String productSku;
    private String productName;
    private Integer quantity;
}