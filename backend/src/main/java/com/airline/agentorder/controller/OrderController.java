package com.airline.agentorder.controller;

import com.airline.agentorder.dto.ApiResponse;
import com.airline.agentorder.model.Order;
import com.airline.agentorder.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ApiResponse<List<Order>> listOrders() {
        return ApiResponse.success(orderService.listOrders());
    }
}
