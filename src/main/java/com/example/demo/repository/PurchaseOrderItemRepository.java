
package com.example.demo.repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.example.demo.entity.PurchaseOrder;
import com.example.demo.entity.PurchaseOrderItem;
import com.example.demo.entity.Product;

import com.zc.component.object.ZCRowObject;
import com.zc.component.zcql.ZCQL;

@Repository
public class PurchaseOrderItemRepository {

    private static final String TABLE_NAME = "purchase_order_items";

    private static final int BATCH_SIZE = 300;

    // Find purchase order items using Purchase Order ID
    public List<PurchaseOrderItem> findByPurchaseOrderId(
            Long purchaseOrderId) {

        if (purchaseOrderId == null || purchaseOrderId <= 0) {
            throw new IllegalArgumentException(
                    "Purchase Order ID must be a valid positive number");
        }

        List<PurchaseOrderItem> items = new ArrayList<>();

        try {

            int offset = 0;

            while (true) {

                String query = "SELECT * FROM " + TABLE_NAME
                        + " WHERE purchase_order_id = " + purchaseOrderId
                        + " LIMIT " + offset + "," + BATCH_SIZE;

                ArrayList<ZCRowObject> rows =
                        ZCQL.getInstance().executeQuery(query);

                if (rows == null || rows.isEmpty()) {
                    break;
                }

                for (ZCRowObject row : rows) {
                    items.add(mapRow(row));
                }

                if (rows.size() < BATCH_SIZE) {
                    break;
                }

                offset += BATCH_SIZE;
            }

            return items;

        } catch (Exception e) {

            throw new RuntimeException(
                    "Error fetching purchase order items from Catalyst", e);
        }
    }

    // Convert Catalyst row into PurchaseOrderItem
    private PurchaseOrderItem mapRow(ZCRowObject row) {

        Long purchaseOrderId =
                toLong(row.get("purchase_order_id"));

        Long productId =
                toLong(row.get("product_id"));

        // Map PurchaseOrder reference
        PurchaseOrder purchaseOrder = null;

        if (purchaseOrderId != null) {
            purchaseOrder = new PurchaseOrder();
            purchaseOrder.setId(purchaseOrderId);
        }

        // Map Product reference
        Product product = null;

        if (productId != null) {
            product = new Product();
            product.setId(productId);
        }

        return PurchaseOrderItem.builder()
                .id(toLong(row.get("ROWID")))
                .purchaseOrder(purchaseOrder)
                .product(product)
                .orderedQty(toInteger(row.get("ordered_qty")))
                .receivedQty(toInteger(row.get("received_qty")))
                .unitCost(toBigDecimal(row.get("unit_cost")))
                .build();
    }

    // Data conversion methods
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

    private BigDecimal toBigDecimal(Object value) {

        return value == null
                ? null
                : new BigDecimal(value.toString());
    }
    
public PurchaseOrderItem save(PurchaseOrderItem item) {

    if (item == null
            || item.getPurchaseOrder() == null
            || item.getPurchaseOrder().getId() == null
            || item.getProduct() == null
            || item.getProduct().getId() == null) {

        throw new IllegalArgumentException(
                "Purchase Order and Product IDs are required");
    }

    int orderedQty = item.getOrderedQty() == null
            ? 0 : item.getOrderedQty();

    int receivedQty = item.getReceivedQty() == null
            ? 0 : item.getReceivedQty();

    if (orderedQty <= 0 || receivedQty < 0
            || receivedQty > orderedQty
            || item.getUnitCost() == null
            || item.getUnitCost().signum() < 0) {

        throw new IllegalArgumentException(
                "Invalid purchase order item quantities or unit cost");
    }

    Long purchaseOrderId = item.getPurchaseOrder().getId();
    Long productId = item.getProduct().getId();

    try {

        if (item.getId() == null) {

            // Insert new purchase order item
            String query =
                    "INSERT INTO purchase_order_items "
                    + "(purchase_order_id, product_id, ordered_qty, "
                    + "received_qty, unit_cost) "
                    + "VALUES ("
                    + purchaseOrderId + ", "
                    + productId + ", "
                    + orderedQty + ", "
                    + receivedQty + ", "
                    + item.getUnitCost().toPlainString() + ")";

            ZCQL.getInstance().executeQuery(query);

        } else {

            // Update existing purchase order item
            String query =
                    "UPDATE purchase_order_items SET "
                    + "purchase_order_id = " + purchaseOrderId + ", "
                    + "product_id = " + productId + ", "
                    + "ordered_qty = " + orderedQty + ", "
                    + "received_qty = " + receivedQty + ", "
                    + "unit_cost = " + item.getUnitCost().toPlainString()
                    + " WHERE ROWID = " + item.getId();

            ZCQL.getInstance().executeQuery(query);
        }

        return item;

    } catch (Exception e) {
        throw new RuntimeException(
                "Error saving purchase order item in Catalyst", e);
    }
}

}
