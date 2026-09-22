package com.menusaas.orders.repository;

import com.menusaas.orders.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

    List<OrderStatusHistory> findByOrderIdOrderByChangedAtAsc(Long orderId);

    List<OrderStatusHistory> findByOrderIdInOrderByChangedAtAsc(Collection<Long> orderIds);
}