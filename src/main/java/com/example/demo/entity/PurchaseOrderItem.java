
package com.example.demo.entity;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderItem {

    // Catalyst ROWID
    private Long id;

    private PurchaseOrder purchaseOrder;

    private Product product;

    private Integer orderedQty;

    @Builder.Default
    private Integer receivedQty = 0;

    private BigDecimal unitCost;

    public Integer getRemainingQty() {
        return this.orderedQty - this.receivedQty;
    }
}
