package com.example.Stock.repository;

import com.example.Stock.domain.Portfolio;
import com.example.Stock.domain.TradeOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeOrderRepository extends JpaRepository<TradeOrder, Long> {

    List<TradeOrder> findByPortfolioOrderByExecutedAtDesc(Portfolio portfolio);

    long countByPortfolioAndSide(Portfolio portfolio, TradeOrder.Side side);
}
