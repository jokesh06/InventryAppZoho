
package com.example.demo.service;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.demo.dto.CreateSalesOrderRequest;
import com.example.demo.dto.SalesOrderResponse;
import com.example.demo.dto.ShipOrderRequest;

import com.example.demo.entity.OrderStatusHistory;
import com.example.demo.entity.Product;
import com.example.demo.entity.SalesOrder;
import com.example.demo.entity.SalesOrderItem;
import com.example.demo.entity.User;
import com.example.demo.entity.Warehouse;

import com.example.demo.repository.OrderStatusHistoryRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.SalesOrderRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.WarehouseRepository;

import exception.InvalidOperationException;
import exception.ResourceNotFoundException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SalesOrderService {

    private final SalesOrderRepository salesOrderRepository;
    
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;
    private final OrderStatusHistoryRepository statusHistoryRepository;

    // 1. Create Sales Order
    public SalesOrderResponse createOrder(
            CreateSalesOrderRequest request, Long userId) {

        if (request == null || request.getItems() == null
                || request.getItems().isEmpty()) {
            throw new IllegalArgumentException(
                    "Sales order must contain at least one item");
        }

        Warehouse warehouse = warehouseRepository
                .findById(request.getWarehouseId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Warehouse not found"));

        User user = getUser(userId);

        SalesOrder order = SalesOrder.builder()
                .orderNumber(
                        "SO-" + UUID.randomUUID()
                                .toString().substring(0, 8).toUpperCase())
                .customerName(request.getCustomerName())
                .customerEmail(request.getCustomerEmail())
                .warehouse(warehouse)
                .status(SalesOrder.OrderStatus.DRAFT)
                .createdBy(user)
                .totalAmount(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .build();

        BigDecimal total = BigDecimal.ZERO;

        List<SalesOrderItem> items = new ArrayList<>();

        // Prepare order items and calculate total
        for (var itemReq : request.getItems()) {

            if (itemReq.getQuantity() == null
                    || itemReq.getQuantity() <= 0) {

                throw new IllegalArgumentException(
                        "Product quantity must be greater than zero");
            }

            Product product = productRepository
                    .findById(itemReq.getProductId())
                    .orElseThrow(() ->
                            new ResourceNotFoundException(
                                    "Product not found: "
                                    + itemReq.getProductId()));

            if (product.getUnitSellingPrice() == null
                    || product.getUnitSellingPrice().signum() < 0) {

                throw new IllegalStateException(
                        "Invalid selling price for product: "
                        + product.getId());
            }

            BigDecimal subtotal =
                    product.getUnitSellingPrice()
                            .multiply(BigDecimal.valueOf(
                                    itemReq.getQuantity()));

            total = total.add(subtotal);

            SalesOrderItem item = SalesOrderItem.builder()
                    .product(product)
                    .quantity(itemReq.getQuantity())
                    .unitPriceSnapshot(product.getUnitSellingPrice())
                    .subtotal(subtotal)
                    .build();

            items.add(item);
        }

        order.setTotalAmount(total);

        // Save Sales Order header first
        SalesOrder savedOrder =
                salesOrderRepository.save(order);

        // Save each item separately because Catalyst
        // does not automatically perform JPA cascade operations.
       

        // Record initial status
        recordStatusChange(
                savedOrder,
                "NONE",
                "DRAFT",
                user,
                "Order created in draft");

        // Reload header and items from Catalyst
        return mapToResponse(
                loadSalesOrder(savedOrder.getId()));
    }

    // 2. Confirm Sales Order
    public SalesOrderResponse confirmOrder(
            Long orderId, Long userId) {

        SalesOrder order = loadSalesOrder(orderId);

        if (order.getStatus() != SalesOrder.OrderStatus.DRAFT) {

            throw new InvalidOperationException(
                    "Only DRAFT orders can be confirmed");
        }

        Map<Long, Integer> productQuantities =
                getProductQuantities(order);

        // Reserve inventory
        inventoryService.reserveStockForOrder(
                order.getWarehouse().getId(),
                productQuantities,
                order.getOrderNumber(),
                userId);

        order.setStatus(SalesOrder.OrderStatus.CONFIRMED);

        SalesOrder updatedOrder =
                salesOrderRepository.save(order);

        User user = getUser(userId);

        recordStatusChange(
                updatedOrder,
                "DRAFT",
                "CONFIRMED",
                user,
                "Order confirmed and stock reserved");

        return mapToResponse(
                loadSalesOrder(orderId));
    }

    // 3. Cancel Sales Order
    public SalesOrderResponse cancelOrder(
            Long orderId, String reason, Long userId) {

        SalesOrder order = loadSalesOrder(orderId);

        if (order.getStatus() == SalesOrder.OrderStatus.SHIPPED
                || order.getStatus() == SalesOrder.OrderStatus.DELIVERED) {

            throw new InvalidOperationException(
                    "Cannot cancel an order that has already shipped "
                    + "or delivered");
        }

        if (order.getStatus() == SalesOrder.OrderStatus.CANCELLED) {

            throw new InvalidOperationException(
                    "Order is already cancelled");
        }

        // Release previously reserved stock
        if (order.getStatus() == SalesOrder.OrderStatus.CONFIRMED) {

            Map<Long, Integer> productQuantities =
                    getProductQuantities(order);

            inventoryService.releaseReservationForOrder(
                    order.getWarehouse().getId(),
                    productQuantities,
                    order.getOrderNumber(),
                    userId);
        }

        String previousStatus = order.getStatus().name();

        order.setStatus(SalesOrder.OrderStatus.CANCELLED);

        SalesOrder updatedOrder =
                salesOrderRepository.save(order);

        User user = getUser(userId);

        recordStatusChange(
                updatedOrder,
                previousStatus,
                "CANCELLED",
                user,
                reason);

        return mapToResponse(
                loadSalesOrder(orderId));
    }

    // 4. Get all Sales Orders with pagination
    public Page<SalesOrderResponse> getAllOrders(@RequestParam(defaultValue = "0") int page,
                  @RequestParam(defaultValue = "20") int size
) {

        return salesOrderRepository.findAll(page,size)
                .map(order ->
                        mapToResponse(
                                loadSalesOrder(order.getId())));
    }

    // 5. Ship Sales Order
    public SalesOrderResponse shipOrder(
            Long orderId, ShipOrderRequest request) {

        if (request == null
                || request.getCarrier() == null
                || request.getTrackingNumber() == null) {

            throw new IllegalArgumentException(
                    "Carrier and tracking number are required");
        }

        SalesOrder order = loadSalesOrder(orderId);

        if (order.getStatus() != SalesOrder.OrderStatus.CONFIRMED) {

            throw new InvalidOperationException(
                    "Only CONFIRMED orders can be shipped. Current status: "
                    + order.getStatus());
        }

        order.setCarrier(request.getCarrier());
        order.setTrackingNumber(request.getTrackingNumber());
        order.setStatus(SalesOrder.OrderStatus.SHIPPED);

        SalesOrder savedOrder =
                salesOrderRepository.save(order);

        return mapToResponse(
                loadSalesOrder(savedOrder.getId()));
    }

    // Load complete Sales Order details from Catalyst
    private SalesOrder loadSalesOrder(Long orderId) {

        SalesOrder order = salesOrderRepository.findById(orderId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Sales Order not found: " + orderId));

        // Load complete Warehouse
        if (order.getWarehouse() != null
                && order.getWarehouse().getId() != null) {

            Long warehouseId = order.getWarehouse().getId();

            Warehouse warehouse = warehouseRepository
                    .findById(warehouseId)
                    .orElseThrow(() ->
                            new ResourceNotFoundException(
                                    "Warehouse not found: " + warehouseId));

            order.setWarehouse(warehouse);
        }

        // Load Sales Order items
       

        

        return order;
    }

    // Combine quantities when the same product occurs in multiple items
    private Map<Long, Integer> getProductQuantities(
            SalesOrder order) {

        Map<Long, Integer> quantities = new LinkedHashMap<>();

        for (SalesOrderItem item : order.getItems()) {

            Long productId = item.getProduct().getId();

            quantities.merge(
                    productId,
                    item.getQuantity(),
                    Math::addExact);
        }

        return quantities;
    }

    // Find user using Catalyst
    private User getUser(Long userId) {

        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found: " + userId));
    }

    // Record order status change
    private void recordStatusChange(
            SalesOrder order,
            String from,
            String to,
            User user,
            String reason) {

        OrderStatusHistory history =
                OrderStatusHistory.builder()
                        .salesOrder(order)
                        .fromStatus(from)
                        .toStatus(to)
                        .changedBy(user)
                        .changeReason(reason)
                        .build();

        statusHistoryRepository.save(history);
    }

    // Convert SalesOrder into response DTO
    private SalesOrderResponse mapToResponse(
            SalesOrder order) {

        return SalesOrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .customerName(order.getCustomerName())
                .customerEmail(order.getCustomerEmail())
                .warehouseId(order.getWarehouse().getId())
                .warehouseName(order.getWarehouse().getName())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
