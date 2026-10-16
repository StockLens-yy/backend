package com.example.Stock.dto;

/**
 * 보유 종목 현황.
 *
 * @param avgBuyPrice      매수 수수료가 포함된 평균 매입 단가
 * @param evalAmount       현재가 기준 평가금액 (현재가 × 수량)
 * @param unrealizedProfit 평가손익 (평가금액 - 매입원가)
 */
public record HoldingView(
        String symbol,
        String name,
        long quantity,
        Double avgBuyPrice,
        Double currentPrice,
        Double evalAmount,
        Double unrealizedProfit,
        Double returnPct
) {}
