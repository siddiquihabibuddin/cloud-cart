package com.cloudcart.shipment.model;

public class OrderShippedEvent {

    private String orderId;
    private String userId;

    public OrderShippedEvent() {}

    public OrderShippedEvent(String orderId, String userId) {
        this.orderId = orderId;
        this.userId = userId;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}
