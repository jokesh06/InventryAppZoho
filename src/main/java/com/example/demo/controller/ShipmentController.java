
package com.example.demo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.data.core.TypedPropertyPath;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import org.springframework.web.bind.annotation.*;

import com.example.demo.dto.CreateShipmentRequest;
import com.example.demo.dto.ShipmentResponse;
import com.example.demo.dto.UpdateShipmentStatusRequest;
import com.example.demo.entity.InventoryMovement;
import com.example.demo.entity.User;

import com.example.demo.service.ShipmentService;

@RestController
@RequestMapping("/api/shipments")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;

    // 1. Get all shipments with pagination
    @GetMapping
    public ResponseEntity<Page<ShipmentResponse>> getAllShipments(
                  @RequestParam(defaultValue = "0") int page,
                  @RequestParam(defaultValue = "20") int size

           ) {
                Pageable pageable = PageRequest.of(
        Math.max(page, 0),
        Math.min(Math.max(size, 1), 100),
        Sort.by(
                Sort.Direction.DESC,
                TypedPropertyPath.of(InventoryMovement::getTimestamp)
        )
);

        Page<ShipmentResponse> shipments =
                shipmentService.getAllShipments(page,size);

        return ResponseEntity.ok(shipments);
    }

    // 2. Get shipment by Catalyst ROWID
    @GetMapping("/{id}")
    public ResponseEntity<ShipmentResponse> getShipmentById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                shipmentService.getShipmentById(id));
    }

    // 3. Get shipment by Sales Order ID
    @GetMapping("/order/{orderId}")
    public ResponseEntity<ShipmentResponse> getShipmentByOrderId(
            @PathVariable Long orderId) {

        return ResponseEntity.ok(
                shipmentService.getShipmentByOrderId(orderId));
    }

    // 4. Create and process shipment
    @PostMapping
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'INVENTORY_MANAGER', 'WAREHOUSE_STAFF')")
    public ResponseEntity<ShipmentResponse> processShipment(

            @Valid @RequestBody CreateShipmentRequest request,

            @AuthenticationPrincipal User user) {

        if (user == null || user.getId() == null) {
            throw new IllegalStateException(
                    "Authenticated user information is missing");
        }

        ShipmentResponse response =
                shipmentService.createAndProcessShipment(
                        request, user.getId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    // 5. Update shipment status
    @PatchMapping("/{id}/status")
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'INVENTORY_MANAGER', 'WAREHOUSE_STAFF')")
    public ResponseEntity<ShipmentResponse> updateStatus(

            @PathVariable Long id,

            @Valid @RequestBody UpdateShipmentStatusRequest request,

            @AuthenticationPrincipal User user) {

        if (user == null || user.getId() == null) {
            throw new IllegalStateException(
                    "Authenticated user information is missing");
        }

        ShipmentResponse updated =
                shipmentService.updateShipmentStatus(
                        id, request, user.getId());

        return ResponseEntity.ok(updated);
    }
}
