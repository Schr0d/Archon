package com.archon.java.fixtures;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

@Controller
public class OrderController {
    @Autowired
    private OrderService orderService;

    @Autowired
    private UserRepository userRepo;
}
