package com.example.Stock.repository;

import com.example.Stock.domain.StockMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockMasterRepository extends JpaRepository<StockMaster, Long> {
    Optional<StockMaster> findBySymbol(String symbol);

    List<StockMaster> findBySymbolStartingWithOrNameContainingIgnoreCase(String symbol, String name);
}
