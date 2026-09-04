package com.example.Stock.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 체결 내역. 주문 즉시 현재가로 전량 체결되므로 미체결 상태는 없다. */
@Entity
@Table(name = "trade_order")   // order 는 예약어라 테이블명을 따로 지정한다
@Getter @Setter
@NoArgsConstructor
public class TradeOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(nullable = false, length = 20)
    private String symbol;

    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Side side;

    /** 체결 단가 */
    @Column(nullable = false)
    private Double price;

    @Column(nullable = false)
    private Long quantity;

    /** 매매 수수료 */
    @Column(nullable = false)
    private Double fee;

    /** 증권거래세 (매도 시에만 부과) */
    @Column(nullable = false)
    private Double tax;

    /** 매수는 실제 지출액, 매도는 실제 수령액 */
    @Column(nullable = false)
    private Double settlementAmount;

    /** 매도 시 확정된 손익. 매수는 null */
    private Double realizedProfit;

    @Column(nullable = false)
    private LocalDateTime executedAt;

    public enum Side { BUY, SELL }
}
