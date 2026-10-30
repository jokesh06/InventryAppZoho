
package com.example.demo.entity;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransferItem {

    // Catalyst system ROWID
    private Long id;

    private StockTransfer stockTransfer;

    private Product product;

    private Integer quantity;
}
