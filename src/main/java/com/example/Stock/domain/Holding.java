package com.example.Stock.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 보유 종목.
 * <p>
 * {@code avgBuyPrice} 는 <b>매수 수수료를 포함한</b> 평균 단가다.
 * (총 매입비용 / 총 수량) 이므로 실현손익을 계산할 때 수수료가 자동으로 반영된다.
 */
@Entity
@Table(
        name = "holding",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_holding_portfolio_symbol",
                columnNames = {"portfolio_id", "symbol"})
)
@Getter @Setter
@NoArgsConstructor
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(nullable = false, length = 20)
    private String symbol;

    private String name;

    @Column(nullable = false)
    private Long quantity;

    @Column(name = "avg_buy_price", nullable = false)
    private Double avgBuyPrice;

    public Holding(Portfolio portfolio, String symbol, String name, long quantity, double avgBuyPrice) {
        this.portfolio = portfolio;
        this.symbol = symbol;
        this.name = name;
        this.quantity = quantity;
        this.avgBuyPrice = avgBuyPrice;
    }
}
