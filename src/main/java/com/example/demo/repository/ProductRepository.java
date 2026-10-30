
package com.example.demo.repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.example.demo.entity.Category;
import com.example.demo.entity.Product;

import com.zc.component.object.ZCRowObject;
import com.zc.component.zcql.ZCQL;

@Repository
public class ProductRepository {

    private static final String TABLE_NAME = "products";
    private static final int BATCH_SIZE = 300;

    // Find product using SKU
    public Optional<Product> findBySku(String sku) {

        if (sku == null || sku.trim().isEmpty()) {
            return Optional.empty();
        }

        String safeSku = escapeString(sku.trim());

        String query = "SELECT * FROM " + TABLE_NAME
                + " WHERE sku = '" + safeSku + "' LIMIT 1";

        try {

            ArrayList<ZCRowObject> rows =
                    ZCQL.getInstance().executeQuery(query);

            if (rows == null || rows.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(mapRow(rows.get(0)));

        } catch (Exception e) {
            throw new RuntimeException(
                    "Error finding product by SKU from Catalyst", e);
        }
    }

    // Check whether SKU already exists
    public Boolean existsBySku(String sku) {

        return findBySku(sku).isPresent();
    }

    // Search active products using keyword and pagination
    public Page<Product> searchActiveProducts(
        String keyword,
        int page,
        int size) {

    Pageable pageable = PageRequest.of(
            Math.max(page, 0),
            Math.min(Math.max(size, 1), 100)
    );

    try {

        List<Product> activeProducts = fetchAllActiveProducts();

        List<Product> filteredProducts = new ArrayList<>();

        String searchKeyword = keyword == null
                ? ""
                : keyword.trim().toLowerCase(Locale.ROOT);

        for (Product product : activeProducts) {

            if (searchKeyword.isEmpty()) {
                filteredProducts.add(product);
                continue;
            }

            String name = product.getName() == null
                    ? ""
                    : product.getName().toLowerCase(Locale.ROOT);

            String sku = product.getSku() == null
                    ? ""
                    : product.getSku().toLowerCase(Locale.ROOT);

            if (name.contains(searchKeyword)
                    || sku.contains(searchKeyword)) {
                filteredProducts.add(product);
            }
        }

        int start = (int) Math.min(
                pageable.getOffset(),
                filteredProducts.size()
        );

        int end = (int) Math.min(
                (long) start + pageable.getPageSize(),
                filteredProducts.size()
        );

        List<Product> pageContent = new ArrayList<>(
                filteredProducts.subList(start, end)
        );

        return new PageImpl<>(
                pageContent,
                pageable,
                filteredProducts.size()
        );

    } catch (Exception e) {
        throw new RuntimeException(
                "Error searching active products in Catalyst", e);
    }
}
    // Count all active products
    public long countActiveProducts() {

        String query = "SELECT COUNT(ROWID) AS total_count FROM "
                + TABLE_NAME
                + " WHERE is_active = 1";

        try {

            ArrayList<ZCRowObject> rows =
                    ZCQL.getInstance().executeQuery(query);

            if (rows == null || rows.isEmpty()) {
                return 0L;
            }

            Object countValue = rows.get(0).get("total_count");

            if (countValue == null) {
                countValue = rows.get(0).get("COUNT(ROWID)");
            }

            return countValue == null
                    ? 0L
                    : Long.parseLong(countValue.toString());

        } catch (Exception e) {
            throw new RuntimeException(
                    "Error counting active products in Catalyst", e);
        }
    }

    // Fetch all active products in batches of 300
    private List<Product> fetchAllActiveProducts()
            throws Exception {

        List<Product> products = new ArrayList<>();

        int offset = 0;

        while (true) {

            String query = "SELECT * FROM " + TABLE_NAME
                    + " WHERE is_active = 1"
                    + " ORDER BY ROWID ASC"
                    + " LIMIT " + offset + "," + BATCH_SIZE;

            ArrayList<ZCRowObject> rows =
                    ZCQL.getInstance().executeQuery(query);

            if (rows == null || rows.isEmpty()) {
                break;
            }

            for (ZCRowObject row : rows) {
                products.add(mapRow(row));
            }

            if (rows.size() < BATCH_SIZE) {
                break;
            }

            offset += BATCH_SIZE;
        }

        return products;
    }

    // Convert Catalyst row into Product object
    private Product mapRow(ZCRowObject row) {

        Long categoryId = toLong(row.get("category_id"));

        Category category = null;

        if (categoryId != null) {
            category = new Category();
            category.setId(categoryId);
        }

        return Product.builder()
                .id(toLong(row.get("ROWID")))
                .sku(toStringValue(row.get("sku")))
                .name(toStringValue(row.get("name")))
                .description(toStringValue(row.get("description")))
                .category(category)
                .unitSellingPrice(
                        toBigDecimal(row.get("unit_selling_price")))
                .reorderLevel(
                        toInteger(row.get("reorder_level")))
                .isActive(
                        toBoolean(row.get("is_active")))
                .build();
    }

    // Escape single quotes in string values
    private String escapeString(String value) {
        return value.replace("'", "''");
    }

    private Long toLong(Object value) {

        return value == null
                ? null
                : Long.valueOf(value.toString());
    }

    private Integer toInteger(Object value) {

        return value == null
                ? null
                : Integer.valueOf(value.toString());
    }

    private BigDecimal toBigDecimal(Object value) {

        return value == null
                ? null
                : new BigDecimal(value.toString());
    }

    private Boolean toBoolean(Object value) {

        if (value == null) {
            return false;
        }

        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        return "1".equals(value.toString())
                || "true".equalsIgnoreCase(value.toString());
    }

    private String toStringValue(Object value) {

        return value == null
                ? null
                : value.toString();
    }
    public Optional<Product> findById(Long id) {

    if (id == null || id <= 0) {
        throw new IllegalArgumentException("Invalid Product ID");
    }

    String query = "SELECT * FROM products "
            + "WHERE ROWID = " + id
            + " LIMIT 0,1";

    try {
        ArrayList<ZCRowObject> rows =
                ZCQL.getInstance().executeQuery(query);

        if (rows == null || rows.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(mapRow(rows.get(0)));

    } catch (Exception e) {
        throw new RuntimeException(
                "Error finding product by ID: " + id, e);
    }
}

public Product save(Product product) {

    if (product == null
            || product.getSku() == null
            || product.getName() == null
            || product.getCategory() == null
            || product.getCategory().getId() == null
            || product.getReorderLevel() == null
            || product.getIsActive() == null) {

        throw new IllegalArgumentException(
                "Required product fields are missing");
    }

    Long categoryId = product.getCategory().getId();

    if (categoryId <= 0 || product.getReorderLevel() < 0) {
        throw new IllegalArgumentException(
                "Invalid category ID or reorder level");
    }

    String sku = escapeString(product.getSku());
    String name = escapeString(product.getName());

    String description = product.getDescription() == null
            ? "NULL"
            : "'" + escapeString(product.getDescription()) + "'";

    String price = product.getUnitSellingPrice() == null
            ? "NULL"
            : product.getUnitSellingPrice().toPlainString();

    int isActive = Boolean.TRUE.equals(product.getIsActive()) ? 1 : 0;

    try {

        if (product.getId() == null) {

            // Insert new product
            String query =
                    "INSERT INTO products "
                    + "(sku, name, description, category_id, "
                    + "unit_selling_price, reorder_level, is_active) "
                    + "VALUES ("
                    + "'" + sku + "', "
                    + "'" + name + "', "
                    + description + ", "
                    + categoryId + ", "
                    + price + ", "
                    + product.getReorderLevel() + ", "
                    + isActive + ")";

            ZCQL.getInstance().executeQuery(query);

            // Retrieve inserted product using unique SKU
            return findBySku(product.getSku())
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Product inserted but could not "
                                    + "be retrieved: " + product.getSku()));

        } else {

            // Update existing product
            String query =
                    "UPDATE products SET "
                    + "sku = '" + sku + "', "
                    + "name = '" + name + "', "
                    + "description = " + description + ", "
                    + "category_id = " + categoryId + ", "
                    + "unit_selling_price = " + price + ", "
                    + "reorder_level = " + product.getReorderLevel() + ", "
                    + "is_active = " + isActive + " "
                    + "WHERE ROWID = " + product.getId();

            ZCQL.getInstance().executeQuery(query);

            return findById(product.getId())
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Product update could not be verified: "
                                    + product.getId()));
        }

    } catch (Exception e) {
        throw new RuntimeException(
                "Error saving product in Catalyst", e);
    }
}

}
