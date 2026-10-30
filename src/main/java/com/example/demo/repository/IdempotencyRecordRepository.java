package com.example.demo.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.example.demo.entity.IdempotencyRecord;
import com.zc.component.object.ZCRowObject;
import com.zc.component.zcql.ZCQL;

@Repository
public class IdempotencyRecordRepository {

    public Optional<IdempotencyRecord> findByIdempotencyKey(
            String idempotencyKey) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }

        String safeKey = idempotencyKey.replace("'", "''");

        String query = "SELECT * FROM idempotency_records "
                + "WHERE idempotency_key = '" + safeKey + "' LIMIT 1";

        try {

            ArrayList<ZCRowObject> rows =
                    ZCQL.getInstance().executeQuery(query);

            if (rows == null || rows.isEmpty()) {
                return Optional.empty();
            }

            ZCRowObject row = rows.get(0);

            IdempotencyRecord record = IdempotencyRecord.builder()
                    .id(toLong(row.get("id")))
                    .idempotencyKey((String) row.get("idempotency_key"))
                    .endpoint((String) row.get("endpoint"))
                    .requestHash((String) row.get("request_hash"))
                    .responsePayload((String) row.get("response_payload"))
                    .statusCode(toInteger(row.get("status_code")))
                    .createdAt(toDateTime(row.get("created_at")))
                    .build();

            return Optional.of(record);

        } catch (Exception e) {
            throw new RuntimeException(
                    "Error fetching idempotency record from Catalyst", e);
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        return Long.valueOf(value.toString());
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        return Integer.valueOf(value.toString());
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
}