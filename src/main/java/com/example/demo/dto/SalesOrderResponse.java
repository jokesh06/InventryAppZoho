package com.example.demo.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesOrderResponse {
    private Long id;
    private String orderNumber;
    private String customerName;
    private String customerEmail;
    private Long warehouseId;
    private String warehouseName;
    private String status;
    private BigDecimal totalAmount;
    private List<SalesOrderItemResponse> items;
    private LocalDateTime createdAt;
    private String carrier;
    private String trackingNumber;
}