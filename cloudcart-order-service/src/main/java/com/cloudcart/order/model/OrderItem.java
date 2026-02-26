package com.cloudcart.order.model;

public class OrderItem {
    private String productId;
    private int quantity;
    private double price;

    public OrderItem() {}

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
}
