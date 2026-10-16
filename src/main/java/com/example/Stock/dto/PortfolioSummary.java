package com.example.Stock.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 모의투자 계좌 현황.
 *
 * @param totalValue       총 평가자산 (예수금 + 주식 평가금액)
 * @param totalReturnPct   초기 자본 대비 수익률(%)
 * @param unrealizedProfit 보유 종목의 평가손익 합계
 * @param realizedProfit   매도로 확정된 손익의 누적 합계
 */
public record PortfolioSummary(
        LocalDateTime openedAt,
        Double initialCapital,
        Double cash,
        Double stockValue,
        Double totalValue,
        Double totalReturnPct,
        Double unrealizedProfit,
        Double realizedProfit,
        List<HoldingView> holdings
) {}
