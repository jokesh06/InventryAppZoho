
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
        int ordered = this.orderedQty == null ? 0 : this.orderedQty;
        int received = this.receivedQty == null ? 0 : this.receivedQty;
        return ordered - received;
    }
}
