package com.example.demo.controller;

import java.util.List;

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

import com.example.demo.dto.CancelOrderRequest;
import com.example.demo.dto.CreateSalesOrderRequest;
import com.example.demo.dto.SalesOrderItemResponse;
import com.example.demo.dto.SalesOrderResponse;
import com.example.demo.dto.ShipOrderRequest;
import com.example.demo.entity.InventoryMovement;
import com.example.demo.entity.Product;
import com.example.demo.entity.SalesOrder;
import com.example.demo.entity.SalesOrderItem;
import com.example.demo.entity.User;
import com.example.demo.repository.SalesOrderRepository;
import com.example.demo.service.SalesOrderService;

import exception.ResourceNotFoundException;

@RestController
@RequestMapping("/api/sales-orders")
@RequiredArgsConstructor
public class SalesOrderController {

    private final SalesOrderService salesOrderService;
    private final SalesOrderRepository salesOrderRepository;

    @GetMapping
    public ResponseEntity<Page<SalesOrderResponse>> getOrders(
            @RequestParam(required = false) Long warehouseId,
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

        Page<SalesOrder> orders;
        if (warehouseId != null) {
            orders = salesOrderRepository.findByWarehouseId(warehouseId, page,size);
        } else if (status != null && !status.isBlank()) {
            orders = salesOrderRepository.findByStatus(SalesOrder.OrderStatus.valueOf(status.toUpperCase()), page,size);
        } else {
            orders = salesOrderRepository.findAll(page,size);
        }

        return ResponseEntity.ok(orders.map(this::mapToDetailedResponse));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SalesOrderResponse> getOrderById(@PathVariable Long id) {
        SalesOrder order = salesOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sales Order not found with id: " + id));
        return ResponseEntity.ok(mapToDetailedResponse(order));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER')")
    public ResponseEntity<SalesOrderResponse> createOrder(
            @Valid @RequestBody CreateSalesOrderRequest request,
            @AuthenticationPrincipal User user) {

        SalesOrderResponse created = salesOrderService.createOrder(request, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER')")
    public ResponseEntity<SalesOrderResponse> confirmOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {

        SalesOrderResponse confirmed = salesOrderService.confirmOrder(id, user.getId());
        return ResponseEntity.ok(confirmed);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER')")
    public ResponseEntity<SalesOrderResponse> cancelOrder(
            @PathVariable Long id,
            @Valid @RequestBody CancelOrderRequest request,
            @AuthenticationPrincipal User user) {

        SalesOrderResponse cancelled = salesOrderService.cancelOrder(id, request.getReason(), user.getId());
        return ResponseEntity.ok(cancelled);
    }

    private SalesOrderResponse mapToDetailedResponse(SalesOrder order) {
        Long warehouseId = order.getWarehouse() == null
                ? null
                : order.getWarehouse().getId();
        String warehouseName = order.getWarehouse() == null
                ? null
                : order.getWarehouse().getName();
        List<SalesOrderItem> items = order.getItems() == null
                ? List.of()
                : order.getItems();

        return SalesOrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .customerName(order.getCustomerName())
                .customerEmail(order.getCustomerEmail())
                .warehouseId(warehouseId)
                .warehouseName(warehouseName)
                .status(order.getStatus() == null
                        ? SalesOrder.OrderStatus.DRAFT.name()
                        : order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .items(items.stream().map(item -> {
                    Product product = item.getProduct();
                    return SalesOrderItemResponse.builder()
                        .id(item.getId())
                        .productId(product == null ? null : product.getId())
                        .productSku(product == null ? null : product.getSku())
                        .productName(product == null ? null : product.getName())
                        .quantity(item.getQuantity())
                        .unitPriceSnapshot(item.getUnitPriceSnapshot())
                        .subtotal(item.getSubtotal())
                        .build();
                }).toList())
                .createdAt(order.getCreatedAt())
                .build();
    }
    @PostMapping("/{id}/ship")
    public ResponseEntity<SalesOrderResponse> shipOrder(
            @PathVariable Long id,
            @Valid @RequestBody ShipOrderRequest request) {
        return ResponseEntity.ok(salesOrderService.shipOrder(id, request));
    }
   
}