
package com.example.demo.service;

import lombok.RequiredArgsConstructor;

import org.springframework.data.core.TypedPropertyPath;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.demo.dto.CreateStockTransferRequest;
import com.example.demo.dto.StockTransferItemResponse;
import com.example.demo.dto.StockTransferResponse;

import com.example.demo.entity.InventoryBalance;
import com.example.demo.entity.InventoryMovement;
import com.example.demo.entity.StockTransfer;
import com.example.demo.entity.StockTransferItem;
import com.example.demo.entity.Product;
import com.example.demo.entity.User;
import com.example.demo.entity.Warehouse;

import com.example.demo.repository.InventoryBalanceRepository;
import com.example.demo.repository.InventoryMovementRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.StockTransferRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.WarehouseRepository;

import exception.InsufficientStockException;
import exception.InvalidOperationException;
import exception.ResourceNotFoundException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockTransferService {

    private final StockTransferRepository stockTransferRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final InventoryMovementRepository movementRepository;

    // 1. Create Stock Transfer
    public StockTransferResponse createTransfer(
            CreateStockTransferRequest request, Long userId) {

        if (request == null
                || request.getSourceWarehouseId() == null
                || request.getDestinationWarehouseId() == null) {

            throw new IllegalArgumentException(
                    "Source and destination warehouse IDs are required");
        }

        if (request.getSourceWarehouseId() <= 0
                || request.getDestinationWarehouseId() <= 0
                || Objects.equals(
                        request.getSourceWarehouseId(),
                        request.getDestinationWarehouseId())) {

            throw new InvalidOperationException(
                    "Source and destination warehouses must be different "
                    + "and valid");
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException(
                    "Stock transfer must contain at least one item");
        }

        Warehouse source = warehouseRepository
                .findById(request.getSourceWarehouseId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Source warehouse not found: "
                                + request.getSourceWarehouseId()));

        Warehouse destination = warehouseRepository
                .findById(request.getDestinationWarehouseId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Destination warehouse not found: "
                                + request.getDestinationWarehouseId()));

        User user = getUser(userId);

        // Validate and prepare transfer items
        List<StockTransferItem> items = new ArrayList<>();

        for (var itemReq : request.getItems()) {

            if (itemReq.getProductId() == null
                    || itemReq.getProductId() <= 0
                    || itemReq.getQuantity() == null
                    || itemReq.getQuantity() <= 0) {

                throw new IllegalArgumentException(
                        "Product ID and transfer quantity must be valid");
            }

            Product product = productRepository
                    .findById(itemReq.getProductId())
                    .orElseThrow(() ->
                            new ResourceNotFoundException(
                                    "Product not found: "
                                    + itemReq.getProductId()));

            StockTransferItem item = StockTransferItem.builder()
                    .product(product)
                    .quantity(itemReq.getQuantity())
                    .build();

            items.add(item);
        }

        LocalDateTime now = LocalDateTime.now();

        StockTransfer transfer = StockTransfer.builder()
                .transferNumber(
                        "TRF-" + UUID.randomUUID()
                                .toString().substring(0, 8).toUpperCase())
                .sourceWarehouse(source)
                .destinationWarehouse(destination)
                .status(StockTransfer.TransferStatus.DRAFT)
                .createdBy(user)
                .createdAt(now)
                .items(new ArrayList<>())
                .build();

        // Save transfer header first
        StockTransfer savedTransfer =
                stockTransferRepository.save(transfer);

        // Save each transfer item separately in Catalyst
       

        // Reload complete transfer including its items
        return mapToResponse(
                loadStockTransfer(savedTransfer.getId()));
    }

    // 2. Dispatch Stock Transfer
    public StockTransferResponse dispatchTransfer(
            Long transferId, Long userId) {

        StockTransfer transfer = loadStockTransfer(transferId);

        if (transfer.getStatus() != StockTransfer.TransferStatus.DRAFT) {

            throw new InvalidOperationException(
                    "Only DRAFT transfers can be dispatched");
        }

        User user = getUser(userId);

        Long sourceWarehouseId =
                transfer.getSourceWarehouse().getId();

        // Aggregate repeated products and sort product IDs
        Map<Long, Integer> productQuantities =
                getProductQuantities(transfer.getItems());

        List<Long> productIds =
                new ArrayList<>(productQuantities.keySet());

        // Fetch source warehouse balances
        List<InventoryBalance> sourceBalances =
                inventoryBalanceRepository
                        .findByWarehouseIdAndProductIdsOrderedWithPessimisticLock(
                                sourceWarehouseId, productIds);

        Map<Long, InventoryBalance> balanceMap =
                createBalanceMap(sourceBalances);

        // Validate all stock before performing updates
        for (Long productId : productIds) {

            InventoryBalance balance = balanceMap.get(productId);

            int requiredQty = productQuantities.get(productId);

            if (balance == null
                    || balance.getAvailableQty() < requiredQty) {

                int available = balance == null
                        ? 0
                        : balance.getAvailableQty();

                Product product = findTransferProduct(
                        transfer.getItems(), productId);

                throw new InsufficientStockException(
                        "Insufficient available stock at source warehouse for "
                        + product.getName()
                        + ". Required: " + requiredQty
                        + ", Available: " + available);
            }
        }

        // Deduct stock and create movement records
        for (Long productId : productIds) {

            InventoryBalance balance = balanceMap.get(productId);

            int quantity = productQuantities.get(productId);

            Product product = findTransferProduct(
                    transfer.getItems(), productId);

            balance.setOnHandQty(
                    getQty(balance.getOnHandQty()) - quantity);

            inventoryBalanceRepository.save(balance);

            InventoryMovement movement =
                    InventoryMovement.builder()
                            .movementType(
                                    InventoryMovement.MovementType.TRANSFER_OUT)
                            .product(product)
                            .warehouse(transfer.getSourceWarehouse())
                            .onHandChange(-quantity)
                            .reservedChange(0)
                            .referenceDocument(
                                    transfer.getTransferNumber())
                            .reason(
                                    "Dispatched to warehouse "
                                    + transfer.getDestinationWarehouse().getCode())
                            .user(user)
                            .timestamp(LocalDateTime.now())
                            .build();

            movementRepository.save(movement);
        }

        // Update transfer status
        transfer.setStatus(
                StockTransfer.TransferStatus.DISPATCHED);

        transfer.setDispatchedAt(LocalDateTime.now());

        stockTransferRepository.save(transfer);

        return mapToResponse(
                loadStockTransfer(transferId));
    }

    // 3. Receive Stock Transfer
    public StockTransferResponse receiveTransfer(
            Long transferId, Long userId) {

        StockTransfer transfer = loadStockTransfer(transferId);

        if (transfer.getStatus()
                != StockTransfer.TransferStatus.DISPATCHED) {

            throw new InvalidOperationException(
                    "Only DISPATCHED transfers can be received");
        }

        User user = getUser(userId);

        Long destinationWarehouseId =
                transfer.getDestinationWarehouse().getId();

        Map<Long, Integer> productQuantities =
                getProductQuantities(transfer.getItems());

        List<Long> productIds =
                new ArrayList<>(productQuantities.keySet());

        // Fetch destination balances before updating stock
        Map<Long, InventoryBalance> destinationBalances =
                new LinkedHashMap<>();

        for (Long productId : productIds) {

            InventoryBalance balance =
                    inventoryBalanceRepository
                            .findByWarehouseIdAndProductIdWithPessimisticLock(
                                    destinationWarehouseId, productId)
                            .orElseGet(() ->
                                    InventoryBalance.builder()
                                            .warehouse(
                                                    transfer.getDestinationWarehouse())
                                            .product(
                                                    findTransferProduct(
                                                            transfer.getItems(),
                                                            productId))
                                            .onHandQty(0)
                                            .reservedQty(0)
                                            .version(0L)
                                            .build());

            destinationBalances.put(productId, balance);
        }

        // Increase destination stock and record movements
        for (Long productId : productIds) {

            InventoryBalance balance =
                    destinationBalances.get(productId);

            int quantity = productQuantities.get(productId);

            Product product = findTransferProduct(
                    transfer.getItems(), productId);

            balance.setOnHandQty(
                    getQty(balance.getOnHandQty()) + quantity);

            inventoryBalanceRepository.save(balance);

            InventoryMovement movement =
                    InventoryMovement.builder()
                            .movementType(
                                    InventoryMovement.MovementType.TRANSFER_IN)
                            .product(product)
                            .warehouse(
                                    transfer.getDestinationWarehouse())
                            .onHandChange(quantity)
                            .reservedChange(0)
                            .referenceDocument(
                                    transfer.getTransferNumber())
                            .reason(
                                    "Received from warehouse "
                                    + transfer.getSourceWarehouse().getCode())
                            .user(user)
                            .timestamp(LocalDateTime.now())
                            .build();

            movementRepository.save(movement);
        }

        // Update transfer status
        transfer.setStatus(
                StockTransfer.TransferStatus.RECEIVED);

        transfer.setReceivedAt(LocalDateTime.now());

        stockTransferRepository.save(transfer);

        return mapToResponse(
                loadStockTransfer(transferId));
    }

    // 4. Load complete Stock Transfer from Catalyst
    private StockTransfer loadStockTransfer(Long transferId) {

        StockTransfer transfer =
                stockTransferRepository.findById(transferId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Stock Transfer not found: "
                                        + transferId));

        if (transfer.getSourceWarehouse() == null
                || transfer.getDestinationWarehouse() == null) {

            throw new IllegalStateException(
                    "Stock Transfer warehouse references are missing");
        }

        // Load source warehouse details
        Long sourceId =
                transfer.getSourceWarehouse().getId();

        Warehouse source = warehouseRepository
                .findById(sourceId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Source warehouse not found: " + sourceId));

        transfer.setSourceWarehouse(source);

        // Load destination warehouse details
        Long destinationId =
                transfer.getDestinationWarehouse().getId();

        Warehouse destination = warehouseRepository
                .findById(destinationId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Destination warehouse not found: "
                                + destinationId));

        transfer.setDestinationWarehouse(destination);

        // Load transfer items separately
        

        return transfer;
    }

    // 5. Aggregate quantities by product ID
    private Map<Long, Integer> getProductQuantities(
            List<StockTransferItem> items) {

        if (items == null || items.isEmpty()) {

            throw new InvalidOperationException(
                    "Stock transfer does not contain any items");
        }

        Map<Long, Integer> quantities = new TreeMap<>();

        for (StockTransferItem item : items) {

            if (item.getProduct() == null
                    || item.getProduct().getId() == null
                    || item.getQuantity() == null
                    || item.getQuantity() <= 0) {

                throw new IllegalArgumentException(
                        "Invalid stock transfer item");
            }

            quantities.merge(
                    item.getProduct().getId(),
                    item.getQuantity(),
                    Math::addExact);
        }

        return quantities;
    }

    // 6. Create inventory balance map
    private Map<Long, InventoryBalance> createBalanceMap(
            List<InventoryBalance> balances) {

        Map<Long, InventoryBalance> balanceMap =
                new LinkedHashMap<>();

        for (InventoryBalance balance : balances) {

            if (balance.getProduct() == null
                    || balance.getProduct().getId() == null) {

                throw new IllegalStateException(
                        "Inventory balance has no Product reference");
            }

            Long productId = balance.getProduct().getId();

            if (balanceMap.putIfAbsent(productId, balance) != null) {

                throw new IllegalStateException(
                        "Duplicate inventory balances found for product: "
                        + productId);
            }
        }

        return balanceMap;
    }

    // 7. Find product details from transfer items
    private Product findTransferProduct(
            List<StockTransferItem> items, Long productId) {

        return items.stream()
                .filter(item ->
                        item.getProduct() != null
                        && productId.equals(item.getProduct().getId()))
                .map(StockTransferItem::getProduct)
                .findFirst()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found: " + productId));
    }

    // 8. Find user using Catalyst
    private User getUser(Long userId) {

        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found: " + userId));
    }

    private int getQty(Integer quantity) {
        return quantity == null ? 0 : quantity;
    }

    // 9. Convert Stock Transfer into response DTO
    public StockTransferResponse mapToResponse(
            StockTransfer transfer) {

        return StockTransferResponse.builder()
                .id(transfer.getId())
                .transferNumber(transfer.getTransferNumber())
                .sourceWarehouseId(
                        transfer.getSourceWarehouse().getId())
                .sourceWarehouseName(
                        transfer.getSourceWarehouse().getName())
                .destinationWarehouseId(
                        transfer.getDestinationWarehouse().getId())
                .destinationWarehouseName(
                        transfer.getDestinationWarehouse().getName())
                .status(transfer.getStatus().name())
                .items(transfer.getItems().stream()
                        .map(item ->
                                StockTransferItemResponse.builder()
                                        .id(item.getId())
                                        .productId(
                                                item.getProduct().getId())
                                        .productSku(
                                                item.getProduct().getSku())
                                        .productName(
                                                item.getProduct().getName())
                                        .quantity(item.getQuantity())
                                        .build())
                        .toList())
                .dispatchedAt(transfer.getDispatchedAt())
                .receivedAt(transfer.getReceivedAt())
                .createdAt(transfer.getCreatedAt())
                .build();
    }

    public Page<StockTransferResponse> getAllTransfers(
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
    if (pageable == null) {
        throw new IllegalArgumentException(
                "Pageable cannot be null");
    }

    return stockTransferRepository.findAll(page,size)
            .map(transfer ->
                    mapToResponse(
                            loadStockTransfer(transfer.getId())));
}
public StockTransferResponse getTransferById(Long transferId) {

    return mapToResponse(
            loadStockTransfer(transferId));
}

}
