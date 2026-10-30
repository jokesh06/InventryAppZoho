
package com.example.demo.service;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.demo.dto.CreateShipmentRequest;
import com.example.demo.dto.ShipmentResponse;
import com.example.demo.dto.UpdateShipmentStatusRequest;

import com.example.demo.entity.OrderStatusHistory;
import com.example.demo.entity.SalesOrder;
import com.example.demo.entity.SalesOrderItem;
import com.example.demo.entity.Shipment;
import com.example.demo.entity.User;
import com.example.demo.entity.Warehouse;
import com.example.demo.entity.Product;

import com.example.demo.repository.OrderStatusHistoryRepository;
import com.example.demo.repository.SalesOrderRepository;
import com.example.demo.repository.ShipmentRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.WarehouseRepository;
import com.example.demo.repository.ProductRepository;

import exception.InvalidOperationException;
import exception.ResourceNotFoundException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final InventoryService inventoryService;

    // 1. Create and process shipment
    public ShipmentResponse createAndProcessShipment(
            CreateShipmentRequest request, Long userId) {

        if (request == null
                || request.getCarrier() == null
                || request.getCarrier().trim().isEmpty()
                || request.getTrackingNumber() == null
                || request.getTrackingNumber().trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Carrier and tracking number are required");
        }

        // Load complete Sales Order including its items
        SalesOrder order = loadSalesOrder(request.getSalesOrderId());

        // Only CONFIRMED orders can be shipped
        if (order.getStatus() != SalesOrder.OrderStatus.CONFIRMED) {

            throw new InvalidOperationException(
                    "Order cannot be shipped. Current status is "
                    + order.getStatus()
                    + ", but must be CONFIRMED.");
        }

        // Prevent duplicate shipment processing
        if (shipmentRepository.existsBySalesOrderId(order.getId())) {

            throw new InvalidOperationException(
                    "A shipment record already exists for order "
                    + order.getOrderNumber());
        }

        User user = getUser(userId);

        String shipmentNumber =
                "SHP-" + UUID.randomUUID()
                        .toString().substring(0, 8).toUpperCase();

        // Prepare product quantities.
        // LinkedHashMap also handles duplicate products correctly.
        Map<Long, Integer> productQuantities =
                getProductQuantities(order.getItems());

        // Deduct on-hand stock and release reserved stock
        inventoryService.fulfillShipment(
                order.getWarehouse().getId(),
                productQuantities,
                shipmentNumber,
                userId);

        // Create Shipment
        Shipment shipment = Shipment.builder()
                .shipmentNumber(shipmentNumber)
                .salesOrder(order)
                .carrier(request.getCarrier().trim())
                .trackingNumber(request.getTrackingNumber().trim())
                .status(Shipment.ShipmentStatus.PACKED)
                .shippedAt(LocalDateTime.now())
                .createdBy(user)
                .build();

        Shipment savedShipment =
                shipmentRepository.save(shipment);

        // Update Sales Order status
        order.setStatus(SalesOrder.OrderStatus.SHIPPED);

        salesOrderRepository.save(order);

        // Record order status history
        recordStatusChange(
                order,
                SalesOrder.OrderStatus.CONFIRMED.name(),
                SalesOrder.OrderStatus.SHIPPED.name(),
                user,
                "Fulfillment dispatched via "
                        + request.getCarrier()
                        + " with tracking #"
                        + request.getTrackingNumber());

        // Reload full shipment details
        return mapToResponse(
                loadShipment(savedShipment.getId()));
    }

    // 2. Update shipment status
    public ShipmentResponse updateShipmentStatus(
            Long shipmentId,
            UpdateShipmentStatusRequest request,
            Long userId) {

        if (request == null || request.getStatus() == null
                || request.getStatus().trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Shipment status is required");
        }

        // Load shipment with complete Sales Order and User
        Shipment shipment = loadShipment(shipmentId);

        Shipment.ShipmentStatus newStatus;

        try {

            newStatus = Shipment.ShipmentStatus.valueOf(
                    request.getStatus().trim().toUpperCase());

        } catch (IllegalArgumentException e) {

            throw new InvalidOperationException(
                    "Invalid shipment status: " + request.getStatus());
        }

        User user = getUser(userId);

        SalesOrder order = shipment.getSalesOrder();

        Shipment.ShipmentStatus previousShipmentStatus =
                shipment.getStatus();

        // Update shipment status
        shipment.setStatus(newStatus);

        if (newStatus == Shipment.ShipmentStatus.DELIVERED) {

            if (order.getStatus() != SalesOrder.OrderStatus.SHIPPED
                    && order.getStatus()
                            != SalesOrder.OrderStatus.DELIVERED) {

                throw new InvalidOperationException(
                        "Only shipped orders can be marked as delivered");
            }

            shipment.setDeliveredAt(LocalDateTime.now());

            // Change Sales Order status only once
            if (order.getStatus() == SalesOrder.OrderStatus.SHIPPED) {

                String previousOrderStatus = order.getStatus().name();

                order.setStatus(SalesOrder.OrderStatus.DELIVERED);

                salesOrderRepository.save(order);

                recordStatusChange(
                        order,
                        previousOrderStatus,
                        SalesOrder.OrderStatus.DELIVERED.name(),
                        user,
                        "Carrier marked shipment as delivered");
            }
        }

        Shipment savedShipment =
                shipmentRepository.save(shipment);

        return mapToResponse(
                loadShipment(savedShipment.getId()));
    }

    // 3. Get shipment using Sales Order ID
    public ShipmentResponse getShipmentByOrderId(Long salesOrderId) {

        Shipment shipment = shipmentRepository
                .findBySalesOrderId(salesOrderId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "No shipment record found for Sales Order ID: "
                                + salesOrderId));

        return mapToResponse(
                loadShipment(shipment.getId()));
    }

    // 4. Load complete Sales Order with Warehouse and Items
    private SalesOrder loadSalesOrder(Long orderId) {

        SalesOrder order = salesOrderRepository.findById(orderId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Sales Order not found: " + orderId));

        // Load full Warehouse details
        if (order.getWarehouse() != null
                && order.getWarehouse().getId() != null) {

            Long warehouseId = order.getWarehouse().getId();

            Warehouse warehouse =
                    warehouseRepository.findById(warehouseId)
                            .orElseThrow(() ->
                                    new ResourceNotFoundException(
                                            "Warehouse not found: "
                                            + warehouseId));

            order.setWarehouse(warehouse);
        }

        // Load Sales Order Items separately
        return order;
    }

    // 5. Load shipment with complete related records
    private Shipment loadShipment(Long shipmentId) {

        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Shipment not found: " + shipmentId));

        Long salesOrderId = shipment.getSalesOrder().getId();

        SalesOrder order = loadSalesOrder(salesOrderId);

        shipment.setSalesOrder(order);

        // Load shipment creator
        if (shipment.getCreatedBy() != null
                && shipment.getCreatedBy().getId() != null) {

            Long createdById = shipment.getCreatedBy().getId();

            User createdBy = getUser(createdById);

            shipment.setCreatedBy(createdBy);
        }

        return shipment;
    }

    // 6. Prepare product quantity map
    private Map<Long, Integer> getProductQuantities(
            List<SalesOrderItem> items) {

        if (items == null || items.isEmpty()) {
            throw new InvalidOperationException(
                    "Sales Order does not contain any items");
        }

        Map<Long, Integer> productQuantities =
                new LinkedHashMap<>();

        for (SalesOrderItem item : items) {

            if (item.getProduct() == null
                    || item.getProduct().getId() == null
                    || item.getQuantity() == null
                    || item.getQuantity() <= 0) {

                throw new IllegalStateException(
                        "Invalid Sales Order item details");
            }

            productQuantities.merge(
                    item.getProduct().getId(),
                    item.getQuantity(),
                    Math::addExact);
        }

        return productQuantities;
    }

    // 7. Find User
    private User getUser(Long userId) {

        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found: " + userId));
    }

    // 8. Record order status history
    private void recordStatusChange(
            SalesOrder order,
            String fromStatus,
            String toStatus,
            User user,
            String reason) {

        OrderStatusHistory history =
                OrderStatusHistory.builder()
                        .salesOrder(order)
                        .fromStatus(fromStatus)
                        .toStatus(toStatus)
                        .changedBy(user)
                        .changeReason(reason)
                        .build();

        statusHistoryRepository.save(history);
    }

    // 9. Convert Shipment into API response
    public ShipmentResponse mapToResponse(Shipment shipment) {

        SalesOrder order = shipment.getSalesOrder();

        return ShipmentResponse.builder()
                .id(shipment.getId())
                .shipmentNumber(shipment.getShipmentNumber())
                .salesOrderId(order.getId())
                .salesOrderNumber(order.getOrderNumber())
                .customerName(order.getCustomerName())
                .carrier(shipment.getCarrier())
                .trackingNumber(shipment.getTrackingNumber())
                .status(shipment.getStatus().name())
                .shippedAt(shipment.getShippedAt())
                .deliveredAt(shipment.getDeliveredAt())
                .createdByUsername(
                        shipment.getCreatedBy() != null
                                ? shipment.getCreatedBy().getUsername()
                                : "SYSTEM")
                .build();
    }
    public Page<ShipmentResponse> getAllShipments(@RequestParam(defaultValue = "0") int page,
                  @RequestParam(defaultValue = "20") int size
) {

    return shipmentRepository.findAll(page,size)
            .map(shipment ->
                    mapToResponse(loadShipment(shipment.getId())));
}

// Get shipment by Catalyst ROWID
public ShipmentResponse getShipmentById(Long shipmentId) {

    return mapToResponse(loadShipment(shipmentId));
}



}
