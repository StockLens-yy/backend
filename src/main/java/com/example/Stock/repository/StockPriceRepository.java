package com.example.Stock.repository;

import com.example.Stock.domain.StockPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StockPriceRepository extends JpaRepository<StockPrice, Long> {

    List<StockPrice> findBySymbolOrderByTradeDateAsc(String symbol);

    List<StockPrice> findBySymbolAndTradeDateBetweenOrderByTradeDateAsc(
            String symbol, LocalDate from, LocalDate to);

    // 수집 시 중복 확인용 - 구간 내 이미 저장된 행을 한 번에 읽는다
    List<StockPrice> findBySymbolAndTradeDateBetween(String symbol, LocalDate from, LocalDate to);

    Optional<StockPrice> findTopBySymbolOrderByTradeDateDesc(String symbol);

    long countBySymbol(String symbol);

    @Query("select count(distinct p.symbol) from StockPrice p")
    long countDistinctSymbols();
}
