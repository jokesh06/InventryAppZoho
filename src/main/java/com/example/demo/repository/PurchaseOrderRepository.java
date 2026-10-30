
package com.example.demo.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.data.core.TypedPropertyPath;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.demo.entity.InventoryMovement;
import com.example.demo.entity.PurchaseOrder;
import com.example.demo.entity.PurchaseOrder.POStatus;
import com.example.demo.entity.Supplier;
import com.example.demo.entity.Warehouse;
import com.example.demo.entity.User;
import com.example.demo.util.CatalystDateTimes;

import com.zc.component.object.ZCRowObject;
import com.zc.component.zcql.ZCQL;

@Repository
public class PurchaseOrderRepository {

    private static final String TABLE_NAME = "purchase_orders";

    private static final int BATCH_SIZE = 300;

    // 1. Find Purchase Order using PO Number
    public Optional<PurchaseOrder> findByPoNumber(String poNumber) {

        if (poNumber == null || poNumber.trim().isEmpty()) {
            return Optional.empty();
        }

        String safePoNumber = escapeString(poNumber.trim());

        String query = "SELECT * FROM " + TABLE_NAME
                + " WHERE po_number = '" + safePoNumber + "'"
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
                    "Error finding purchase order by PO number", e);
        }
    }

    // 2. Count pending purchase orders
    public long countPendingPurchaseOrders() {

        try {

            long draftCount = countByStatus(POStatus.DRAFT);

            long submittedCount = countByStatus(POStatus.SUBMITTED);

            long partiallyReceivedCount =
                    countByStatus(POStatus.PARTIALLY_RECEIVED);

            return draftCount
                    + submittedCount
                    + partiallyReceivedCount;

        } catch (Exception e) {
            throw new RuntimeException(
                    "Error counting pending purchase orders", e);
        }
    }

    // Count Purchase Orders for a particular status
    private long countByStatus(POStatus status) throws Exception {

        String query = "SELECT COUNT(ROWID) FROM " + TABLE_NAME
                + " WHERE status = '" + status.name() + "'";

        ArrayList<ZCRowObject> rows =
                ZCQL.getInstance().executeQuery(query);

        if (rows == null || rows.isEmpty()) {
            return 0L;
        }

        Object countValue = rows.get(0).get("COUNT(ROWID)");

        if (countValue == null) {
            throw new RuntimeException(
                    "Catalyst COUNT result not found for status: " + status);
        }

        return Long.parseLong(countValue.toString());
    }

    // 3. Find Purchase Orders by status with pagination
    public Page<PurchaseOrder> findByStatus(
            POStatus status, @RequestParam(defaultValue = "0") int page,
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

        if (status == null) {
            throw new IllegalArgumentException(
                    "Purchase Order status cannot be null");
        }

        if (pageable == null) {
            throw new IllegalArgumentException(
                    "Pageable cannot be null");
        }

        try {

            String baseQuery = "SELECT * FROM " + TABLE_NAME
                    + " WHERE status = '" + status.name() + "'";

            String orderBy = buildOrderBy(pageable.getSort());

            String query = baseQuery + orderBy;

            long totalCount = countByStatus(status);

            if (pageable.isUnpaged()) {

                List<PurchaseOrder> allOrders =
                        fetchPurchaseOrders(query, 0, totalCount);

                return new PageImpl<>(allOrders);
            }

            List<PurchaseOrder> pageContent =
                    fetchPurchaseOrders(
                            query,
                            pageable.getOffset(),
                            pageable.getPageSize());

            return new PageImpl<>(
                    pageContent,
                    pageable,
                    totalCount);

        } catch (Exception e) {
            throw new RuntimeException(
                    "Error fetching purchase orders by status from Catalyst",
                    e);
        }
    }

    // Fetch records in batches of 300
    private List<PurchaseOrder> fetchPurchaseOrders(
            String baseQuery,
            long offset,
            long requestedRecords) throws Exception {

        List<PurchaseOrder> orders = new ArrayList<>();

        long fetchedRecords = 0;

        while (fetchedRecords < requestedRecords) {

            int batchSize = (int) Math.min(
                    BATCH_SIZE,
                    requestedRecords - fetchedRecords);

            long currentOffset = offset + fetchedRecords;

            String query = baseQuery
                    + " LIMIT " + currentOffset + "," + batchSize;

            ArrayList<ZCRowObject> rows =
                    ZCQL.getInstance().executeQuery(query);

            if (rows == null || rows.isEmpty()) {
                break;
            }

            for (ZCRowObject row : rows) {
                orders.add(mapRow(row));
            }

            fetchedRecords += rows.size();

            if (rows.size() < batchSize) {
                break;
            }
        }

        return orders;
    }

    // Build a safe ORDER BY clause from Pageable sorting
    private String buildOrderBy(Sort sort) {

        StringBuilder orderBy = new StringBuilder();

        if (sort != null && sort.isSorted()) {

            for (Sort.Order order : sort) {

                String column = getSortColumn(order.getProperty());

                // Ignore unsupported sort properties
                if (column == null) {
                    continue;
                }

                if (orderBy.length() == 0) {
                    orderBy.append(" ORDER BY ");
                } else {
                    orderBy.append(", ");
                }

                orderBy.append(column)
                        .append(order.isAscending() ? " ASC" : " DESC");
            }
        }

        // Stable ordering when no supported sort property is supplied
        if (orderBy.length() == 0) {
            orderBy.append(" ORDER BY ROWID ASC");
        }

        return orderBy.toString();
    }

    private String getSortColumn(String property) {

        switch (property) {

            case "id":
                return "ROWID";

            case "poNumber":
                return "po_number";

            case "status":
                return "status";

            case "totalAmount":
                return "total_amount";

            case "createdAt":
                return "created_at";

            case "updatedAt":
                return "updated_at";

            default:
                return null;
        }
    }

    // Convert Catalyst row into PurchaseOrder object
    private PurchaseOrder mapRow(ZCRowObject row) {

        Long supplierId = toLong(row.get("supplier_id"));

        Long warehouseId =
                toLong(row.get("destination_warehouse_id"));

        Long userId =
                toLong(row.get("created_by_user_id"));

        Supplier supplier = null;

        if (supplierId != null) {
            supplier = new Supplier();
            supplier.setId(supplierId);
        }

        Warehouse warehouse = null;

        if (warehouseId != null) {
            warehouse = new Warehouse();
            warehouse.setId(warehouseId);
        }

        User createdBy = null;

        if (userId != null) {
            createdBy = new User();
            createdBy.setId(userId);
        }

        String statusValue = toStringValue(row.get("status"));

POStatus status = parsePOStatus(statusValue);

        return PurchaseOrder.builder()
                .id(toLong(row.get("ROWID")))
                .poNumber(toStringValue(row.get("po_number")))
                .supplier(supplier)
                .destinationWarehouse(warehouse)
                .status(status)
                .totalAmount(
                        toBigDecimal(row.get("total_amount")))
                .createdBy(createdBy)
                .createdAt(toDateTime(row.get("created_at")))
                .updatedAt(toDateTime(row.get("updated_at")))
                .build();
    }

    // Conversion methods

    private String escapeString(String value) {
        return value.replace("'", "''");
    }

    private Long toLong(Object value) {

        return value == null
                ? null
                : Long.valueOf(value.toString());
    }

    private String toStringValue(Object value) {

        return value == null
                ? null
                : value.toString();
    }

    private java.math.BigDecimal toBigDecimal(Object value) {

        return value == null
                ? java.math.BigDecimal.ZERO
                : new java.math.BigDecimal(value.toString());
    }

    private LocalDateTime toDateTime(Object value) {
        return CatalystDateTimes.parse(value);
    }
    
private static final java.time.format.DateTimeFormatter DATE_FORMAT =
        java.time.format.DateTimeFormatter.ofPattern(
                "yyyy-MM-dd HH:mm:ss:SSS");

// Find Purchase Order by Catalyst ROWID
public Optional<PurchaseOrder> findById(Long id) {

    if (id == null || id <= 0) {
        throw new IllegalArgumentException(
                "Invalid Purchase Order ID");
    }

    String query = "SELECT * FROM purchase_orders "
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
                "Error finding purchase order by ID: " + id, e);
    }
}

// Save new Purchase Order or update existing Purchase Order
public PurchaseOrder save(PurchaseOrder po) {

    if (po == null) {
        throw new IllegalArgumentException(
                "Purchase Order cannot be null");
    }

    if (po.getSupplier() == null
            || po.getSupplier().getId() == null
            || po.getDestinationWarehouse() == null
            || po.getDestinationWarehouse().getId() == null
            || po.getCreatedBy() == null
            || po.getCreatedBy().getId() == null
            || po.getPoNumber() == null
            || po.getStatus() == null) {

        throw new IllegalArgumentException(
                "Required Purchase Order fields are missing");
    }

    LocalDateTime now = LocalDateTime.now();

    if (po.getCreatedAt() == null) {
        po.setCreatedAt(now);
    }

    po.setUpdatedAt(now);

    String poNumber = escapeString(po.getPoNumber());

    String status = po.getStatus().name();

    String totalAmount = po.getTotalAmount() == null
            ? "0"
            : po.getTotalAmount().toPlainString();

    Long supplierId = po.getSupplier().getId();

    Long warehouseId =
            po.getDestinationWarehouse().getId();

    Long userId = po.getCreatedBy().getId();

    String updatedAt = DATE_FORMAT.format(po.getUpdatedAt());

    try {

        if (po.getId() == null) {

            // INSERT new Purchase Order
            String createdAt = DATE_FORMAT.format(po.getCreatedAt());

            String query =
                    "INSERT INTO purchase_orders "
                    + "(po_number, supplier_id, "
                    + "destination_warehouse_id, status, "
                    + "total_amount, created_by_user_id, "
                    + "created_at, updated_at) "
                    + "VALUES ("
                    + "'" + poNumber + "', "
                    + supplierId + ", "
                    + warehouseId + ", "
                    + "'" + status + "', "
                    + totalAmount + ", "
                    + userId + ", "
                    + "'" + createdAt + "', "
                    + "'" + updatedAt + "')";

            ZCQL.getInstance().executeQuery(query);

            // Retrieve inserted record using unique PO number
            return findByPoNumber(po.getPoNumber())
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Purchase Order inserted but could "
                                    + "not be retrieved: "
                                    + po.getPoNumber()));

        } else {

            // UPDATE existing Purchase Order
            String query =
                    "UPDATE purchase_orders SET "
                    + "po_number = '" + poNumber + "', "
                    + "supplier_id = " + supplierId + ", "
                    + "destination_warehouse_id = " + warehouseId + ", "
                    + "status = '" + status + "', "
                    + "total_amount = " + totalAmount + ", "
                    + "created_by_user_id = " + userId + ", "
                    + "updated_at = '" + updatedAt + "' "
                    + "WHERE ROWID = " + po.getId();

            ZCQL.getInstance().executeQuery(query);

            return findById(po.getId())
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Purchase Order update could not "
                                    + "be verified: " + po.getId()));
        }

    } catch (Exception e) {
        throw new RuntimeException(
                "Error saving Purchase Order in Catalyst", e);
    }
}

public Page<PurchaseOrder> findAll(@RequestParam(defaultValue = "0") int page,
                  @RequestParam(defaultValue = "20") int size) {
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

    try {

        String query = "SELECT * FROM purchase_orders"
                + buildOrderBy(pageable.getSort());

        long totalRecords = countAllPurchaseOrders();

        // Handle unpaged request
        if (pageable.isUnpaged()) {

            List<PurchaseOrder> allOrders =
                    fetchPurchaseOrders(query, 0, totalRecords);

            return new PageImpl<>(allOrders);
        }

        // Fetch requested page
        List<PurchaseOrder> orders =
                fetchPurchaseOrders(
                        query,
                        pageable.getOffset(),
                        pageable.getPageSize());

        return new PageImpl<>(
                orders,
                pageable,
                totalRecords);

    } catch (Exception e) {

        throw new RuntimeException(
                "Error fetching all purchase orders from Catalyst", e);
    }
}

private long countAllPurchaseOrders() throws Exception {

    long total = 0;
    int offset = 0;

    while (true) {

        String query = "SELECT ROWID FROM purchase_orders "
                + "LIMIT " + offset + ",300";

        ArrayList<ZCRowObject> rows =
                ZCQL.getInstance().executeQuery(query);

        if (rows == null || rows.isEmpty()) {
            break;
        }

        total += rows.size();

        if (rows.size() < 300) {
            break;
        }

        offset += rows.size();
    }

    return total;
}
private POStatus parsePOStatus(String statusValue) {

    if (statusValue == null || statusValue.isBlank()) {
        return POStatus.DRAFT;
    }

    try {
        return POStatus.valueOf(
                statusValue.trim().toUpperCase(Locale.ROOT)
        );
    } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(
                "Invalid purchase order status in Catalyst: "
                        + statusValue,
                e
        );
    }
}

}
