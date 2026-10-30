
package com.example.demo.service;

import lombok.RequiredArgsConstructor;

import org.springframework.data.core.TypedPropertyPath;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.demo.dto.CreatePurchaseOrderRequest;
import com.example.demo.dto.PurchaseOrderItemResponse;
import com.example.demo.dto.PurchaseOrderResponse;
import com.example.demo.dto.ReceiveGoodsRequest;

import com.example.demo.entity.PurchaseOrder;
import com.example.demo.entity.PurchaseOrderItem;
import com.example.demo.entity.Supplier;
import com.example.demo.entity.User;
import com.example.demo.entity.Warehouse;
import com.example.demo.entity.InventoryMovement;
import com.example.demo.entity.Product;

import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.PurchaseOrderItemRepository;
import com.example.demo.repository.PurchaseOrderRepository;
import com.example.demo.repository.SupplierRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.WarehouseRepository;

import exception.InvalidOperationException;
import exception.ResourceNotFoundException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final SupplierRepository supplierRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;

    // 1. Create Purchase Order
    public PurchaseOrderResponse createPurchaseOrder(
            CreatePurchaseOrderRequest request, Long userId) {

        if (request == null || request.getItems() == null
                || request.getItems().isEmpty()) {
            throw new IllegalArgumentException(
                    "Purchase Order must contain at least one item");
        }

        Supplier supplier = supplierRepository.findById(
                request.getSupplierId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Supplier not found: "
                                + request.getSupplierId()));

        Warehouse warehouse = warehouseRepository.findById(
                request.getDestinationWarehouseId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Warehouse not found: "
                                + request.getDestinationWarehouseId()));

        User user = getUser(userId);

        LocalDateTime now = LocalDateTime.now();

        PurchaseOrder po = PurchaseOrder.builder()
                .poNumber(
                        "PO-" + UUID.randomUUID()
                                .toString().substring(0, 8).toUpperCase())
                .supplier(supplier)
                .destinationWarehouse(warehouse)
                .status(PurchaseOrder.POStatus.DRAFT)
                .createdBy(user)
                .totalAmount(BigDecimal.ZERO)
                .createdAt(now)
                .updatedAt(now)
                .build();

        BigDecimal total = BigDecimal.ZERO;

        List<PurchaseOrderItem> items = new ArrayList<>();

        // Prepare purchase order items
        for (var itemReq : request.getItems()) {

            if (itemReq.getOrderedQty() == null
                    || itemReq.getOrderedQty() <= 0
                    || itemReq.getUnitCost() == null
                    || itemReq.getUnitCost().signum() < 0) {

                throw new IllegalArgumentException(
                        "Invalid purchase order item quantity or unit cost");
            }

            Product product = productRepository.findById(
                    itemReq.getProductId())
                    .orElseThrow(() ->
                            new ResourceNotFoundException(
                                    "Product not found: "
                                    + itemReq.getProductId()));

            BigDecimal lineTotal = itemReq.getUnitCost()
                    .multiply(BigDecimal.valueOf(
                            itemReq.getOrderedQty()));

            total = total.add(lineTotal);

            PurchaseOrderItem item = PurchaseOrderItem.builder()
                    .product(product)
                    .orderedQty(itemReq.getOrderedQty())
                    .receivedQty(0)
                    .unitCost(itemReq.getUnitCost())
                    .build();

            items.add(item);
        }

        po.setTotalAmount(total);

        // Save Purchase Order header first
        PurchaseOrder savedPO =
                purchaseOrderRepository.save(po);

        // Save each item separately using Catalyst
        for (PurchaseOrderItem item : items) {

            item.setPurchaseOrder(savedPO);

            purchaseOrderItemRepository.save(item);
        }

        // Reload items so Catalyst ROWIDs are available
        return mapToResponse(
                loadPurchaseOrder(savedPO.getId()));
    }

    // 2. Submit Purchase Order
    public PurchaseOrderResponse submitPurchaseOrder(Long poId) {

        PurchaseOrder po = loadPurchaseOrder(poId);

        if (po.getStatus() != PurchaseOrder.POStatus.DRAFT) {

            throw new InvalidOperationException(
                    "Only DRAFT purchase orders can be submitted");
        }

        po.setStatus(PurchaseOrder.POStatus.SUBMITTED);

        purchaseOrderRepository.save(po);

        return mapToResponse(
                loadPurchaseOrder(poId));
    }

    // 3. Receive goods against Purchase Order
    public PurchaseOrderResponse receiveGoods(
            Long poId,
            ReceiveGoodsRequest request,
            Long userId) {

        PurchaseOrder po = loadPurchaseOrder(poId);

        if (po.getStatus() != PurchaseOrder.POStatus.SUBMITTED
                && po.getStatus()
                        != PurchaseOrder.POStatus.PARTIALLY_RECEIVED) {

            throw new InvalidOperationException(
                    "Goods can only be received against SUBMITTED "
                    + "or PARTIALLY_RECEIVED purchase orders");
        }

        if (request == null || request.getReceipts() == null
                || request.getReceipts().isEmpty()) {

            throw new IllegalArgumentException(
                    "At least one goods receipt is required");
        }

        User user = getUser(userId);

        // Map Purchase Order items using their Catalyst ROWIDs
        Map<Long, PurchaseOrderItem> poItemsMap = new HashMap<>();

        for (PurchaseOrderItem item : po.getItems()) {
            poItemsMap.put(item.getId(), item);
        }

        // Validate all receipt quantities before modifying records.
        // This also handles multiple receipt entries for the same item.
        Map<Long, Integer> plannedReceivedQty = new HashMap<>();

        for (var receipt : request.getReceipts()) {

            PurchaseOrderItem item =
                    poItemsMap.get(receipt.getPurchaseOrderItemId());

            if (item == null) {
                throw new ResourceNotFoundException(
                        "PO Item " + receipt.getPurchaseOrderItemId()
                        + " does not belong to PO " + poId);
            }

            int receiptQty = receipt.getQuantityReceived();

            if (receiptQty <= 0) {
                throw new InvalidOperationException(
                        "Received quantity must be greater than zero");
            }

            int alreadyReceived = plannedReceivedQty.getOrDefault(
                    item.getId(),
                    item.getReceivedQty() == null
                            ? 0 : item.getReceivedQty());

            int newTotalReceived = alreadyReceived + receiptQty;

            if (newTotalReceived > item.getOrderedQty()) {

                throw new InvalidOperationException(
                        "Cannot receive more units than ordered. Item: "
                        + item.getProduct().getName()
                        + " [Ordered: " + item.getOrderedQty()
                        + ", Previously Received: " + alreadyReceived
                        + ", Attempted: " + receiptQty + "]");
            }

            plannedReceivedQty.put(item.getId(), newTotalReceived);
        }

        // Update received quantities and inventory
        for (var receipt : request.getReceipts()) {

            PurchaseOrderItem item =
                    poItemsMap.get(receipt.getPurchaseOrderItemId());

            int receiptQty = receipt.getQuantityReceived();

            int currentReceived = item.getReceivedQty() == null
                    ? 0 : item.getReceivedQty();

            item.setReceivedQty(currentReceived + receiptQty);

            purchaseOrderItemRepository.save(item);

            // Update inventory and create movement record
            inventoryService.receiveGoods(
                    po.getDestinationWarehouse().getId(),
                    item.getProduct().getId(),
                    receiptQty,
                    po.getPoNumber(),
                    userId);
        }

        // Determine overall Purchase Order status
        boolean allComplete = po.getItems().stream()
                .allMatch(item ->
                        Objects.equals(
                                item.getReceivedQty(),
                                item.getOrderedQty()));

        boolean anyReceived = po.getItems().stream()
                .anyMatch(item ->
                        item.getReceivedQty() != null
                        && item.getReceivedQty() > 0);

        if (allComplete) {

            po.setStatus(PurchaseOrder.POStatus.RECEIVED);

        } else if (anyReceived) {

            po.setStatus(
                    PurchaseOrder.POStatus.PARTIALLY_RECEIVED);
        }

        purchaseOrderRepository.save(po);

        return mapToResponse(
                loadPurchaseOrder(poId));
    }

    // 4. Load Purchase Order with complete related records
    private PurchaseOrder loadPurchaseOrder(Long poId) {

        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Purchase Order not found: " + poId));

        // Load complete Supplier details
        if (po.getSupplier() != null
                && po.getSupplier().getId() != null) {

            Long supplierId = po.getSupplier().getId();

            Supplier supplier = supplierRepository.findById(supplierId)
                    .orElseThrow(() ->
                            new ResourceNotFoundException(
                                    "Supplier not found: " + supplierId));

            po.setSupplier(supplier);
        }

        // Load complete Warehouse details
        if (po.getDestinationWarehouse() != null
                && po.getDestinationWarehouse().getId() != null) {

            Long warehouseId =
                    po.getDestinationWarehouse().getId();

            Warehouse warehouse =
                    warehouseRepository.findById(warehouseId)
                            .orElseThrow(() ->
                                    new ResourceNotFoundException(
                                            "Warehouse not found: "
                                            + warehouseId));

            po.setDestinationWarehouse(warehouse);
        }

        // Load Purchase Order items separately
        List<PurchaseOrderItem> items =
                purchaseOrderItemRepository.findByPurchaseOrderId(poId);

        for (PurchaseOrderItem item : items) {

            item.setPurchaseOrder(po);

            if (item.getReceivedQty() == null) {
                item.setReceivedQty(0);
            }

            // Load full Product details
            if (item.getProduct() != null
                    && item.getProduct().getId() != null) {

                Long productId = item.getProduct().getId();

                Product product = productRepository.findById(productId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Product not found: " + productId));

                item.setProduct(product);
            }
        }

        po.setItems(items);

        return po;
    }

    // 5. Find User
    private User getUser(Long userId) {

        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found: " + userId));
    }

    // 6. Convert Purchase Order entity into response DTO
    public PurchaseOrderResponse mapToResponse(PurchaseOrder po) {

        return PurchaseOrderResponse.builder()
                .id(po.getId())
                .poNumber(po.getPoNumber())
                .supplierId(po.getSupplier().getId())
                .supplierName(po.getSupplier().getName())
                .destinationWarehouseId(
                        po.getDestinationWarehouse().getId())
                .destinationWarehouseName(
                        po.getDestinationWarehouse().getName())
                .status(po.getStatus().name())
                .totalAmount(po.getTotalAmount())
                .items(po.getItems().stream()
                        .map(item ->
                                PurchaseOrderItemResponse.builder()
                                        .id(item.getId())
                                        .productId(item.getProduct().getId())
                                        .productSku(item.getProduct().getSku())
                                        .productName(item.getProduct().getName())
                                        .orderedQty(item.getOrderedQty())
                                        .receivedQty(item.getReceivedQty())
                                        .remainingQty(item.getRemainingQty())
                                        .unitCost(item.getUnitCost())
                                        .lineTotal(
                                                item.getUnitCost().multiply(
                                                        BigDecimal.valueOf(
                                                                item.getOrderedQty())))
                                        .build())
                        .toList())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }
    public PurchaseOrderResponse getPurchaseOrderById(Long poId) {

    return mapToResponse(loadPurchaseOrder(poId));
}

// Get all Purchase Orders with pagination
public Page<PurchaseOrderResponse> getAllPurchaseOrders(
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

    return purchaseOrderRepository.findAll(page,size)
            .map(po -> mapToResponse(
                    loadPurchaseOrder(po.getId())));
}

// Get Purchase Orders filtered by status
public Page<PurchaseOrderResponse> getPurchaseOrdersByStatus(
        PurchaseOrder.POStatus status,
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

    return purchaseOrderRepository.findByStatus(status, page,size)
            .map(po -> mapToResponse(
                    loadPurchaseOrder(po.getId())));
}
}
