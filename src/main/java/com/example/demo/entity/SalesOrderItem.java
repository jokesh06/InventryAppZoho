
package com.example.demo.entity;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesOrderItem {

    // Catalyst system ROWID
    private Long id;

    private SalesOrder salesOrder;

    private Product product;

    private Integer quantity;

    private BigDecimal unitPriceSnapshot;

    private BigDecimal subtotal;
}
