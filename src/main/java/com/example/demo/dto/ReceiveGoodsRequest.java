package com.example.demo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceiveGoodsRequest {

    @NotEmpty(message = "At least one item receipt must be specified")
    @Valid
    private List<ItemReceipt> receipts;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItemReceipt {
        @NotNull(message = "Purchase order item ID is required")
        private Long purchaseOrderItemId;

        @NotNull(message = "Quantity received is required")
        @Min(value = 1, message = "Received quantity must be at least 1")
        private Integer quantityReceived;
    }
}