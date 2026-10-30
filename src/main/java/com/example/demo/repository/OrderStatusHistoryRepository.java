
package com.example.demo.repository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.example.demo.entity.OrderStatusHistory;
import com.example.demo.entity.SalesOrder;
import com.example.demo.entity.User;
import com.example.demo.util.CatalystDateTimes;

import com.zc.component.object.ZCRowObject;
import com.zc.component.zcql.ZCQL;

@Repository
public class OrderStatusHistoryRepository {

    private static final String TABLE_NAME = "order_status_history";

    private static final int BATCH_SIZE = 300;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS");

    // 1. Save Order Status History
    public OrderStatusHistory save(OrderStatusHistory history) {

        if (history == null
                || history.getSalesOrder() == null
                || history.getSalesOrder().getId() == null
                || history.getChangedBy() == null
                || history.getChangedBy().getId() == null
                || history.getFromStatus() == null
                || history.getToStatus() == null) {

            throw new IllegalArgumentException(
                    "Required order status history fields are missing");
        }

        if (history.getCreatedAt() == null) {
            history.setCreatedAt(LocalDateTime.now());
        }

        Long salesOrderId = history.getSalesOrder().getId();
        Long userId = history.getChangedBy().getId();

        String fromStatus = escapeString(history.getFromStatus());
        String toStatus = escapeString(history.getToStatus());

        String changeReason = history.getChangeReason() == null
                ? "NULL"
                : "'" + escapeString(history.getChangeReason()) + "'";

        String createdAt = DATE_FORMAT.format(history.getCreatedAt());

        String query =
                "INSERT INTO " + TABLE_NAME
                + " (sales_order_id, from_status, to_status, "
                + "changed_by_user_id, change_reason, created_at) "
                + "VALUES ("
                + salesOrderId + ", "
                + "'" + fromStatus + "', "
                + "'" + toStatus + "', "
                + userId + ", "
                + changeReason + ", "
                + "'" + createdAt + "')";

        try {

            ZCQL.getInstance().executeQuery(query);

            return history;

        } catch (Exception e) {

            throw new RuntimeException(
                    "Error saving order status history in Catalyst", e);
        }
    }

    // 2. Find status history by Sales Order ID
    public List<OrderStatusHistory> findBySalesOrderIdOrderByCreatedAtDesc(
            Long salesOrderId) {

        if (salesOrderId == null || salesOrderId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid Sales Order ID");
        }

        List<OrderStatusHistory> historyList = new ArrayList<>();

        try {

            int offset = 0;

            while (true) {

                String query = "SELECT * FROM " + TABLE_NAME
                        + " WHERE sales_order_id = " + salesOrderId
                        + " ORDER BY created_at DESC"
                        + " LIMIT " + offset + "," + BATCH_SIZE;

                ArrayList<ZCRowObject> rows =
                        ZCQL.getInstance().executeQuery(query);

                if (rows == null || rows.isEmpty()) {
                    break;
                }

                for (ZCRowObject row : rows) {
                    historyList.add(mapRow(row));
                }

                if (rows.size() < BATCH_SIZE) {
                    break;
                }

                offset += rows.size();
            }

            return historyList;

        } catch (Exception e) {

            throw new RuntimeException(
                    "Error fetching order status history from Catalyst", e);
        }
    }

    // 3. Convert Catalyst row into OrderStatusHistory
    private OrderStatusHistory mapRow(ZCRowObject row) {

        Long salesOrderId =
                toLong(row.get("sales_order_id"));

        Long changedByUserId =
                toLong(row.get("changed_by_user_id"));

        SalesOrder salesOrder = null;

        if (salesOrderId != null) {
            salesOrder = new SalesOrder();
            salesOrder.setId(salesOrderId);
        }

        User changedBy = null;

        if (changedByUserId != null) {
            changedBy = new User();
            changedBy.setId(changedByUserId);
        }

        return OrderStatusHistory.builder()
                .id(toLong(row.get("ROWID")))
                .salesOrder(salesOrder)
                .fromStatus(toStringValue(row.get("from_status")))
                .toStatus(toStringValue(row.get("to_status")))
                .changedBy(changedBy)
                .changeReason(toStringValue(row.get("change_reason")))
                .createdAt(toDateTime(row.get("created_at")))
                .build();
    }

    // 4. Helper methods
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

    private LocalDateTime toDateTime(Object value) {
        return CatalystDateTimes.parse(value);
    }
}
