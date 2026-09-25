package com.menusaas.cash.repository;

import com.menusaas.cash.entity.CashClosing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface CashClosingRepository extends JpaRepository<CashClosing, Long> {

    Optional<CashClosing> findByRestaurantIdAndBusinessDate(Long restaurantId, LocalDate businessDate);

    Page<CashClosing> findByRestaurantIdOrderByBusinessDateDesc(Long restaurantId, Pageable pageable);
}
