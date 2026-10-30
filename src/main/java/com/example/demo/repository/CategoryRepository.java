package com.example.demo.repository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.example.demo.entity.Category;
import com.zc.component.object.ZCRowObject;
import com.zc.component.zcql.ZCQL;

@Repository
public class CategoryRepository {

    public Optional<Category> findByCode(String code) throws Exception {

        if (code == null || code.isBlank()) {
            return Optional.empty();
        }

        // Escape single quotes in the input
        String safeCode = code.replace("'", "''");

        String query =
                "SELECT * FROM categories WHERE code = '" + safeCode + "' LIMIT 1";

        ArrayList<ZCRowObject> rows = ZCQL.getInstance().executeQuery(query);

        if (rows == null || rows.isEmpty()) {
            return Optional.empty();
        }

        ZCRowObject row = rows.get(0);

        Category category = Category.builder()
        .id(toLong(row.get("ROWID")))
        .code(toStringValue(row.get("code")))
        .name(toStringValue(row.get("name")))
        .description(toStringValue(row.get("description")))
        .createdAt(toDateTime(row.get("created_at")))
        .updatedAt(toDateTime(row.get("updated_at")))
        .build();

        return Optional.of(category);
    }

    public Boolean existsByCode(String code) throws Exception {
        return findByCode(code).isPresent();
    }

    private Long toLong(Object value) {

        if (value == null) {
            return null;
        }

        if (value instanceof Number) {
            return ((Number) value).longValue();
        }

        return Long.parseLong(value.toString());
    }

    private LocalDateTime toDateTime(Object value) {

        if (value == null) {
            return null;
        }

        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }

        String date = value.toString();

        try {
            return LocalDateTime.parse(date);
        } catch (Exception ignored) {
            // Try Catalyst datetime format below.
        }

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS");

        return LocalDateTime.parse(date, formatter);
    }
    public Optional<Category> findById(Long id) {

    if (id == null || id <= 0) {
        throw new IllegalArgumentException("Invalid Category ID");
    }

    String query = "SELECT * FROM categories "
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
                "Error finding category by ROWID: " + id, e);
    }
}

private Category mapRow(ZCRowObject row) {

    return Category.builder()
            .id(toLong(row.get("ROWID")))
            .code(toStringValue(row.get("code")))
            .name(toStringValue(row.get("name")))
            .description(toStringValue(row.get("description")))
            .createdAt(toDateTime(row.get("created_at")))
            .updatedAt(toDateTime(row.get("updated_at")))
            .build();
}
private String toStringValue(Object value) {
    return value == null
            ? null
            : value.toString();
}
}