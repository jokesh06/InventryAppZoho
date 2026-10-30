
package com.example.demo.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.example.demo.entity.Warehouse;
import com.example.demo.entity.InventoryBalance;
import com.example.demo.entity.Product;
import org.springframework.stereotype.Repository;


import com.zc.component.object.ZCRowObject;
import com.zc.component.zcql.ZCQL;

@Repository
public class InventoryBalanceRepository {

    // 1. Find balance using warehouse ID and product ID
    public Optional<InventoryBalance> findByWarehouseIdAndProductId(
            Long warehouseId, Long productId) {

        String query = "SELECT * FROM inventory_balances "
                + "WHERE warehouse_id = " + warehouseId
                + " AND product_id = " + productId;

        return executeSingleBalanceQuery(query, false);
    }

    // 2. Compatibility method.
    // This does not provide actual pessimistic locking.
    public Optional<InventoryBalance> findByWarehouseIdAndProductIdWithPessimisticLock(
            Long warehouseId, Long productId) {

        return findByWarehouseIdAndProductId(warehouseId, productId);
    }

    // 3. Compatibility method.
    // This does not provide actual pessimistic locking.
    public List<InventoryBalance> findByWarehouseIdAndProductIdsOrderedWithPessimisticLock(
            Long warehouseId, List<Long> productIds) {

        if (productIds == null || productIds.isEmpty()) {
            return new ArrayList<>();
        }

        String ids = productIds.stream()
                .distinct()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        String query = "SELECT * FROM inventory_balances "
                + "WHERE warehouse_id = " + warehouseId
                + " AND product_id IN (" + ids + ")"
                + " ORDER BY product_id ASC";

        return executeBalanceListQuery(query, false);
    }

    // 4. Find all balances for a warehouse with product and warehouse details
    public List<InventoryBalance> findByWarehouseIdWithProductAndWarehouse(
            Long warehouseId) {

        String query = "SELECT * FROM inventory_balances "
                + "WHERE warehouse_id = " + warehouseId;

        return executeBalanceListQuery(query, true);
    }

    private Optional<InventoryBalance> executeSingleBalanceQuery(
            String query, boolean loadRelations) {

        List<InventoryBalance> results =
                executeBalanceListQuery(query, loadRelations);

        return results.stream().findFirst();
    }

    private List<InventoryBalance> executeBalanceListQuery(
            String query, boolean loadRelations) {

        try {

            ArrayList<ZCRowObject> rows =
                    ZCQL.getInstance().executeQuery(query);

            List<InventoryBalance> balances = new ArrayList<>();

            if (rows == null) {
                return balances;
            }

            for (ZCRowObject row : rows) {

                Long warehouseId = toLong(row.get("warehouse_id"));
                Long productId = toLong(row.get("product_id"));

                Warehouse warehouse = Warehouse.builder()
                        .id(warehouseId)
                        .build();

                Product product = Product.builder()
                        .id(productId)
                        .build();

                if (loadRelations) {
                    warehouse = findWarehouseByRowId(warehouseId);
                    product = findProductByRowId(productId);
                }

                InventoryBalance balance = InventoryBalance.builder()
                        .id(toLong(row.get("ROWID")))
                        .warehouse(warehouse)
                        .product(product)
                        .onHandQty(toInteger(row.get("on_hand_qty")))
                        .reservedQty(toInteger(row.get("reserved_qty")))
                        .version(toLong(row.get("version")))
                        .updatedAt(toDateTime(row.get("updated_at")))
                        .build();

                balances.add(balance);
            }

            return balances;

        } catch (Exception e) {
            throw new RuntimeException(
                    "Error fetching inventory balances from Catalyst", e);
        }
    }

    // Get warehouse using Catalyst ROWID
    private Warehouse findWarehouseByRowId(Long warehouseId) {

        String query = "SELECT * FROM warehouses WHERE ROWID = " + warehouseId;

        try {

            ArrayList<ZCRowObject> rows =
                    ZCQL.getInstance().executeQuery(query);

            if (rows == null || rows.isEmpty()) {
                throw new RuntimeException(
                        "Warehouse not found: " + warehouseId);
            }

            ZCRowObject row = rows.get(0);

            return Warehouse.builder()
                    .id(toLong(row.get("ROWID")))
                    .code((String) row.get("code"))
                    .name((String) row.get("name"))
                    .address((String) row.get("address"))
                    .city((String) row.get("city"))
                    .state((String) row.get("state"))
                    .country((String) row.get("country"))
                    .status(Warehouse.Status.valueOf(
                            row.get("status").toString()))
                    .build();

        } catch (Exception e) {
            throw new RuntimeException(
                    "Error fetching warehouse from Catalyst", e);
        }
    }

    // Get product using Catalyst ROWID
    private Product findProductByRowId(Long productId) {

        String query = "SELECT * FROM products WHERE ROWID = " + productId;

        try {

            ArrayList<ZCRowObject> rows =
                    ZCQL.getInstance().executeQuery(query);

            if (rows == null || rows.isEmpty()) {
                throw new RuntimeException(
                        "Product not found: " + productId);
            }

            ZCRowObject row = rows.get(0);

            return Product.builder()
                    .id(toLong(row.get("ROWID")))
                    .sku((String) row.get("sku"))
                    .name((String) row.get("name"))
                    .description((String) row.get("description"))
                    .unitSellingPrice(toBigDecimal(
                            row.get("unit_selling_price")))
                    .reorderLevel(toInteger(row.get("reorder_level")))
                    .isActive(toBoolean(row.get("is_active")))
                    .build();

        } catch (Exception e) {
            throw new RuntimeException(
                    "Error fetching product from Catalyst", e);
        }
    }

    // Conversion methods
    private Long toLong(Object value) {
        return value == null ? null : Long.valueOf(value.toString());
    }

    private Integer toInteger(Object value) {
        return value == null ? null : Integer.valueOf(value.toString());
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

    private LocalDateTime toDateTime(Object value) {

        if (value == null) {
            return null;
        }

        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }

        return LocalDateTime.parse(
                value.toString().replace(" ", "T"));
    }
    
public InventoryBalance save(InventoryBalance balance) {

    if (balance == null) {
        throw new IllegalArgumentException(
                "Inventory balance cannot be null");
    }

    if (balance.getWarehouse() == null
            || balance.getWarehouse().getId() == null
            || balance.getProduct() == null
            || balance.getProduct().getId() == null) {

        throw new IllegalArgumentException(
                "Warehouse and Product IDs are required");
    }

    Long warehouseId = balance.getWarehouse().getId();
    Long productId = balance.getProduct().getId();

    if (warehouseId <= 0 || productId <= 0) {
        throw new IllegalArgumentException(
                "Warehouse and Product IDs must be positive");
    }

    int onHandQty = balance.getOnHandQty() == null
            ? 0 : balance.getOnHandQty();

    int reservedQty = balance.getReservedQty() == null
            ? 0 : balance.getReservedQty();

    if (onHandQty < 0 || reservedQty < 0) {
        throw new IllegalArgumentException(
                "Inventory quantities cannot be negative");
    }

    LocalDateTime now = LocalDateTime.now();

    String updatedAt = now.format(
            java.time.format.DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd HH:mm:ss:SSS"));

    try {

        // INSERT: New inventory balance
        if (balance.getId() == null) {

            long initialVersion = balance.getVersion() == null
                    ? 0L : balance.getVersion();

            String query =
                    "INSERT INTO inventory_balances "
                    + "(warehouse_id, product_id, on_hand_qty, "
                    + "reserved_qty, version, updated_at) "
                    + "VALUES ("
                    + warehouseId + ", "
                    + productId + ", "
                    + onHandQty + ", "
                    + reservedQty + ", "
                    + initialVersion + ", "
                    + "'" + updatedAt + "')";

            ArrayList<ZCRowObject> insertedRows =
                    ZCQL.getInstance().executeQuery(query);

            // Read generated Catalyst ROWID
            Long generatedId = null;

            if (insertedRows != null && !insertedRows.isEmpty()) {
                Object rowId = insertedRows.get(0).get("ROWID");

                if (rowId != null) {
                    generatedId = Long.valueOf(rowId.toString());
                }
            }

            // Fallback: retrieve the newly inserted balance
            if (generatedId == null) {

                String selectQuery =
                        "SELECT * FROM inventory_balances "
                        + "WHERE warehouse_id = " + warehouseId
                        + " AND product_id = " + productId
                        + " ORDER BY ROWID DESC LIMIT 0,1";

                ArrayList<ZCRowObject> rows =
                        ZCQL.getInstance().executeQuery(selectQuery);

                if (rows != null && !rows.isEmpty()) {
                    Object rowId = rows.get(0).get("ROWID");

                    if (rowId != null) {
                        generatedId =
                                Long.valueOf(rowId.toString());
                    }
                }
            }

            if (generatedId == null) {
                throw new IllegalStateException(
                        "Inventory balance was inserted, but its ROWID "
                        + "could not be retrieved. Check the Catalyst "
                        + "INSERT response.");
            }

            balance.setId(generatedId);
            balance.setVersion(initialVersion);
            balance.setUpdatedAt(now);

            return balance;
        }

        // UPDATE: Existing inventory balance
        Long currentVersion = balance.getVersion();

        long newVersion = currentVersion == null
                ? 1L : currentVersion + 1L;

        String versionCondition = currentVersion == null
                ? "version IS NULL"
                : "version = " + currentVersion;

        String updateQuery =
                "UPDATE inventory_balances SET "
                + "on_hand_qty = " + onHandQty + ", "
                + "reserved_qty = " + reservedQty + ", "
                + "version = " + newVersion + ", "
                + "updated_at = '" + updatedAt + "' "
                + "WHERE ROWID = " + balance.getId()
                + " AND " + versionCondition;

        ArrayList<ZCRowObject> updatedRows =
                ZCQL.getInstance().executeQuery(updateQuery);

        if (updatedRows == null || updatedRows.isEmpty()) {
            throw new java.util.ConcurrentModificationException(
                    "Inventory balance was modified by another request "
                    + "or the record was not found. ROWID: "
                    + balance.getId());
        }

        balance.setVersion(newVersion);
        balance.setUpdatedAt(now);

        return balance;

    } catch (java.util.ConcurrentModificationException e) {
        throw e;

    } catch (Exception e) {
        throw new RuntimeException(
                "Error saving inventory balance in Catalyst", e);
    }
}

}
