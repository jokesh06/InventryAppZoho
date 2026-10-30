
package com.example.demo.repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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
import com.example.demo.entity.StockTransfer;
import com.example.demo.entity.StockTransfer.TransferStatus;
import com.example.demo.entity.Warehouse;
import com.example.demo.entity.User;

import com.zc.component.object.ZCRowObject;
import com.zc.component.zcql.ZCQL;

@Repository
public class StockTransferRepository {

    private static final String TABLE_NAME = "stock_transfers";

    // Find stock transfer using transfer number
    public Optional<StockTransfer> findByTransferNumber(
            String transferNumber) {

        if (transferNumber == null || transferNumber.trim().isEmpty()) {
            return Optional.empty();
        }

        String safeTransferNumber =
                escapeString(transferNumber.trim());

        String query = "SELECT * FROM " + TABLE_NAME
                + " WHERE transfer_number = '"
                + safeTransferNumber + "'"
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
                    "Error finding stock transfer by transfer number",
                    e);
        }
    }

    // Convert Catalyst row into StockTransfer object
    private StockTransfer mapRow(ZCRowObject row) {

        Long sourceWarehouseId =
                toLong(row.get("source_warehouse_id"));

        Long destinationWarehouseId =
                toLong(row.get("destination_warehouse_id"));

        Long createdByUserId =
                toLong(row.get("created_by_user_id"));

        // Source warehouse reference
        Warehouse sourceWarehouse = null;

        if (sourceWarehouseId != null) {
            sourceWarehouse = new Warehouse();
            sourceWarehouse.setId(sourceWarehouseId);
        }

        // Destination warehouse reference
        Warehouse destinationWarehouse = null;

        if (destinationWarehouseId != null) {
            destinationWarehouse = new Warehouse();
            destinationWarehouse.setId(destinationWarehouseId);
        }

        // User reference
        User createdBy = null;

        if (createdByUserId != null) {
            createdBy = new User();
            createdBy.setId(createdByUserId);
        }

        String statusValue =
                toStringValue(row.get("status"));

        TransferStatus status = statusValue == null
                ? TransferStatus.DRAFT
                : TransferStatus.valueOf(statusValue.toUpperCase());

        return StockTransfer.builder()
                .id(toLong(row.get("ROWID")))
                .transferNumber(
                        toStringValue(row.get("transfer_number")))
                .sourceWarehouse(sourceWarehouse)
                .destinationWarehouse(destinationWarehouse)
                .status(status)
                .createdBy(createdBy)
                .dispatchedAt(toDateTime(row.get("dispatched_at")))
                .receivedAt(toDateTime(row.get("received_at")))
                .createdAt(toDateTime(row.get("created_at")))
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

    private String toStringValue(Object value) {

        return value == null
                ? null
                : value.toString();
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

        if (value instanceof java.util.Date) {
            return new Timestamp(
                    ((java.util.Date) value).getTime())
                    .toLocalDateTime();
        }

        String dateValue = value.toString().trim();

        try {
            return LocalDateTime.parse(dateValue);
        } catch (Exception ignored) {
            // Try offset date-time.
        }

        try {
            return OffsetDateTime.parse(dateValue).toLocalDateTime();
        } catch (Exception ignored) {
            // Try date-time with a space separator.
        }

        return LocalDateTime.parse(
                dateValue.replaceFirst(" ", "T"));
    }
    public Optional<StockTransfer> findById(Long id) {

    if (id == null || id <= 0) {
        throw new IllegalArgumentException("Invalid Stock Transfer ID");
    }

    String query = "SELECT * FROM stock_transfers "
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
                "Error finding stock transfer by ID: " + id, e);
    }
}
public StockTransfer save(StockTransfer transfer) {

    if (transfer == null
            || transfer.getSourceWarehouse() == null
            || transfer.getSourceWarehouse().getId() == null
            || transfer.getDestinationWarehouse() == null
            || transfer.getDestinationWarehouse().getId() == null
            || transfer.getCreatedBy() == null
            || transfer.getCreatedBy().getId() == null
            || transfer.getTransferNumber() == null
            || transfer.getStatus() == null) {

        throw new IllegalArgumentException(
                "Required stock transfer fields are missing");
    }

    if (transfer.getCreatedAt() == null) {
        transfer.setCreatedAt(java.time.LocalDateTime.now());
    }

    String transferNumber =
            transfer.getTransferNumber().replace("'", "''");

    String status = transfer.getStatus().name();

    Long sourceId = transfer.getSourceWarehouse().getId();
    Long destinationId = transfer.getDestinationWarehouse().getId();
    Long userId = transfer.getCreatedBy().getId();

    try {

        if (transfer.getId() == null) {

            String query =
                    "INSERT INTO stock_transfers "
                    + "(transfer_number, source_warehouse_id, "
                    + "destination_warehouse_id, status, "
                    + "created_by_user_id, created_at, "
                    + "dispatched_at, received_at) "
                    + "VALUES ("
                    + "'" + transferNumber + "', "
                    + sourceId + ", "
                    + destinationId + ", "
                    + "'" + status + "', "
                    + userId + ", "
                    + toSqlDateTime(transfer.getCreatedAt()) + ", "
                    + toSqlDateTime(transfer.getDispatchedAt()) + ", "
                    + toSqlDateTime(transfer.getReceivedAt()) + ")";

            ZCQL.getInstance().executeQuery(query);

            return findByTransferNumber(transfer.getTransferNumber())
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Inserted transfer could not be retrieved"));

        } else {

            String query =
                    "UPDATE stock_transfers SET "
                    + "transfer_number = '" + transferNumber + "', "
                    + "source_warehouse_id = " + sourceId + ", "
                    + "destination_warehouse_id = " + destinationId + ", "
                    + "status = '" + status + "', "
                    + "created_by_user_id = " + userId + ", "
                    + "dispatched_at = "
                    + toSqlDateTime(transfer.getDispatchedAt()) + ", "
                    + "received_at = "
                    + toSqlDateTime(transfer.getReceivedAt()) + " "
                    + "WHERE ROWID = " + transfer.getId();

            ZCQL.getInstance().executeQuery(query);

            return findById(transfer.getId())
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Updated transfer could not be retrieved"));
        }

    } catch (Exception e) {
        throw new RuntimeException(
                "Error saving stock transfer in Catalyst", e);
    }
}

private String toSqlDateTime(java.time.LocalDateTime value) {

    if (value == null) {
        return "NULL";
    }

    return "'" + value.format(
            java.time.format.DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd HH:mm:ss")) + "'";
}


private static final int TRANSFER_BATCH_SIZE = 300;

public Page<StockTransfer> findAll(@RequestParam(defaultValue = "0") int page,
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
        throw new IllegalArgumentException(
                "Pageable cannot be null");
    }

    try {

        String baseQuery = "SELECT * FROM stock_transfers"
                + buildTransferOrderBy(pageable.getSort());

        long total = countAllTransferRecords();

        long offset = pageable.isUnpaged()
                ? 0 : pageable.getOffset();

        long pageSize = pageable.isUnpaged()
                ? total : pageable.getPageSize();

        List<StockTransfer> transfers =
                new ArrayList<>();

        long fetched = 0;

        while (fetched < pageSize) {

            int batchSize = (int) Math.min(
                    TRANSFER_BATCH_SIZE,
                    pageSize - fetched);

            String query = baseQuery
                    + " LIMIT " + (offset + fetched)
                    + "," + batchSize;

            ArrayList<ZCRowObject> rows =
                    ZCQL.getInstance().executeQuery(query);

            if (rows == null || rows.isEmpty()) {
                break;
            }

            for (ZCRowObject row : rows) {
                transfers.add(mapRow(row));
            }

            fetched += rows.size();

            if (rows.size() < batchSize) {
                break;
            }
        }

        if (pageable.isUnpaged()) {
            return new PageImpl<>(transfers);
        }

        return new PageImpl<>(
                transfers, pageable, total);

    } catch (Exception e) {
        throw new RuntimeException(
                "Error fetching stock transfers from Catalyst", e);
    }
}

// Count stock transfers
private long countAllTransferRecords() throws Exception {

    long total = 0;
    int offset = 0;

    while (true) {

        String query = "SELECT ROWID FROM stock_transfers "
                + "LIMIT " + offset + "," + TRANSFER_BATCH_SIZE;

        ArrayList<ZCRowObject> rows =
                ZCQL.getInstance().executeQuery(query);

        if (rows == null || rows.isEmpty()) {
            break;
        }

        total += rows.size();

        if (rows.size() < TRANSFER_BATCH_SIZE) {
            break;
        }

        offset += rows.size();
    }

    return total;
}

// Build sorting clause
private String buildTransferOrderBy(Sort sort) {

    List<String> columns = new ArrayList<>();

    if (sort != null && sort.isSorted()) {

        for (Sort.Order order : sort) {

            String column =
                    getTransferSortColumn(order.getProperty());

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

// Allow only known Catalyst column names
private String getTransferSortColumn(String property) {

    switch (property) {

        case "id":
            return "ROWID";

        case "transferNumber":
            return "transfer_number";

        case "status":
            return "status";

        case "createdAt":
            return "created_at";

        case "dispatchedAt":
            return "dispatched_at";

        case "receivedAt":
            return "received_at";

        default:
            return null;
    }
}

}
