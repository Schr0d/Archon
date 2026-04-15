package com.archon.java.fixtures;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ReportService {
    private final OrderService orderService;
    private final UserRepository userRepo;

    @Autowired
    public ReportService(OrderService orderService, UserRepository userRepo) {
        this.orderService = orderService;
        this.userRepo = userRepo;
    }
}
