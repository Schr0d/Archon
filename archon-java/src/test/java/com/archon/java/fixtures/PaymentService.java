package com.archon.java.fixtures;

import org.springframework.stereotype.Service;

@Service
public class PaymentService {
    private final OrderService orderService;

    public PaymentService(OrderService orderService) {
        this.orderService = orderService;
    }
}
