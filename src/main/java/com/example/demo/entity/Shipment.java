
package com.example.demo.entity;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shipment {

    // Catalyst system ROWID
    private Long id;

    private String shipmentNumber;

    private SalesOrder salesOrder;

    private String carrier;

    private String trackingNumber;

    @Builder.Default
    private ShipmentStatus status = ShipmentStatus.PACKED;

    private LocalDateTime shippedAt;

    private LocalDateTime deliveredAt;

    private User createdBy;

    public enum ShipmentStatus {
        PACKED,
        IN_TRANSIT,
        DELIVERED,
        RETURNED
    }
}
