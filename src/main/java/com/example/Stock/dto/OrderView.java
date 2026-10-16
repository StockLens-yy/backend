package com.example.Stock.dto;

import com.example.Stock.domain.TradeOrder;

import java.time.LocalDateTime;

/**
 * 체결 내역.
 *
 * @param settlementAmount 매수는 실제 지출액, 매도는 실제 수령액
 * @param realizedProfit   매도로 확정된 손익. 매수는 null
 */
public record OrderView(
        Long id,
        String symbol,
        String name,
        TradeOrder.Side side,
        Double price,
        long quantity,
        Double fee,
        Double tax,
        Double settlementAmount,
        Double realizedProfit,
        LocalDateTime executedAt
) {
    public static OrderView from(TradeOrder o) {
        return new OrderView(o.getId(), o.getSymbol(), o.getName(), o.getSide(),
                o.getPrice(), o.getQuantity(), o.getFee(), o.getTax(),
                o.getSettlementAmount(), o.getRealizedProfit(), o.getExecutedAt());
    }
}
