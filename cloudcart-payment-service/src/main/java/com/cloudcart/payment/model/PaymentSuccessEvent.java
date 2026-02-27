package com.cloudcart.payment.model;

import java.util.List;

public class PaymentSuccessEvent {
    private String orderId;
    private String userId;
    private List<OrderItem> items;
    private double totalAmount;

    public PaymentSuccessEvent() {}

    public PaymentSuccessEvent(String orderId, String userId, List<OrderItem> items, double totalAmount) {
        this.orderId = orderId;
        this.userId = userId;
        this.items = items;
        this.totalAmount = totalAmount;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
}
