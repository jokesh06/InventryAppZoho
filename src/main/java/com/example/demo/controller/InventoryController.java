
package com.example.demo.controller;

import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.core.TypedPropertyPath;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import org.springframework.web.bind.annotation.*;

import com.example.demo.dto.InventoryBalanceResponse;
import com.example.demo.dto.InventoryMovementResponse;
import com.example.demo.dto.ManualAdjustmentRequest;

import com.example.demo.entity.InventoryBalance;
import com.example.demo.entity.InventoryMovement;
import com.example.demo.entity.Product;
import com.example.demo.entity.User;
import com.example.demo.entity.Warehouse;

import com.example.demo.repository.InventoryBalanceRepository;
import com.example.demo.repository.InventoryMovementRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.WarehouseRepository;
import com.example.demo.repository.UserRepository;

import exception.InsufficientStockException;
import exception.ResourceNotFoundException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryBalanceRepository inventoryBalanceRepository;

    private final InventoryMovementRepository movementRepository;

    private final WarehouseRepository warehouseRepository;

    private final ProductRepository productRepository;

    private final UserRepository userRepository;

    // 1. Get inventory stock by warehouse
    @GetMapping("/warehouse/{warehouseId}")
    public ResponseEntity<List<InventoryBalanceResponse>>
            getStockByWarehouse(@PathVariable Long warehouseId) {

        if (warehouseId == null || warehouseId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid Warehouse ID");
        }

        // Verify warehouse exists
        warehouseRepository.findById(warehouseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Warehouse not found: " + warehouseId));

        List<InventoryBalance> balances =
                inventoryBalanceRepository
                        .findByWarehouseIdWithProductAndWarehouse(
                                warehouseId);

        return ResponseEntity.ok(
                balances.stream()
                        .map(this::mapBalanceToResponse)
                        .toList());
    }

    // 2. Get stock for a specific product at a warehouse
    @GetMapping("/balance")
    public ResponseEntity<InventoryBalanceResponse>
            getProductStockAtWarehouse(
                    @RequestParam Long warehouseId,
                    @RequestParam Long productId) {

        InventoryBalance balance =
                inventoryBalanceRepository
                        .findByWarehouseIdAndProductId(
                                warehouseId, productId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "No inventory record found for warehouse "
                                        + warehouseId
                                        + " and product " + productId));

        return ResponseEntity.ok(
                mapBalanceToResponse(balance));
    }

    // 3. Manually adjust inventory stock
    @PostMapping("/adjust")
    @PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_MANAGER')")
    public ResponseEntity<InventoryBalanceResponse> adjustStock(
            @Valid @RequestBody ManualAdjustmentRequest request,
            @AuthenticationPrincipal User user) {

        if (request.getWarehouseId() == null
                || request.getWarehouseId() <= 0
                || request.getProductId() == null
                || request.getProductId() <= 0) {

            throw new IllegalArgumentException(
                    "Valid Warehouse ID and Product ID are required");
        }

        Integer quantityChange = request.getQuantityChange();

        if (quantityChange == null || quantityChange == 0) {
            throw new IllegalArgumentException(
                    "Quantity change cannot be zero or null");
        }

        if (user == null || user.getId() == null) {
            throw new IllegalStateException(
                    "Authenticated user information is missing");
        }

        Long warehouseId = request.getWarehouseId();
        Long productId = request.getProductId();

        // Find existing inventory balance
        InventoryBalance balance =
                inventoryBalanceRepository
                        .findByWarehouseIdAndProductIdWithPessimisticLock(
                                warehouseId, productId)
                        .orElseGet(() -> {

                            Warehouse warehouse =
                                    warehouseRepository.findById(warehouseId)
                                            .orElseThrow(() ->
                                                    new ResourceNotFoundException(
                                                            "Warehouse not found: "
                                                            + warehouseId));

                            Product product =
                                    productRepository.findById(productId)
                                            .orElseThrow(() ->
                                                    new ResourceNotFoundException(
                                                            "Product not found: "
                                                            + productId));

                            return InventoryBalance.builder()
                                    .warehouse(warehouse)
                                    .product(product)
                                    .onHandQty(0)
                                    .reservedQty(0)
                                    .version(0L)
                                    .build();
                        });

        int currentOnHand = getQty(balance.getOnHandQty());

        int currentReserved = getQty(balance.getReservedQty());

        int newOnHand;

        try {
            newOnHand = Math.addExact(
                    currentOnHand, quantityChange);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "Inventory quantity exceeds the supported range", e);
        }

        // Validate stock invariants
        if (newOnHand < 0 || newOnHand < currentReserved) {

            throw new InsufficientStockException(
                    "Manual adjustment rejected: Cannot reduce "
                    + "on-hand stock below reserved quantity ("
                    + currentReserved + " units currently reserved)");
        }

        balance.setOnHandQty(newOnHand);

        // Save inventory balance in Catalyst
        InventoryBalance savedBalance =
                inventoryBalanceRepository.save(balance);

        // Create inventory audit movement
        InventoryMovement movement =
                InventoryMovement.builder()
                        .movementType(
                                InventoryMovement.MovementType.MANUAL_ADJUSTMENT)
                        .product(savedBalance.getProduct())
                        .warehouse(savedBalance.getWarehouse())
                        .onHandChange(quantityChange)
                        .reservedChange(0)
                        .referenceDocument(
                                "MANUAL-ADJ-" + UUID.randomUUID())
                        .reason(request.getReason())
                        .user(user)
                        .timestamp(LocalDateTime.now())
                        .build();

        movementRepository.save(movement);

        return ResponseEntity.ok(
                mapBalanceToResponse(savedBalance));
    }

    // 4. Get inventory movement history with pagination and filters
    @GetMapping("/movements")
    public ResponseEntity<Page<InventoryMovementResponse>>
            getMovements(
                    @RequestParam(required = false) Long warehouseId,
                    @RequestParam(required = false) Long productId,

                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime startDate,

                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime endDate,
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

        if (warehouseId != null && warehouseId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid Warehouse ID");
        }

        if (productId != null && productId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid Product ID");
        }

        if (startDate != null && endDate != null
                && startDate.isAfter(endDate)) {

            throw new IllegalArgumentException(
                    "Start date cannot be after end date");
        }

        Page<InventoryMovement> movements =
                movementRepository.filterMovements(
                        warehouseId,
                        productId,
                        startDate,
                        endDate,
                        page,size);

        // Explicitly load Product, Warehouse and User references
        Page<InventoryMovementResponse> response =
                movements.map(this::mapMovementToResponse);

        return ResponseEntity.ok(response);
    }

    // 5. Map inventory balance into response DTO
    private InventoryBalanceResponse mapBalanceToResponse(
            InventoryBalance balance) {

        if (balance.getWarehouse() == null
                || balance.getWarehouse().getId() == null
                || balance.getProduct() == null
                || balance.getProduct().getId() == null) {

            throw new IllegalStateException(
                    "Inventory balance has missing Warehouse or Product reference");
        }

        // Load complete warehouse information
        Long warehouseId = balance.getWarehouse().getId();

        Warehouse warehouse =
                warehouseRepository.findById(warehouseId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Warehouse not found: " + warehouseId));

        // Load complete product information
        Long productId = balance.getProduct().getId();

        Product product =
                productRepository.findById(productId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Product not found: " + productId));

        balance.setWarehouse(warehouse);
        balance.setProduct(product);

        return InventoryBalanceResponse.builder()
                .id(balance.getId())
                .warehouseId(warehouse.getId())
                .warehouseCode(warehouse.getCode())
                .warehouseName(warehouse.getName())
                .productId(product.getId())
                .productSku(product.getSku())
                .productName(product.getName())
                .onHandQty(getQty(balance.getOnHandQty()))
                .reservedQty(getQty(balance.getReservedQty()))
                .availableQty(balance.getAvailableQty())
                .updatedAt(balance.getUpdatedAt())
                .build();
    }

    // 6. Map inventory movement into response DTO
    private InventoryMovementResponse mapMovementToResponse(
            InventoryMovement movement) {

        if (movement.getProduct() == null
                || movement.getWarehouse() == null) {

            throw new IllegalStateException(
                    "Inventory movement has missing Product or Warehouse reference");
        }

        // Load complete Product
        Long productId = movement.getProduct().getId();

        Product product =
                productRepository.findById(productId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Product not found: " + productId));

        // Load complete Warehouse
        Long warehouseId = movement.getWarehouse().getId();

        Warehouse warehouse =
                warehouseRepository.findById(warehouseId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Warehouse not found: " + warehouseId));

        movement.setProduct(product);
        movement.setWarehouse(warehouse);

        // Load user who performed the movement
        String username = "SYSTEM";

        if (movement.getUser() != null
                && movement.getUser().getId() != null) {

            Long userId = movement.getUser().getId();

            User user = userRepository.findById(userId)
                    .orElseThrow(() ->
                            new ResourceNotFoundException(
                                    "User not found: " + userId));

            movement.setUser(user);

            username = user.getUsername();
        }

        return InventoryMovementResponse.builder()
                .id(movement.getId())
                .movementType(
                        movement.getMovementType().name())
                .productId(product.getId())
                .productSku(product.getSku())
                .productName(product.getName())
                .warehouseId(warehouse.getId())
                .warehouseCode(warehouse.getCode())
                .onHandChange(movement.getOnHandChange())
                .reservedChange(movement.getReservedChange())
                .referenceDocument(movement.getReferenceDocument())
                .reason(movement.getReason())
                .performedByUsername(username)
                .timestamp(movement.getTimestamp())
                .build();
    }

    // Null-safe quantity conversion
    private int getQty(Integer quantity) {
        return quantity == null ? 0 : quantity;
    }
}
