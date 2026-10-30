package com.example.demo.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentResponse {
    private Long id;
    private String shipmentNumber;
    private Long salesOrderId;
    private String salesOrderNumber;
    private String customerName;
    private String carrier;
    private String trackingNumber;
    private String status;
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;
    private String createdByUsername;
}