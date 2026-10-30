
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
import com.example.demo.entity.User;
import com.example.demo.entity.User.Role;
import com.example.demo.util.CatalystDateTimes;

import com.zc.component.object.ZCRowObject;
import com.zc.component.zcql.ZCQL;

@Repository
public class UserRepository {

    private static final String TABLE_NAME = "users";

    private static final int BATCH_SIZE = 300;

    // 1. Find user using username
    public Optional<User> findByUsername(String username) {

        if (username == null || username.trim().isEmpty()) {
            return Optional.empty();
        }

        String safeUsername = escapeString(username.trim());

        String query = "SELECT * FROM " + TABLE_NAME
                + " WHERE username = '" + safeUsername + "'"
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
                    "Error finding user by username from Catalyst", e);
        }
    }

    // 2. Find user using email
    public Optional<User> findByEmail(String email) {

        if (email == null || email.trim().isEmpty()) {
            return Optional.empty();
        }

        String safeEmail = escapeString(email.trim());

        String query = "SELECT * FROM " + TABLE_NAME
                + " WHERE email = '" + safeEmail + "'"
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
                    "Error finding user by email from Catalyst", e);
        }
    }

    // 3. Check whether username exists
    public Boolean existsByUsername(String username) {

        return findByUsername(username).isPresent();
    }

    // 4. Check whether email exists
    public Boolean existsByEmail(String email) {

        return findByEmail(email).isPresent();
    }

    // 5. Find all users with pagination
    public Page<User> findAll(@RequestParam(defaultValue = "0") int page,
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

            String baseQuery = "SELECT * FROM " + TABLE_NAME
                    + buildOrderBy(pageable.getSort());

            long totalRecords = countAllUsers();

            if (pageable.isUnpaged()) {

                List<User> allUsers = fetchUsers(
                        baseQuery, 0, Long.MAX_VALUE);

                return new PageImpl<>(allUsers);
            }

            List<User> pageContent = fetchUsers(
                    baseQuery,
                    pageable.getOffset(),
                    pageable.getPageSize());

            return new PageImpl<>(
                    pageContent,
                    pageable,
                    totalRecords);

        } catch (Exception e) {

            throw new RuntimeException(
                    "Error fetching users from Catalyst", e);
        }
    }

    // Fetch users in batches of 300
    private List<User> fetchUsers(
            String baseQuery,
            long offset,
            long requestedRecords) throws Exception {

        List<User> users = new ArrayList<>();

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
                users.add(mapRow(row));
            }

            fetchedRecords += rows.size();

            if (rows.size() < batchSize) {
                break;
            }
        }

        return users;
    }

    // Count all user records
    private long countAllUsers() throws Exception {

        long total = 0;
        int offset = 0;

        while (true) {

            String query = "SELECT ROWID FROM " + TABLE_NAME
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

    // Build ORDER BY using supported properties
    private String buildOrderBy(Sort sort) {

        List<String> orderExpressions = new ArrayList<>();

        if (sort != null && sort.isSorted()) {

            for (Sort.Order order : sort) {

                String column = getSortColumn(order.getProperty());

                if (column == null) {
                    continue;
                }

                orderExpressions.add(
                        column
                        + (order.isAscending() ? " ASC" : " DESC"));
            }
        }

        if (orderExpressions.isEmpty()) {
            orderExpressions.add("ROWID ASC");
        }

        return " ORDER BY " + String.join(", ", orderExpressions);
    }

    // Map Java properties to Catalyst columns
    private String getSortColumn(String property) {

        switch (property) {

            case "id":
                return "ROWID";

            case "username":
                return "username";

            case "email":
                return "email";

            case "fullName":
                return "full_name";

            case "role":
                return "role";

            case "isActive":
                return "is_active";

            case "createdAt":
                return "created_at";

            case "updatedAt":
                return "updated_at";

            default:
                return null;
        }
    }

    // Convert Catalyst row into User object
    private User mapRow(ZCRowObject row) {

        String roleValue = toStringValue(row.get("role"));

        Role role = roleValue == null
                ? null
                : Role.valueOf(roleValue.toUpperCase(Locale.ROOT));

        return User.builder()
                .id(toLong(row.get("ROWID")))
                .username(toStringValue(row.get("username")))
                .email(toStringValue(row.get("email")))
                .passwordHash(toStringValue(row.get("password_hash")))
                .fullName(toStringValue(row.get("full_name")))
                .role(role)
                .isActive(toBoolean(row.get("is_active")))
                .createdAt(toDateTime(row.get("created_at")))
                .updatedAt(toDateTime(row.get("updated_at")))
                .build();
    }

    // Escape single quotes
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

    // Convert Catalyst date-time to LocalDateTime
    private LocalDateTime toDateTime(Object value) {
        return CatalystDateTimes.parse(value);
    }
    public Optional<User> findById(Long id) {

    if (id == null || id <= 0) {
        throw new IllegalArgumentException("Invalid User ID");
    }

    String query = "SELECT * FROM users "
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
                "Error finding user by ID: " + id, e);
    }
}

public void updatePasswordHash(Long userId, String passwordHash) {

    if (userId == null || userId <= 0
            || passwordHash == null
            || passwordHash.isBlank()) {

        throw new IllegalArgumentException(
                "Valid User ID and password hash are required");
    }

    String safePasswordHash =
            passwordHash.replace("'", "''");

    String query = "UPDATE users SET "
            + "password_hash = '" + safePasswordHash + "' "
            + "WHERE ROWID = " + userId;

    try {

        ZCQL.getInstance().executeQuery(query);

    } catch (Exception e) {

        throw new RuntimeException(
                "Error updating admin password in Catalyst", e);
    }
}

}
