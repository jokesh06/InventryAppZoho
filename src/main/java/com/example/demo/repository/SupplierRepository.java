
package com.example.demo.repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.example.demo.entity.Supplier;

import com.zc.component.object.ZCRowObject;
import com.zc.component.zcql.ZCQL;

@Repository
public class SupplierRepository {

    private static final String TABLE_NAME = "suppliers";

    private static final int BATCH_SIZE = 300;

    // 1. Find supplier using supplier code
    public Optional<Supplier> findByCode(String code) {

        if (code == null || code.trim().isEmpty()) {
            return Optional.empty();
        }

        String safeCode = escapeString(code.trim());

        String query = "SELECT * FROM " + TABLE_NAME
                + " WHERE code = '" + safeCode + "'"
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
                    "Error finding supplier by code from Catalyst", e);
        }
    }

    // 2. Find all active suppliers
    public List<Supplier> findByIsActiveTrue() {

        List<Supplier> suppliers = new ArrayList<>();

        try {

            int offset = 0;

            while (true) {

                String query = "SELECT * FROM " + TABLE_NAME
                        + " WHERE is_active IS TRUE"
                        + " ORDER BY ROWID ASC"
                        + " LIMIT " + offset + "," + BATCH_SIZE;

                ArrayList<ZCRowObject> rows =
                        ZCQL.getInstance().executeQuery(query);

                if (rows == null || rows.isEmpty()) {
                    break;
                }

                for (ZCRowObject row : rows) {
                    suppliers.add(mapRow(row));
                }

                if (rows.size() < BATCH_SIZE) {
                    break;
                }

                offset += BATCH_SIZE;
            }

            return suppliers;

        } catch (Exception e) {

            throw new RuntimeException(
                    "Error fetching active suppliers from Catalyst", e);
        }
    }

    // Convert Catalyst row into Supplier object
    private Supplier mapRow(ZCRowObject row) {

        return Supplier.builder()
                .id(toLong(row.get("ROWID")))
                .code(toStringValue(row.get("code")))
                .name(toStringValue(row.get("name")))
                .contactEmail(toStringValue(row.get("contact_email")))
                .phone(toStringValue(row.get("phone")))
                .address(toStringValue(row.get("address")))
                .isActive(toBoolean(row.get("is_active")))
                .createdAt(toDateTime(row.get("created_at")))
                .updatedAt(toDateTime(row.get("updated_at")))
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

    private Boolean toBoolean(Object value) {

        if (value == null) {
            return false;
        }

        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        String booleanValue = value.toString();

        return "true".equalsIgnoreCase(booleanValue)
                || "1".equals(booleanValue);
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
            // Try Catalyst date-time with a space separator.
        }

        return LocalDateTime.parse(
                dateValue.replaceFirst(" ", "T"));
    }
    
public Optional<Supplier> findById(Long id) {

    if (id == null || id <= 0) {
        throw new IllegalArgumentException("Invalid Supplier ID");
    }

    String query = "SELECT * FROM suppliers "
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
                "Error finding supplier by ID: " + id, e);
    }
}

}
