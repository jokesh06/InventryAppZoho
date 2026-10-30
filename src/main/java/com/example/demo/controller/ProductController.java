
package com.example.demo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.*;

import com.example.demo.dto.CreateProductRequest;
import com.example.demo.dto.ProductResponse;
import com.example.demo.dto.UpdateProductRequest;

import com.example.demo.entity.Category;
import com.example.demo.entity.Product;

import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.ProductRepository;

import exception.InvalidOperationException;
import exception.ResourceNotFoundException;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    // 1. Get all active products with search and pagination
    @GetMapping
public ResponseEntity<Page<ProductResponse>> getAllProducts(
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {

    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 100);

    Page<Product> productPage =
            productRepository.searchActiveProducts(
                    search,
                    safePage,
                    safeSize
            );

    return ResponseEntity.ok(
            productPage.map(this::mapToResponse)
    );
}

    // 2. Get product by Catalyst ROWID
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(
            @PathVariable Long id) {

        Product product = productRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found with ID: " + id));

        return ResponseEntity.ok(mapToResponse(product));
    }

    // 3. Create product
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_MANAGER')")
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request) {

        String sku = request.getSku().trim().toUpperCase();

        if (productRepository.existsBySku(sku)) {
            throw new InvalidOperationException(
                    "Product SKU already exists: " + sku);
        }

        Category category = categoryRepository.findById(
                request.getCategoryId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Category not found with ID: "
                                + request.getCategoryId()));

        Product product = Product.builder()
                .sku(sku)
                .name(request.getName())
                .description(request.getDescription())
                .category(category)
                .unitSellingPrice(request.getUnitSellingPrice())
                .reorderLevel(request.getReorderLevel())
                .isActive(true)
                .build();

        // Save using Catalyst ZCQL
        Product savedProduct = productRepository.save(product);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapToResponse(savedProduct));
    }

    // 4. Update product
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_MANAGER')")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request) {

        Product product = productRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found with ID: " + id));

        Category category = categoryRepository.findById(
                request.getCategoryId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Category not found with ID: "
                                + request.getCategoryId()));

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setCategory(category);
        product.setUnitSellingPrice(request.getUnitSellingPrice());
        product.setReorderLevel(request.getReorderLevel());
        product.setIsActive(request.getIsActive());

        Product updatedProduct = productRepository.save(product);

        return ResponseEntity.ok(mapToResponse(updatedProduct));
    }

    // 5. Soft delete: deactivate product
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateProduct(
            @PathVariable Long id) {

        Product product = productRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found with ID: " + id));

        if (Boolean.FALSE.equals(product.getIsActive())) {
            throw new InvalidOperationException(
                    "Product is already inactive");
        }

        // Soft delete instead of permanently deleting the record
        product.setIsActive(false);

        productRepository.save(product);

        return ResponseEntity.noContent().build();
    }

    // 6. Map Product to ProductResponse
    private ProductResponse mapToResponse(Product product) {

        if (product.getCategory() == null
                || product.getCategory().getId() == null) {

            throw new IllegalStateException(
                    "Category reference is missing for product: "
                    + product.getId());
        }

        // ProductRepository initially loads only category_id.
        // Load complete Category details separately.
        Long categoryId = product.getCategory().getId();

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Category not found with ID: " + categoryId));

        product.setCategory(category);

        return ProductResponse.builder()
                .id(product.getId())
                .sku(product.getSku())
                .name(product.getName())
                .description(product.getDescription())
                .categoryId(category.getId())
                .categoryName(category.getName())
                .unitSellingPrice(product.getUnitSellingPrice())
                .reorderLevel(product.getReorderLevel())
                .isActive(product.getIsActive())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
