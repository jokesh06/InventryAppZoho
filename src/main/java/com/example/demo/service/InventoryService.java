
package com.example.demo.service;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import com.example.demo.entity.InventoryBalance;
import com.example.demo.entity.InventoryMovement;
import com.example.demo.entity.Product;
import com.example.demo.entity.User;
import com.example.demo.entity.Warehouse;

import com.example.demo.repository.InventoryBalanceRepository;
import com.example.demo.repository.InventoryMovementRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.WarehouseRepository;

import exception.InsufficientStockException;
import exception.ResourceNotFoundException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final InventoryMovementRepository movementRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;

    // 1. Reserve stock for sales order
    public void reserveStockForOrder(
            Long warehouseId,
            Map<Long, Integer> productQuantities,
            String orderNumber,
            Long userId) {

        validateProductQuantities(warehouseId, productQuantities);

        User user = getUser(userId);

        List<Long> productIds = getOrderedProductIds(productQuantities);

        List<InventoryBalance> balances =
                inventoryBalanceRepository
                        .findByWarehouseIdAndProductIdsOrderedWithPessimisticLock(
                                warehouseId, productIds);

        Map<Long, InventoryBalance> balanceMap = createBalanceMap(balances);

        // Validate every product before changing any balance
        for (Long productId : productIds) {

            int requestedQty = productQuantities.get(productId);

            InventoryBalance balance = balanceMap.get(productId);

            if (balance == null
                    || balance.getAvailableQty() < requestedQty) {

                int available = balance == null
                        ? 0
                        : balance.getAvailableQty();

                throw new InsufficientStockException(
                        "Insufficient stock for product ID " + productId
                        + ". Requested: " + requestedQty
                        + ", Available: " + available);
            }
        }

        // Update balances and record movements
        for (Long productId : productIds) {

            int requestedQty = productQuantities.get(productId);

            InventoryBalance balance = balanceMap.get(productId);

            balance.setReservedQty(
                    getQty(balance.getReservedQty()) + requestedQty);

            inventoryBalanceRepository.save(balance);

            InventoryMovement movement = createMovement(
                    InventoryMovement.MovementType.SALE_CONFIRMATION,
                    balance,
                    0,
                    requestedQty,
                    orderNumber,
                    "Reservation for Sales Order " + orderNumber,
                    user);

            movementRepository.save(movement);
        }
    }

    // 2. Release stock reservation
    public void releaseReservationForOrder(
            Long warehouseId,
            Map<Long, Integer> productQuantities,
            String orderNumber,
            Long userId) {

        validateProductQuantities(warehouseId, productQuantities);

        User user = getUser(userId);

        List<Long> productIds = getOrderedProductIds(productQuantities);

        List<InventoryBalance> balances =
                inventoryBalanceRepository
                        .findByWarehouseIdAndProductIdsOrderedWithPessimisticLock(
                                warehouseId, productIds);

        Map<Long, InventoryBalance> balanceMap = createBalanceMap(balances);

        // Validate before updating any balance
        for (Long productId : productIds) {

            InventoryBalance balance = balanceMap.get(productId);

            int releaseQty = productQuantities.get(productId);

            if (balance == null
                    || getQty(balance.getReservedQty()) < releaseQty) {

                throw new IllegalStateException(
                        "Insufficient reserved quantity for product ID "
                        + productId);
            }
        }

        for (Long productId : productIds) {

            int releaseQty = productQuantities.get(productId);

            InventoryBalance balance = balanceMap.get(productId);

            balance.setReservedQty(
                    getQty(balance.getReservedQty()) - releaseQty);

            inventoryBalanceRepository.save(balance);

            InventoryMovement movement = createMovement(
                    InventoryMovement.MovementType.SALE_CANCELLATION,
                    balance,
                    0,
                    -releaseQty,
                    orderNumber,
                    "Cancelled reservation for Sales Order " + orderNumber,
                    user);

            movementRepository.save(movement);
        }
    }

    // 3. Fulfill shipment
    public void fulfillShipment(
            Long warehouseId,
            Map<Long, Integer> productQuantities,
            String shipmentNumber,
            Long userId) {

        validateProductQuantities(warehouseId, productQuantities);

        User user = getUser(userId);

        List<Long> productIds = getOrderedProductIds(productQuantities);

        List<InventoryBalance> balances =
                inventoryBalanceRepository
                        .findByWarehouseIdAndProductIdsOrderedWithPessimisticLock(
                                warehouseId, productIds);

        Map<Long, InventoryBalance> balanceMap = createBalanceMap(balances);

        // Validate every product before updating stock
        for (Long productId : productIds) {

            int qtyToShip = productQuantities.get(productId);

            InventoryBalance balance = balanceMap.get(productId);

            if (balance == null
                    || getQty(balance.getOnHandQty()) < qtyToShip
                    || getQty(balance.getReservedQty()) < qtyToShip) {

                throw new IllegalStateException(
                        "Stock state invalid for shipment fulfillment "
                        + "on product ID " + productId);
            }
        }

        for (Long productId : productIds) {

            int qtyToShip = productQuantities.get(productId);

            InventoryBalance balance = balanceMap.get(productId);

            balance.setOnHandQty(
                    getQty(balance.getOnHandQty()) - qtyToShip);

            balance.setReservedQty(
                    getQty(balance.getReservedQty()) - qtyToShip);

            inventoryBalanceRepository.save(balance);

            InventoryMovement movement = createMovement(
                    InventoryMovement.MovementType.SALE_SHIPMENT,
                    balance,
                    -qtyToShip,
                    -qtyToShip,
                    shipmentNumber,
                    "Shipped under Shipment " + shipmentNumber,
                    user);

            movementRepository.save(movement);
        }
    }

    // 4. Receive goods against purchase order
    public void receiveGoods(
            Long warehouseId,
            Long productId,
            int qtyReceived,
            String poNumber,
            Long userId) {

        if (warehouseId == null || warehouseId <= 0
                || productId == null || productId <= 0
                || qtyReceived <= 0) {

            throw new IllegalArgumentException(
                    "Warehouse ID, Product ID and received quantity "
                    + "must be valid");
        }

        User user = getUser(userId);

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

        // Increase on-hand quantity
        balance.setOnHandQty(
                getQty(balance.getOnHandQty()) + qtyReceived);

        inventoryBalanceRepository.save(balance);

        InventoryMovement movement = createMovement(
                InventoryMovement.MovementType.PURCHASE_RECEIPT,
                balance,
                qtyReceived,
                0,
                poNumber,
                "Received goods for PO " + poNumber,
                user);

        movementRepository.save(movement);
    }

    // Get user using Catalyst repository
    private User getUser(Long userId) {

        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found: " + userId));
    }

    // Get product IDs in sorted order
    private List<Long> getOrderedProductIds(
            Map<Long, Integer> productQuantities) {

        return productQuantities.keySet()
                .stream()
                .sorted()
                .collect(Collectors.toList());
    }

    // Create product balance map
    private Map<Long, InventoryBalance> createBalanceMap(
            List<InventoryBalance> balances) {

        Map<Long, InventoryBalance> balanceMap = new HashMap<>();

        for (InventoryBalance balance : balances) {

            Long productId = balance.getProduct().getId();

            if (balanceMap.putIfAbsent(productId, balance) != null) {
                throw new IllegalStateException(
                        "Duplicate inventory balance for product ID: "
                        + productId);
            }
        }

        return balanceMap;
    }

    // Validate requested quantities
    private void validateProductQuantities(
            Long warehouseId,
            Map<Long, Integer> productQuantities) {

        if (warehouseId == null || warehouseId <= 0) {
            throw new IllegalArgumentException(
                    "Warehouse ID must be valid");
        }

        if (productQuantities == null || productQuantities.isEmpty()) {
            throw new IllegalArgumentException(
                    "Product quantities cannot be empty");
        }

        for (Map.Entry<Long, Integer> entry
                : productQuantities.entrySet()) {

            if (entry.getKey() == null || entry.getKey() <= 0
                    || entry.getValue() == null
                    || entry.getValue() <= 0) {

                throw new IllegalArgumentException(
                        "Product ID and quantity must be valid");
            }
        }
    }

    // Handle null quantities
    private int getQty(Integer quantity) {
        return quantity == null ? 0 : quantity;
    }

    // Create inventory movement
    private InventoryMovement createMovement(
            InventoryMovement.MovementType movementType,
            InventoryBalance balance,
            Integer onHandChange,
            Integer reservedChange,
            String referenceDocument,
            String reason,
            User user) {

        return InventoryMovement.builder()
                .movementType(movementType)
                .product(balance.getProduct())
                .warehouse(balance.getWarehouse())
                .onHandChange(onHandChange)
                .reservedChange(reservedChange)
                .referenceDocument(referenceDocument)
                .reason(reason)
                .user(user)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
