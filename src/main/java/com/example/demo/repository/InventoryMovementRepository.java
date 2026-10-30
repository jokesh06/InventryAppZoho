
package com.example.demo.repository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.core.TypedPropertyPath;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.demo.entity.InventoryMovement;
import com.example.demo.entity.InventoryMovement.MovementType;
import com.example.demo.entity.Product;
import com.example.demo.entity.Warehouse;
import com.example.demo.entity.User;
import com.example.demo.util.CatalystDateTimes;

import com.zc.component.object.ZCRowObject;
import com.zc.component.zcql.ZCQL;

@Repository
public class InventoryMovementRepository {

    private static final String TABLE_NAME = "inventory_movements";
    private static final int FETCH_SIZE = 300;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Filter inventory movements with pagination
    public Page<InventoryMovement> filterMovements(
            Long warehouseId,
            Long productId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
                  @RequestParam(defaultValue = "20") int size

           ) {
            Pageable pageable = PageRequest.of(
        Math.max(page, 0),
        Math.min(Math.max(size, 1), 100));
        


        if (pageable == null) {
            throw new IllegalArgumentException("Pageable cannot be null");
        }

        try {
            StringBuilder query = new StringBuilder(
                    "SELECT * FROM " + TABLE_NAME);

            List<String> conditions = new ArrayList<>();

            if (warehouseId != null) {
                conditions.add("warehouse_id = " + warehouseId);
            }

            if (productId != null) {
                conditions.add("product_id = " + productId);
            }

            if (startDate != null) {
                conditions.add(
                        "movement_timestamp >= '" + formatDate(startDate) + "'");
            }

            if (endDate != null) {
                conditions.add(
                        "movement_timestamp <= '" + formatDate(endDate) + "'");
            }

            if (!conditions.isEmpty()) {
                query.append(" WHERE ");
                query.append(String.join(" AND ", conditions));
            }

            query.append(" ORDER BY movement_timestamp DESC");

            // Fetch all matching rows in batches of 300.
            // Catalyst SELECT * has a 300-row limit per query.
            List<InventoryMovement> allMovements =
                    fetchAllMovements(query.toString());

            int start = (int) Math.min(
                    pageable.getOffset(), allMovements.size());

            int end = Math.min(
                    start + pageable.getPageSize(),
                    allMovements.size());

            List<InventoryMovement> pageContent =
                    allMovements.subList(start, end);

            return new PageImpl<>(
                    pageContent,
                    pageable,
                    allMovements.size());

        } catch (Exception e) {
            throw new RuntimeException(
                    "Error filtering inventory movements from Catalyst", e);
        }
    }

    // Get latest 10 inventory movements
    public List<InventoryMovement> findTop10ByOrderByTimestampDesc() {

        String query = "SELECT * FROM " + TABLE_NAME
                + " ORDER BY CREATEDTIME  DESC LIMIT 10";

        try {
            ArrayList<ZCRowObject> rows =
                    ZCQL.getInstance().executeQuery(query);

            List<InventoryMovement> movements = new ArrayList<>();

            if (rows == null) {
                return movements;
            }

            for (ZCRowObject row : rows) {
                movements.add(mapRow(row));
            }

            return movements;

        } catch (Exception e) {
            throw new RuntimeException(
                    "Error fetching latest inventory movements from Catalyst", e);
        }
    }

    // Fetch records in batches because SELECT * returns at most 300 rows.
    private List<InventoryMovement> fetchAllMovements(String baseQuery)
            throws Exception {

        List<InventoryMovement> movements = new ArrayList<>();

        int offset = 0;

        while (true) {

            String query = baseQuery
                    + " LIMIT " + offset + "," + FETCH_SIZE;

            ArrayList<ZCRowObject> rows =
                    ZCQL.getInstance().executeQuery(query);

            if (rows == null || rows.isEmpty()) {
                break;
            }

            for (ZCRowObject row : rows) {
                movements.add(mapRow(row));
            }

            if (rows.size() < FETCH_SIZE) {
                break;
            }

            offset += FETCH_SIZE;
        }

        return movements;
    }

    // Convert Catalyst row into Java InventoryMovement object.
    private InventoryMovement mapRow(ZCRowObject row) {

        Long productId = toLong(row.get("product_id"));
        Long warehouseId = toLong(row.get("warehouse_id"));
        Long userId = toLong(row.get("user_id"));

        Product product = null;

        if (productId != null) {
            product = new Product();
            product.setId(productId);
        }

        Warehouse warehouse = null;

        if (warehouseId != null) {
            warehouse = new Warehouse();
            warehouse.setId(warehouseId);
        }

        User user = null;

        if (userId != null) {
            user = new User();
            user.setId(userId);
        }

        String movementTypeValue = toStringValue(
                row.get("movement_type"));

        MovementType movementType = movementTypeValue == null
                ? null
                : MovementType.valueOf(
                        movementTypeValue.toUpperCase());

        return InventoryMovement.builder()
                .id(toLong(row.get("ROWID")))
                .movementType(movementType)
                .product(product)
                .warehouse(warehouse)
                .onHandChange(toInteger(row.get("on_hand_change")))
                .reservedChange(toInteger(row.get("reserved_change")))
                .referenceDocument(
                        toStringValue(row.get("reference_document")))
                .reason(toStringValue(row.get("reason")))
                .user(user)
                .timestamp(toDateTime(row.get("movement_timestamp")))
                .build();
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

    private String toStringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String formatDate(LocalDateTime dateTime) {
        return dateTime.format(DATE_FORMAT);
    }

    private LocalDateTime toDateTime(Object value) {
        return CatalystDateTimes.parse(value);
    }
    
public InventoryMovement save(InventoryMovement movement) {

    if (movement == null) {
        throw new IllegalArgumentException(
                "Inventory movement cannot be null");
    }

    if (movement.getMovementType() == null
            || movement.getProduct() == null
            || movement.getProduct().getId() == null
            || movement.getWarehouse() == null
            || movement.getWarehouse().getId() == null
            || movement.getUser() == null
            || movement.getUser().getId() == null
            || movement.getReferenceDocument() == null
            || movement.getReferenceDocument().trim().isEmpty()) {

        throw new IllegalArgumentException(
                "Movement type, Product, Warehouse, User "
                + "and Reference Document are required");
    }

    if (movement.getOnHandChange() == null
            || movement.getReservedChange() == null) {
        throw new IllegalArgumentException(
                "Stock quantity changes cannot be null");
    }

    if (movement.getTimestamp() == null) {
        movement.setTimestamp(LocalDateTime.now());
    }

    Long productId = movement.getProduct().getId();
    Long warehouseId = movement.getWarehouse().getId();
    Long userId = movement.getUser().getId();

    String movementType =
            movement.getMovementType().name();

    String referenceDocument =
            escapeSql(movement.getReferenceDocument());

    String reason = movement.getReason() == null
            ? "NULL"
            : "'" + escapeSql(movement.getReason()) + "'";

    String timestamp = movement.getTimestamp().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS"));

    String query =
            "INSERT INTO inventory_movements "
            + "(movement_type, product_id, warehouse_id, "
            + "on_hand_change, reserved_change, "
            + "reference_document, reason, user_id, movement_timestamp) "
            + "VALUES ("
            + "'" + movementType + "', "
            + productId + ", "
            + warehouseId + ", "
            + movement.getOnHandChange() + ", "
            + movement.getReservedChange() + ", "
            + "'" + referenceDocument + "', "
            + reason + ", "
            + userId + ", "
            + "'" + timestamp + "')";

    try {

        ZCQL.getInstance().executeQuery(query);

        return movement;

    } catch (Exception e) {

        throw new RuntimeException(
                "Error inserting inventory movement into Catalyst", e);
    }
}

// Escape single quotes in string values
private String escapeSql(String value) {
    return value.replace("'", "''");
}

}
