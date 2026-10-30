package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManualAdjustmentRequest {

    @NotNull(message = "Warehouse ID is required")
    private Long warehouseId;

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Quantity change is required (positive for increase, negative for decrease)")
    private Integer quantityChange;

    @NotBlank(message = "Reason for manual adjustment is mandatory for audit logging")
    private String reason;
}