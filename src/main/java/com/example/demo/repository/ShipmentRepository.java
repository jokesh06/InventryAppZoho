
package com.example.demo.repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
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

import com.example.demo.entity.Shipment;
import com.example.demo.entity.Shipment.ShipmentStatus;
import com.example.demo.entity.InventoryMovement;
import com.example.demo.entity.SalesOrder;
import com.example.demo.entity.User;

import com.zc.component.object.ZCRowObject;
import com.zc.component.zcql.ZCQL;

@Repository
public class ShipmentRepository {

    private static final String TABLE_NAME = "shipments";

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 1. Find shipment by shipment number
    public Optional<Shipment> findByShipmentNumber(
            String shipmentNumber) {

        if (shipmentNumber == null || shipmentNumber.trim().isEmpty()) {
            return Optional.empty();
        }

        String query = "SELECT * FROM " + TABLE_NAME
                + " WHERE shipment_number = '"
                + escapeString(shipmentNumber.trim()) + "'"
                + " LIMIT 0,1";

        return executeSingleQuery(query);
    }

    // 2. Find shipment by Sales Order ID
    public Optional<Shipment> findBySalesOrderId(
            Long salesOrderId) {

        validateId(salesOrderId);

        String query = "SELECT * FROM " + TABLE_NAME
                + " WHERE sales_order_id = " + salesOrderId
                + " LIMIT 0,1";

        return executeSingleQuery(query);
    }

    // 3. Find shipment by Catalyst ROWID
    public Optional<Shipment> findById(Long id) {

        validateId(id);

        String query = "SELECT * FROM " + TABLE_NAME
                + " WHERE ROWID = " + id
                + " LIMIT 0,1";

        return executeSingleQuery(query);
    }

    // 4. Check whether shipment exists for Sales Order
    public Boolean existsBySalesOrderId(Long salesOrderId) {

        return findBySalesOrderId(salesOrderId).isPresent();
    }

    // 5. Save shipment - INSERT or UPDATE
    public Shipment save(Shipment shipment) {

        if (shipment == null) {
            throw new IllegalArgumentException(
                    "Shipment cannot be null");
        }

        if (shipment.getShipmentNumber() == null
                || shipment.getShipmentNumber().trim().isEmpty()
                || shipment.getSalesOrder() == null
                || shipment.getSalesOrder().getId() == null
                || shipment.getCreatedBy() == null
                || shipment.getCreatedBy().getId() == null
                || shipment.getCarrier() == null
                || shipment.getTrackingNumber() == null
                || shipment.getStatus() == null) {

            throw new IllegalArgumentException(
                    "Required shipment fields are missing");
        }

        if (shipment.getShippedAt() == null) {
            shipment.setShippedAt(LocalDateTime.now());
        }

        Long salesOrderId = shipment.getSalesOrder().getId();
        Long userId = shipment.getCreatedBy().getId();

        String shipmentNumber =
                escapeString(shipment.getShipmentNumber());

        String carrier =
                escapeString(shipment.getCarrier());

        String trackingNumber =
                escapeString(shipment.getTrackingNumber());

        String status = shipment.getStatus().name();

        String shippedAt = toSqlDateTime(shipment.getShippedAt());

        String deliveredAt = toSqlDateTime(
                shipment.getDeliveredAt());

        try {

            if (shipment.getId() == null) {

                // INSERT new shipment
                String query =
                        "INSERT INTO " + TABLE_NAME
                        + " (shipment_number, sales_order_id, "
                        + "carrier, tracking_number, status, "
                        + "shipped_at, delivered_at, created_by_user_id) "
                        + "VALUES ("
                        + "'" + shipmentNumber + "', "
                        + salesOrderId + ", "
                        + "'" + carrier + "', "
                        + "'" + trackingNumber + "', "
                        + "'" + status + "', "
                        + shippedAt + ", "
                        + deliveredAt + ", "
                        + userId + ")";

                ZCQL.getInstance().executeQuery(query);

                // Retrieve inserted shipment and generated ROWID
                return findByShipmentNumber(
                        shipment.getShipmentNumber())
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Shipment inserted but could not "
                                        + "be retrieved: "
                                        + shipment.getShipmentNumber()));

            } else {

                // UPDATE existing shipment
                String query =
                        "UPDATE " + TABLE_NAME + " SET "
                        + "shipment_number = '" + shipmentNumber + "', "
                        + "sales_order_id = " + salesOrderId + ", "
                        + "carrier = '" + carrier + "', "
                        + "tracking_number = '" + trackingNumber + "', "
                        + "status = '" + status + "', "
                        + "shipped_at = " + shippedAt + ", "
                        + "delivered_at = " + deliveredAt + ", "
                        + "created_by_user_id = " + userId + " "
                        + "WHERE ROWID = " + shipment.getId();

                ZCQL.getInstance().executeQuery(query);

                return findById(shipment.getId())
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Shipment update could not "
                                        + "be verified: "
                                        + shipment.getId()));
            }

        } catch (Exception e) {

            throw new RuntimeException(
                    "Error saving shipment in Catalyst", e);
        }
    }

    // Execute SELECT and convert first row
    private Optional<Shipment> executeSingleQuery(String query) {

        try {

            ArrayList<ZCRowObject> rows =
                    ZCQL.getInstance().executeQuery(query);

            if (rows == null || rows.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(mapRow(rows.get(0)));

        } catch (Exception e) {

            throw new RuntimeException(
                    "Error fetching shipment from Catalyst", e);
        }
    }

    // Convert Catalyst row into Shipment object
    private Shipment mapRow(ZCRowObject row) {

        Long salesOrderId =
                toLong(row.get("sales_order_id"));

        Long createdByUserId =
                toLong(row.get("created_by_user_id"));

        SalesOrder salesOrder = null;

        if (salesOrderId != null) {
            salesOrder = new SalesOrder();
            salesOrder.setId(salesOrderId);
        }

        User createdBy = null;

        if (createdByUserId != null) {
            createdBy = new User();
            createdBy.setId(createdByUserId);
        }

        String statusValue =
                toStringValue(row.get("status"));

        ShipmentStatus status = statusValue == null
                ? null
                : ShipmentStatus.valueOf(statusValue.toUpperCase());

        return Shipment.builder()
                .id(toLong(row.get("ROWID")))
                .shipmentNumber(
                        toStringValue(row.get("shipment_number")))
                .salesOrder(salesOrder)
                .carrier(toStringValue(row.get("carrier")))
                .trackingNumber(
                        toStringValue(row.get("tracking_number")))
                .status(status)
                .shippedAt(toDateTime(row.get("shipped_at")))
                .deliveredAt(toDateTime(row.get("delivered_at")))
                .createdBy(createdBy)
                .build();
    }

    // Validate Catalyst ROWID
    private void validateId(Long id) {

        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "Record ID must be a positive number");
        }
    }

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

    private String toSqlDateTime(LocalDateTime value) {

        if (value == null) {
            return "NULL";
        }

        return "'" + value.format(DATE_FORMAT) + "'";
    }

    // Convert Catalyst date-time into LocalDateTime
    private LocalDateTime toDateTime(Object value) {

        if (value == null) {
            return null;
        }

        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }

        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime();
        }

        String dateValue = value.toString().trim();

        try {
            return LocalDateTime.parse(dateValue);
        } catch (Exception ignored) {
            // Try ISO offset date-time.
        }

        try {
            return OffsetDateTime.parse(dateValue).toLocalDateTime();
        } catch (Exception ignored) {
            // Try Catalyst timestamp with colon milliseconds.
        }

        try {
            return LocalDateTime.parse(
                    dateValue,
                    DateTimeFormatter.ofPattern(
                            "yyyy-MM-dd HH:mm:ss:SSS"));
        } catch (Exception ignored) {
            // Try timestamp with decimal milliseconds.
        }

        try {
            return LocalDateTime.parse(
                    dateValue,
                    DateTimeFormatter.ofPattern(
                            "yyyy-MM-dd HH:mm:ss.SSS"));
        } catch (Exception ignored) {
            return LocalDateTime.parse(
                    dateValue,
                    DATE_FORMAT);
        }
    }


    
private static final int BATCH_SIZE = 300;

// Find all shipments with pagination
public Page<Shipment> findAll(@RequestParam(defaultValue = "0") int page,
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

        String baseQuery = "SELECT * FROM shipments"
                + buildOrderBy(pageable.getSort());

        long total = countAllShipments();

        long offset = pageable.isUnpaged()
                ? 0 : pageable.getOffset();

        long pageSize = pageable.isUnpaged()
                ? total : pageable.getPageSize();

        List<Shipment> shipments = new ArrayList<>();

        long fetched = 0;

        while (fetched < pageSize) {

            int batchSize = (int) Math.min(
                    BATCH_SIZE, pageSize - fetched);

            String query = baseQuery
                    + " LIMIT " + (offset + fetched)
                    + "," + batchSize;

            ArrayList<ZCRowObject> rows =
                    ZCQL.getInstance().executeQuery(query);

            if (rows == null || rows.isEmpty()) {
                break;
            }

            for (ZCRowObject row : rows) {
                shipments.add(mapRow(row));
            }

            fetched += rows.size();

            if (rows.size() < batchSize) {
                break;
            }
        }

        if (pageable.isUnpaged()) {
            return new PageImpl<>(shipments);
        }

        return new PageImpl<>(
                shipments, pageable, total);

    } catch (Exception e) {

        throw new RuntimeException(
                "Error fetching shipments from Catalyst", e);
    }
}

// Count all shipment records
private long countAllShipments() throws Exception {

    long total = 0;
    int offset = 0;

    while (true) {

        String query = "SELECT ROWID FROM shipments "
                + "LIMIT " + offset + "," + BATCH_SIZE;

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

// Build sorting query
private String buildOrderBy(Sort sort) {

    List<String> columns = new ArrayList<>();

    if (sort != null && sort.isSorted()) {

        for (Sort.Order order : sort) {

            String column = getSortColumn(order.getProperty());

            if (column != null) {
                columns.add(
                        column
                        + (order.isAscending() ? " ASC" : " DESC"));
            }
        }
    }

    if (columns.isEmpty()) {
        columns.add("ROWID DESC");
    }

    return " ORDER BY " + String.join(", ", columns);
}

// Map Java property names to Catalyst columns
private String getSortColumn(String property) {

    switch (property) {

        case "id":
            return "ROWID";

        case "shipmentNumber":
            return "shipment_number";

        case "carrier":
            return "carrier";

        case "trackingNumber":
            return "tracking_number";

        case "status":
            return "status";

        case "shippedAt":
            return "shipped_at";

        case "deliveredAt":
            return "delivered_at";

        default:
            return null;
    }
}

}
