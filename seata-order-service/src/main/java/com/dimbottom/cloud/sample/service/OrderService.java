package com.dimbottom.cloud.sample.service;


import com.dimbottom.cloud.sample.domain.Order;

public interface OrderService {

    /**
     * 创建订单
     */
    void create(Order order);
}
