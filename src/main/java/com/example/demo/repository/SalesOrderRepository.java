
package com.example.demo.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
import com.example.demo.entity.SalesOrder;
import com.example.demo.entity.SalesOrder.OrderStatus;
import com.example.demo.entity.Warehouse;
import com.example.demo.entity.User;
import com.example.demo.util.CatalystDateTimes;

import com.zc.component.object.ZCRowObject;
import com.zc.component.zcql.ZCQL;

@Repository
public class SalesOrderRepository {

    private static final String TABLE_NAME = "sales_orders";

    private static final int BATCH_SIZE = 300;

    // 1. Find Sales Order using Order Number
    public Optional<SalesOrder> findByOrderNumber(String orderNumber) {

        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            return Optional.empty();
        }

        String safeOrderNumber = escapeString(orderNumber.trim());

        String query = "SELECT * FROM " + TABLE_NAME
                + " WHERE order_number = '" + safeOrderNumber + "'"
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
                    "Error finding sales order by order number", e);
        }
    }

    // 2. Find Sales Orders by Warehouse ID with pagination
    public Page<SalesOrder> findByWarehouseId(
            Long warehouseId, @RequestParam(defaultValue = "0") int page,
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

        if (warehouseId == null || warehouseId <= 0) {
            throw new IllegalArgumentException(
                    "Warehouse ID must be a valid positive number");
        }

        if (pageable == null) {
            throw new IllegalArgumentException(
                    "Pageable cannot be null");
        }

        String whereClause = " WHERE warehouse_id = " + warehouseId;

        return findPage(whereClause, page,size);
    }

    // 3. Find Sales Orders by Status with pagination
    public Page<SalesOrder> findByStatus(
            OrderStatus status, @RequestParam(defaultValue = "0") int page,
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
                    "Order status cannot be null");
        }

        if (pageable == null) {
            throw new IllegalArgumentException(
                    "Pageable cannot be null");
        }

        String whereClause = " WHERE status = '" + status.name() + "'";

        return findPage(whereClause, page,size);
    }

    // 4. Count Sales Orders by Status
    public long countByStatus(OrderStatus status) {

        if (status == null) {
            throw new IllegalArgumentException(
                    "Order status cannot be null");
        }

        String whereClause = " WHERE status = '" + status.name() + "'";

        try {
            return countMatchingRecords(whereClause);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Error counting sales orders by status", e);
        }
    }

    // 5. Count Pending Sales Orders
    // Pending statuses: DRAFT and CONFIRMED
    public long countPendingOrders() {

        return countByStatus(OrderStatus.DRAFT)
                + countByStatus(OrderStatus.CONFIRMED);
    }

    // Common pagination implementation
    private Page<SalesOrder> findPage(
            String whereClause, @RequestParam(defaultValue = "0") int page,
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
        try {

            String baseQuery = "SELECT * FROM " + TABLE_NAME
                    + whereClause
                    + buildOrderBy(pageable.getSort());

            long totalRecords = countMatchingRecords(whereClause);

            if (pageable.isUnpaged()) {

                List<SalesOrder> allOrders =
                        fetchSalesOrders(baseQuery, 0, Long.MAX_VALUE);

                return new PageImpl<>(allOrders);
            }

            List<SalesOrder> pageContent = fetchSalesOrders(
                    baseQuery,
                    pageable.getOffset(),
                    pageable.getPageSize());

            return new PageImpl<>(
                    pageContent,
                    pageable,
                    totalRecords);

        } catch (Exception e) {
            throw new RuntimeException(
                    "Error fetching sales orders from Catalyst", e);
        }
    }

    // Fetch records in batches of 300
    private List<SalesOrder> fetchSalesOrders(
            String baseQuery,
            long offset,
            long requestedRecords) throws Exception {

        List<SalesOrder> orders = new ArrayList<>();

        long fetchedRecords = 0;

        while (fetchedRecords < requestedRecords) {

            int batchSize = (int) Math.min(
                    BATCH_SIZE,
                    requestedRecords - fetchedRecords);

            String query = baseQuery
                    + " LIMIT " + (offset + fetchedRecords)
                    + "," + batchSize;

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

    // Count matching records by fetching only ROWID
    private long countMatchingRecords(String whereClause)
            throws Exception {

        long total = 0;
        int offset = 0;

        while (true) {

            String query = "SELECT ROWID FROM " + TABLE_NAME
                    + whereClause
                    + " LIMIT " + offset + "," + BATCH_SIZE;

            ArrayList<ZCRowObject> rows =
                    ZCQL.getInstance().executeQuery(query);

            if (rows == null || rows.isEmpty()) {
                break;
            }

            total += rows.size();

            if (rows.size() < BATCH_SIZE) {
                break;
            }

            offset += rows.size();
        }

        return total;
    }

    // Build ORDER BY using supported properties only
    private String buildOrderBy(Sort sort) {

        List<String> orderExpressions = new ArrayList<>();

        if (sort != null && sort.isSorted()) {

            for (Sort.Order order : sort) {

                String column = getSortColumn(order.getProperty());

                if (column == null) {
                    continue;
                }

                orderExpressions.add(
                        column + (order.isAscending() ? " ASC" : " DESC"));
            }
        }

        // Default ordering for consistent pagination
        if (orderExpressions.isEmpty()) {
            orderExpressions.add("ROWID DESC");
        }

        return " ORDER BY " + String.join(", ", orderExpressions);
    }

    // Map Java sorting properties to Catalyst columns
    private String getSortColumn(String property) {

        switch (property) {

            case "id":
                return "ROWID";

            case "orderNumber":
                return "order_number";

            case "customerName":
                return "customer_name";

            case "customerEmail":
                return "customer_email";

            case "warehouseId":
            case "warehouse.id":
                return "warehouse_id";

            case "status":
                return "status";

            case "totalAmount":
                return "total_amount";

            case "createdAt":
                return "created_at";

            case "updatedAt":
                return "updated_at";

            case "carrier":
                return "carrier";

            case "trackingNumber":
                return "tracking_number";

            default:
                return null;
        }
    }

    // Convert Catalyst row into SalesOrder object
    private SalesOrder mapRow(ZCRowObject row) {

        Long warehouseId = toLong(row.get("warehouse_id"));

        Long createdByUserId =
                toLong(row.get("created_by_user_id"));

        Warehouse warehouse = null;

        if (warehouseId != null) {
            warehouse = new Warehouse();
            warehouse.setId(warehouseId);
        }

        User createdBy = null;

        if (createdByUserId != null) {
            createdBy = new User();
            createdBy.setId(createdByUserId);
        }

        String statusValue = toStringValue(row.get("status"));

        OrderStatus status = statusValue == null
                ? OrderStatus.DRAFT
                : OrderStatus.valueOf(statusValue.toUpperCase());

        return SalesOrder.builder()
                .id(toLong(row.get("ROWID")))
                .orderNumber(toStringValue(row.get("order_number")))
                .customerName(toStringValue(row.get("customer_name")))
                .customerEmail(toStringValue(row.get("customer_email")))
                .warehouse(warehouse)
                .status(status)
                .totalAmount(toBigDecimal(row.get("total_amount")))
                .createdBy(createdBy)
                .createdAt(toDateTime(row.get("created_at")))
                .updatedAt(toDateTime(row.get("updated_at")))
                .carrier(toStringValue(row.get("carrier")))
                .trackingNumber(toStringValue(row.get("tracking_number")))
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
    public Optional<SalesOrder> findById(Long id) {

    if (id == null || id <= 0) {
        throw new IllegalArgumentException("Invalid Sales Order ID");
    }

    String query = "SELECT * FROM sales_orders "
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
                "Error finding sales order: " + id, e);
    }
}
public SalesOrder save(SalesOrder order) {

    if (order == null) {
        throw new IllegalArgumentException(
                "Sales Order cannot be null");
    }

    if (order.getWarehouse() == null
            || order.getWarehouse().getId() == null
            || order.getCreatedBy() == null
            || order.getCreatedBy().getId() == null
            || order.getStatus() == null
            || order.getOrderNumber() == null
            || order.getCustomerName() == null
            || order.getCustomerEmail() == null) {

        throw new IllegalArgumentException(
                "Required Sales Order fields are missing");
    }

    LocalDateTime now = LocalDateTime.now();

    if (order.getCreatedAt() == null) {
        order.setCreatedAt(now);
    }

    order.setUpdatedAt(now);

    String orderNumber = escapeString(order.getOrderNumber());
    String customerName = escapeString(order.getCustomerName());
    String customerEmail = escapeString(order.getCustomerEmail());

    String carrier = toSqlString(order.getCarrier());
    String trackingNumber = toSqlString(order.getTrackingNumber());

    String status = order.getStatus().name();

    String totalAmount = order.getTotalAmount() == null
            ? "0"
            : order.getTotalAmount().toPlainString();

    Long warehouseId = order.getWarehouse().getId();
    Long userId = order.getCreatedBy().getId();

    String createdAt = formatDate(order.getCreatedAt());
    String updatedAt = formatDate(order.getUpdatedAt());

    try {

        if (order.getId() == null) {

            // INSERT new Sales Order
            String query =
                    "INSERT INTO sales_orders "
                    + "(order_number, customer_name, customer_email, "
                    + "warehouse_id, status, total_amount, "
                    + "created_by_user_id, created_at, updated_at, "
                    + "carrier, tracking_number) "
                    + "VALUES ("
                    + "'" + orderNumber + "', "
                    + "'" + customerName + "', "
                    + "'" + customerEmail + "', "
                    + warehouseId + ", "
                    + "'" + status + "', "
                    + totalAmount + ", "
                    + userId + ", "
                    + "'" + createdAt + "', "
                    + "'" + updatedAt + "', "
                    + carrier + ", "
                    + trackingNumber + ")";

            ZCQL.getInstance().executeQuery(query);

            // Retrieve the newly inserted record
            return findByOrderNumber(order.getOrderNumber())
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Sales Order inserted but could not "
                                    + "be retrieved: "
                                    + order.getOrderNumber()));

        } else {

            // UPDATE existing Sales Order
            String query =
                    "UPDATE sales_orders SET "
                    + "order_number = '" + orderNumber + "', "
                    + "customer_name = '" + customerName + "', "
                    + "customer_email = '" + customerEmail + "', "
                    + "warehouse_id = " + warehouseId + ", "
                    + "status = '" + status + "', "
                    + "total_amount = " + totalAmount + ", "
                    + "created_by_user_id = " + userId + ", "
                    + "updated_at = '" + updatedAt + "', "
                    + "carrier = " + carrier + ", "
                    + "tracking_number = " + trackingNumber + " "
                    + "WHERE ROWID = " + order.getId();

            ZCQL.getInstance().executeQuery(query);

            // Retrieve updated record
            return findById(order.getId())
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Sales Order update could not "
                                    + "be verified: " + order.getId()));
        }

    } catch (Exception e) {
        throw new RuntimeException(
                "Error saving Sales Order in Catalyst", e);
    }
}private String toSqlString(String value) {

    if (value == null || value.trim().isEmpty()) {
        return "NULL";
    }

    return "'" + escapeString(value) + "'";
}
public Page<SalesOrder> findAll(@RequestParam(defaultValue = "0") int page,
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
        throw new IllegalArgumentException("Pageable cannot be null");
    }

    try {

        String query = "SELECT * FROM sales_orders ORDER BY ROWID DESC";

        long total = countAllSalesOrders();

        long offset = pageable.isUnpaged()
                ? 0 : pageable.getOffset();

        long pageSize = pageable.isUnpaged()
                ? total : pageable.getPageSize();

        List<SalesOrder> orders = new ArrayList<>();

        long fetched = 0;

        while (fetched < pageSize) {

            int batchSize = (int) Math.min(300, pageSize - fetched);

            String batchQuery = query
                    + " LIMIT " + (offset + fetched)
                    + "," + batchSize;

            ArrayList<ZCRowObject> rows =
                    ZCQL.getInstance().executeQuery(batchQuery);

            if (rows == null || rows.isEmpty()) {
                break;
            }

            for (ZCRowObject row : rows) {
                orders.add(mapRow(row));
            }

            fetched += rows.size();

            if (rows.size() < batchSize) {
                break;
            }
        }

        if (pageable.isUnpaged()) {
            return new PageImpl<>(orders);
        }

        return new PageImpl<>(orders, pageable, total);

    } catch (Exception e) {
        throw new RuntimeException(
                "Error fetching all sales orders from Catalyst", e);
    }
}

private long countAllSalesOrders() throws Exception {

    long total = 0;
    int offset = 0;

    while (true) {

        String query = "SELECT ROWID FROM sales_orders "
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
private String formatDate(LocalDateTime dateTime) {

    if (dateTime == null) {
        return null;
    }

    return dateTime.format(
            java.time.format.DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd HH:mm:ss:SSS"));
}

}
