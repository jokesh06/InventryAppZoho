
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

import com.example.demo.dto.CreateStockTransferRequest;
import com.example.demo.dto.StockTransferResponse;
import com.example.demo.entity.InventoryMovement;
import com.example.demo.entity.User;
import com.example.demo.service.StockTransferService;

@RestController
@RequestMapping("/api/stock-transfers")
@RequiredArgsConstructor
public class StockTransferController {

    private final StockTransferService stockTransferService;

    // 1. Get all stock transfers with pagination
    @GetMapping
    public ResponseEntity<Page<StockTransferResponse>> getAllTransfers(
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

        Page<StockTransferResponse> transfers =
                stockTransferService.getAllTransfers(page,size);

        return ResponseEntity.ok(transfers);
    }

    // 2. Get stock transfer by Catalyst ROWID
    @GetMapping("/{id}")
    public ResponseEntity<StockTransferResponse> getTransferById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                stockTransferService.getTransferById(id));
    }

    // 3. Create stock transfer
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_MANAGER')")
    public ResponseEntity<StockTransferResponse> createTransfer(

            @Valid @RequestBody CreateStockTransferRequest request,

            @AuthenticationPrincipal User user) {

        if (user == null || user.getId() == null) {
            throw new IllegalStateException(
                    "Authenticated user information is missing");
        }

        StockTransferResponse response =
                stockTransferService.createTransfer(
                        request, user.getId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    // 4. Dispatch stock transfer
    @PostMapping("/{id}/dispatch")
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'INVENTORY_MANAGER', 'WAREHOUSE_STAFF')")
    public ResponseEntity<StockTransferResponse> dispatchTransfer(

            @PathVariable Long id,

            @AuthenticationPrincipal User user) {

        if (user == null || user.getId() == null) {
            throw new IllegalStateException(
                    "Authenticated user information is missing");
        }

        return ResponseEntity.ok(
                stockTransferService.dispatchTransfer(
                        id, user.getId()));
    }

    // 5. Receive stock transfer
    @PostMapping("/{id}/receive")
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'INVENTORY_MANAGER', 'WAREHOUSE_STAFF')")
    public ResponseEntity<StockTransferResponse> receiveTransfer(

            @PathVariable Long id,

            @AuthenticationPrincipal User user) {

        if (user == null || user.getId() == null) {
            throw new IllegalStateException(
                    "Authenticated user information is missing");
        }

        return ResponseEntity.ok(
                stockTransferService.receiveTransfer(
                        id, user.getId()));
    }
}
