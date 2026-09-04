package com.example.Stock.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 종목별 일봉 시계열 데이터.
 * Stock 엔티티가 종목당 1행(최신가)만 유지하는 것과 달리, 날짜별 이력을 누적한다.
 */
@Entity
@Table(
        name = "stock_price",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stock_price_symbol_date",
                columnNames = {"symbol", "trade_date"}
        ),
        indexes = @Index(name = "idx_stock_price_symbol_date", columnList = "symbol, trade_date")
)
@Getter @Setter
@NoArgsConstructor
public class StockPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "open_price")
    private Double open;

    @Column(name = "high_price")
    private Double high;

    @Column(name = "low_price")
    private Double low;

    @Column(name = "close_price")
    private Double close;

    private Long volume;

    public StockPrice(String symbol, LocalDate tradeDate) {
        this.symbol = symbol;
        this.tradeDate = tradeDate;
    }
}
