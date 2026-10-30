
package com.example.demo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.data.core.TypedPropertyPath;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import org.springframework.web.bind.annotation.*;

import com.example.demo.dto.CreatePurchaseOrderRequest;
import com.example.demo.dto.PurchaseOrderResponse;
import com.example.demo.dto.ReceiveGoodsRequest;
import com.example.demo.entity.InventoryMovement;
import com.example.demo.entity.PurchaseOrder;
import com.example.demo.entity.User;

import com.example.demo.service.PurchaseOrderService;

import exception.InvalidOperationException;

@RestController
@RequestMapping("/api/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    // 1. Get all Purchase Orders with optional status filter
    @GetMapping
    public ResponseEntity<Page<PurchaseOrderResponse>>
            getAllPurchaseOrders(

                    @RequestParam(required = false) String status,
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

        Page<PurchaseOrderResponse> result;

        if (status != null && !status.isBlank()) {

            PurchaseOrder.POStatus poStatus;

            try {
                poStatus = PurchaseOrder.POStatus.valueOf(
                        status.trim().toUpperCase());

            } catch (IllegalArgumentException e) {

                throw new InvalidOperationException(
                        "Invalid Purchase Order status: " + status);
            }

            result = purchaseOrderService
                    .getPurchaseOrdersByStatus(poStatus, page,size);

        } else {

            result = purchaseOrderService
                    .getAllPurchaseOrders(page,size);
        }

        return ResponseEntity.ok(result);
    }

    // 2. Get Purchase Order by Catalyst ROWID
    @GetMapping("/{id}")
    public ResponseEntity<PurchaseOrderResponse>
            getPurchaseOrderById(@PathVariable Long id) {

        return ResponseEntity.ok(
                purchaseOrderService.getPurchaseOrderById(id));
    }

    // 3. Create Purchase Order
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_MANAGER')")
    public ResponseEntity<PurchaseOrderResponse> createPurchaseOrder(

            @Valid @RequestBody CreatePurchaseOrderRequest request,

            @AuthenticationPrincipal User user) {

        if (user == null || user.getId() == null) {
            throw new IllegalStateException(
                    "Authenticated user information is missing");
        }

        PurchaseOrderResponse created =
                purchaseOrderService.createPurchaseOrder(
                        request, user.getId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(created);
    }

    // 4. Submit Purchase Order
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_MANAGER')")
    public ResponseEntity<PurchaseOrderResponse>
            submitPurchaseOrder(@PathVariable Long id) {

        return ResponseEntity.ok(
                purchaseOrderService.submitPurchaseOrder(id));
    }

    // 5. Receive goods against Purchase Order
    @PostMapping("/{id}/receive")
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'INVENTORY_MANAGER', 'WAREHOUSE_STAFF')")
    public ResponseEntity<PurchaseOrderResponse> receiveGoods(

            @PathVariable Long id,

            @Valid @RequestBody ReceiveGoodsRequest request,

            @AuthenticationPrincipal User user) {

        if (user == null || user.getId() == null) {
            throw new IllegalStateException(
                    "Authenticated user information is missing");
        }

        PurchaseOrderResponse received =
                purchaseOrderService.receiveGoods(
                        id, request, user.getId());

        return ResponseEntity.ok(received);
    }
}
