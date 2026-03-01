package com.cloudcart.order.model;

import java.util.List;

public class StockCompensationEvent {
    private String orderId;
    private String userId;
    private List<OrderItem> items;

    public StockCompensationEvent() {}

    public StockCompensationEvent(String orderId, String userId, List<OrderItem> items) {
        this.orderId = orderId;
        this.userId = userId;
        this.items = items;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }
}
