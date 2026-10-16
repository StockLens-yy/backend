package com.example.Stock.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 모의투자 계좌. 사용자당 하나이며 예수금(현금)을 보유한다. */
@Entity
@Table(name = "portfolio")
@Getter @Setter
@NoArgsConstructor
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    /** 예수금 */
    @Column(nullable = false)
    private Double cash;

    /** 계좌 개설 시 넣은 금액. 수익률 계산 기준이 된다. */
    @Column(nullable = false)
    private Double initialCapital;

    /** 매도로 확정된 손익의 누적 합계 */
    @Column(nullable = false)
    private Double realizedProfit = 0d;

    @Column(nullable = false)
    private LocalDateTime openedAt;

    public Portfolio(User user, double initialCapital) {
        this.user = user;
        this.cash = initialCapital;
        this.initialCapital = initialCapital;
        this.realizedProfit = 0d;
        this.openedAt = LocalDateTime.now();
    }
}
