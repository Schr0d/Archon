package com.archon.java.fixtures;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    @Resource
    private OrderService orderService;
}
