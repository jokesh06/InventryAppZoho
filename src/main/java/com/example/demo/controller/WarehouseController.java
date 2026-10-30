
package com.example.demo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.*;

import com.example.demo.dto.CreateWarehouseRequest;
import com.example.demo.dto.UpdateWarehouseRequest;
import com.example.demo.dto.WarehouseResponse;

import com.example.demo.entity.Warehouse;

import com.example.demo.repository.WarehouseRepository;

import exception.InvalidOperationException;
import exception.ResourceNotFoundException;

import java.util.List;

@RestController
@RequestMapping("/api/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseRepository warehouseRepository;

    // 1. Get all warehouses
    @GetMapping
    public ResponseEntity<List<WarehouseResponse>> getAllWarehouses() {

        List<WarehouseResponse> warehouses =
                warehouseRepository.findAll()
                        .stream()
                        .map(this::mapToResponse)
                        .toList();

        return ResponseEntity.ok(warehouses);
    }

    // 2. Get warehouse by Catalyst ROWID
    @GetMapping("/{id}")
    public ResponseEntity<WarehouseResponse> getWarehouseById(
            @PathVariable Long id) {

        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Warehouse not found with ID: " + id));

        return ResponseEntity.ok(mapToResponse(warehouse));
    }

    // 3. Create warehouse
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WarehouseResponse> createWarehouse(
            @Valid @RequestBody CreateWarehouseRequest request) {

        String code = request.getCode().trim().toUpperCase();

        if (warehouseRepository.existsByCode(code)) {

            throw new InvalidOperationException(
                    "Warehouse code already exists: " + code);
        }

        Warehouse warehouse = Warehouse.builder()
                .code(code)
                .name(request.getName())
                .address(request.getAddress())
                .city(request.getCity())
                .state(request.getState())
                .country(request.getCountry())
                .status(Warehouse.Status.ACTIVE)
                .build();

        Warehouse savedWarehouse =
                warehouseRepository.save(warehouse);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapToResponse(savedWarehouse));
    }

    // 4. Update warehouse
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WarehouseResponse> updateWarehouse(
            @PathVariable Long id,
            @Valid @RequestBody UpdateWarehouseRequest request) {

        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Warehouse not found with ID: " + id));

        Warehouse.Status status;

        try {
            status = Warehouse.Status.valueOf(
                    request.getStatus().trim().toUpperCase());

        } catch (Exception e) {
            throw new InvalidOperationException(
                    "Invalid warehouse status: " + request.getStatus());
        }

        warehouse.setName(request.getName());
        warehouse.setAddress(request.getAddress());
        warehouse.setCity(request.getCity());
        warehouse.setState(request.getState());
        warehouse.setCountry(request.getCountry());
        warehouse.setStatus(status);

        Warehouse updatedWarehouse =
                warehouseRepository.save(warehouse);

        return ResponseEntity.ok(
                mapToResponse(updatedWarehouse));
    }

    // 5. Convert Warehouse into response DTO
    private WarehouseResponse mapToResponse(Warehouse warehouse) {

        return WarehouseResponse.builder()
                .id(warehouse.getId())
                .code(warehouse.getCode())
                .name(warehouse.getName())
                .address(warehouse.getAddress())
                .city(warehouse.getCity())
                .state(warehouse.getState())
                .country(warehouse.getCountry())
                .status(
                        warehouse.getStatus() == null
                                ? null
                                : warehouse.getStatus().name())
                .createdAt(warehouse.getCreatedAt())
                .updatedAt(warehouse.getUpdatedAt())
                .build();
    }
}
