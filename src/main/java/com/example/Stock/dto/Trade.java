package com.example.Stock.dto;

import java.time.LocalDate;

/**
 * 완료된 한 건의 매매(매수 → 매도). 금액은 모두 수수료·세금이 반영된 실제 정산 기준이다.
 *
 * @param profit    매도 실수령액 - 매수 실지출액
 * @param returnPct 매수 실지출액 대비 수익률(%)
 */
public record Trade(
        LocalDate buyDate,
        Double buyPrice,
        LocalDate sellDate,
        Double sellPrice,
        long shares,
        Double profit,
        Double returnPct,
        long holdingDays
) {
    public boolean isWin() {
        return profit != null && profit > 0;
    }
}
