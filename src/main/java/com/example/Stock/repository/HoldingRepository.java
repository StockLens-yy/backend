package com.example.Stock.repository;

import com.example.Stock.domain.Holding;
import com.example.Stock.domain.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    List<Holding> findByPortfolio(Portfolio portfolio);

    Optional<Holding> findByPortfolioAndSymbol(Portfolio portfolio, String symbol);
}
