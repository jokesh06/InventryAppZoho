package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateShipmentStatusRequest {

    @NotBlank(message = "Shipment status is required (IN_TRANSIT, DELIVERED, RETURNED)")
    private String status;
}