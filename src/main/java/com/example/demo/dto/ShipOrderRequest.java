package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipOrderRequest {

    @NotBlank(message = "Carrier name is required")
    private String carrier;

    @NotBlank(message = "Tracking number is required")
    private String trackingNumber;
}