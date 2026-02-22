package com.restmvc.beer_store.enums;

public enum OrderStatus {
    NEW,          // order created at checkout, awaiting payment
    PAID,         // payment confirmed
    PROCESSING,   // warehouse is preparing the order
    SHIPPED,      // order dispatched, tracking number assigned
    DELIVERED,    // customer received the order
    CANCELLED     // order cancelled (only possible before SHIPPED)
}