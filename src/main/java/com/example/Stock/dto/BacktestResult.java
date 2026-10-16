package com.example.Stock.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 백테스팅 결과.
 *
 * @param buyAndHoldReturnPct 같은 구간을 처음부터 끝까지 보유했을 때의 수익률(%).
 *                            전략이 단순 보유보다 나았는지 판단하는 기준선이다.
 *                            '계속 보유'가 전제이므로 매도 비용은 빼지 않고 마지막 종가로 평가한다.
 * @param buyAndHoldCurve     위 기준선의 일별 자산 곡선. 전략 곡선과 겹쳐 그리기 위한 것이다.
 * @param maxDrawdownPct      최대낙폭(%). 자산 곡선의 고점 대비 최대 하락률.
 * @param winRate             수익으로 끝난 거래의 비율(%). 완료된 거래가 없으면 0.
 * @param openPosition        구간 종료 시점에 아직 주식을 보유 중이면 true.
 *                            이 경우 finalEquity 에는 미실현 평가액이 포함된다.
 */
public record BacktestResult(
        String symbol,
        LocalDate from,
        LocalDate to,
        int shortPeriod,
        int longPeriod,
        Double initialCapital,
        Double finalEquity,
        Double totalReturnPct,
        Double buyAndHoldReturnPct,
        Double maxDrawdownPct,
        Double winRate,
        int tradeCount,
        Double avgHoldingDays,
        boolean openPosition,
        List<Trade> trades,
        List<EquityPoint> equityCurve,
        List<EquityPoint> buyAndHoldCurve
) {}
