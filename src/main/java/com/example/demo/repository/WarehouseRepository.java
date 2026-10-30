
package com.example.demo.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.example.demo.entity.Warehouse;
import com.example.demo.entity.Warehouse.Status;
import com.example.demo.util.CatalystDateTimes;

import com.zc.component.object.ZCRowObject;
import com.zc.component.zcql.ZCQL;

@Repository
public class WarehouseRepository {

    private static final String TABLE_NAME = "warehouses";

    private static final int BATCH_SIZE = 300;

    // 1. Find warehouse using code
    public Optional<Warehouse> findByCode(String code) {

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
                    "Error finding warehouse by code from Catalyst", e);
        }
    }

    // 2. Check whether warehouse code already exists
    public Boolean existsByCode(String code) {

        return findByCode(code).isPresent();
    }

    // 3. Find warehouses using status
    public List<Warehouse> findByStatus(Status status) {

        if (status == null) {
            throw new IllegalArgumentException(
                    "Warehouse status cannot be null");
        }

        List<Warehouse> warehouses = new ArrayList<>();

        try {

            int offset = 0;

            while (true) {

                String query = "SELECT * FROM " + TABLE_NAME
                        + " WHERE status = '" + status.name() + "'"
                        + " ORDER BY ROWID ASC"
                        + " LIMIT " + offset + "," + BATCH_SIZE;

                ArrayList<ZCRowObject> rows =
                        ZCQL.getInstance().executeQuery(query);

                if (rows == null || rows.isEmpty()) {
                    break;
                }

                for (ZCRowObject row : rows) {
                    warehouses.add(mapRow(row));
                }

                if (rows.size() < BATCH_SIZE) {
                    break;
                }

                offset += rows.size();
            }

            return warehouses;

        } catch (Exception e) {

            throw new RuntimeException(
                    "Error fetching warehouses by status from Catalyst", e);
        }
    }

    // Convert Catalyst row into Warehouse object
    private Warehouse mapRow(ZCRowObject row) {

        String statusValue = toStringValue(row.get("status"));

        Status status = statusValue == null
                ? null
                : Status.valueOf(statusValue.toUpperCase());

        return Warehouse.builder()
                .id(toLong(row.get("ROWID")))
                .code(toStringValue(row.get("code")))
                .name(toStringValue(row.get("name")))
                .address(toStringValue(row.get("address")))
                .city(toStringValue(row.get("city")))
                .state(toStringValue(row.get("state")))
                .country(toStringValue(row.get("country")))
                .status(status)
                .createdAt(toDateTime(row.get("CREATEDTIME")))
                .updatedAt(toDateTime(row.get("MODIFIEDTIME")))
                .build();
    }

    // Escape single quotes in strings
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

    // Convert Catalyst timestamp into LocalDateTime
    private LocalDateTime toDateTime(Object value) {
        return CatalystDateTimes.parse(value);
    }
    public Optional<Warehouse> findById(Long id) {

    if (id == null || id <= 0) {
        throw new IllegalArgumentException("Invalid Warehouse ID");
    }

    String query = "SELECT * FROM warehouses "
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
                "Error finding warehouse by ID: " + id, e);
    }
}


public List<Warehouse> findAll() {

    List<Warehouse> warehouses = new ArrayList<>();

    try {

        int offset = 0;

        while (true) {

            String query = "SELECT * FROM warehouses "
                    + "ORDER BY ROWID ASC "
                    + "LIMIT " + offset + ",300";

            ArrayList<ZCRowObject> rows =
                    ZCQL.getInstance().executeQuery(query);

            if (rows == null || rows.isEmpty()) {
                break;
            }

            for (ZCRowObject row : rows) {
                warehouses.add(mapRow(row));
            }

            if (rows.size() < 300) {
                break;
            }

            offset += rows.size();
        }

        return warehouses;

    } catch (Exception e) {
        throw new RuntimeException(
                "Error fetching warehouses from Catalyst", e);
    }
}


public Warehouse save(Warehouse warehouse) {

    if (warehouse == null
            || warehouse.getCode() == null
            || warehouse.getName() == null
            || warehouse.getAddress() == null
            || warehouse.getStatus() == null) {

        throw new IllegalArgumentException(
                "Required warehouse fields are missing");
    }

    String code = toSqlString(warehouse.getCode());
    String name = toSqlString(warehouse.getName());
    String address = toSqlString(warehouse.getAddress());
    String city = toSqlString(warehouse.getCity());
    String state = toSqlString(warehouse.getState());
    String country = toSqlString(warehouse.getCountry());

    String status = warehouse.getStatus().name();

    try {

        if (warehouse.getId() == null) {

            // INSERT new warehouse
            String query =
                    "INSERT INTO warehouses "
                    + "(code, name, address, city, state, country, status) "
                    + "VALUES ("
                    + code + ", "
                    + name + ", "
                    + address + ", "
                    + city + ", "
                    + state + ", "
                    + country + ", "
                    + "'" + status + "')";

            ZCQL.getInstance().executeQuery(query);

            // Retrieve the inserted warehouse.
            // Code should have a unique constraint in Catalyst.
            return findByCode(warehouse.getCode())
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Warehouse inserted but could "
                                    + "not be retrieved: "
                                    + warehouse.getCode()));

        } else {

            // UPDATE existing warehouse
            String query =
                    "UPDATE warehouses SET "
                    + "code = " + code + ", "
                    + "name = " + name + ", "
                    + "address = " + address + ", "
                    + "city = " + city + ", "
                    + "state = " + state + ", "
                    + "country = " + country + ", "
                    + "status = '" + status + "' "
                    + "WHERE ROWID = " + warehouse.getId();

            ZCQL.getInstance().executeQuery(query);

            return findById(warehouse.getId())
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Warehouse update could not "
                                    + "be verified: "
                                    + warehouse.getId()));
        }

    } catch (Exception e) {
        throw new RuntimeException(
                "Error saving warehouse in Catalyst", e);
    }
}

// Convert Java String into ZCQL string or NULL
private String toSqlString(String value) {

    if (value == null) {
        return "NULL";
    }

    return "'" + escapeString(value) + "'";
}


}
