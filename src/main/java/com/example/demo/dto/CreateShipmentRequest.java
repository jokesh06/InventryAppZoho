package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateShipmentRequest {

    @NotNull(message = "Sales order ID is required")
    private Long salesOrderId;

    @NotBlank(message = "Carrier name is required (e.g. FedEx, UPS, DHL)")
    private String carrier;

    @NotBlank(message = "Tracking number is required")
    private String trackingNumber;
}