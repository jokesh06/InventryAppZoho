
package com.example.demo.entity;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusHistory {

    // Catalyst record ID
    private Long id;

    private SalesOrder salesOrder;

    private String fromStatus;

    private String toStatus;

    private User changedBy;

    private String changeReason;

    private LocalDateTime createdAt;
}
