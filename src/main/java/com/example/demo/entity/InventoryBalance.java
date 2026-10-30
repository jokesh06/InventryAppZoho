package com.example.demo.entity;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryBalance {

    private Long id;

    private Warehouse warehouse;

    private Product product;

    private Integer onHandQty;

    private Integer reservedQty;

    private Long version;

    private LocalDateTime updatedAt;

    public Integer getAvailableQty() {
        return this.onHandQty - this.reservedQty;
    }
}