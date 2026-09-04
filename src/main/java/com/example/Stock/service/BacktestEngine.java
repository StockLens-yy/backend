package com.example.Stock.service;

import com.example.Stock.dto.BacktestResult;
import com.example.Stock.dto.CrossSignal;
import com.example.Stock.dto.EquityPoint;
import com.example.Stock.dto.Trade;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 이동평균 교차 전략 백테스팅 엔진.
 * <p>
 * 골든크로스에 전량 매수, 데드크로스에 전량 매도한다. 외부 의존이 없는 순수 계산이라
 * 단위 테스트로 검증한다.
 * <p>
 * <b>체결 시점</b>: 교차는 당일 종가가 확정된 뒤에야 알 수 있으므로, 신호 다음 거래일의
 * 종가로 체결한다. 신호가 난 당일 종가로 체결하면 실제로는 알 수 없는 정보를 쓰는
 * 미래참조(look-ahead bias)가 되어 성과가 부풀려진다.
 */
public final class BacktestEngine {

    private BacktestEngine() {
    }

    /**
     * @param simulateFrom   시뮬레이션 시작일. 이 날짜 앞의 데이터는 이동평균 계산에만 쓰이고
     *                       매매·자산곡선에서는 제외된다. null 이면 전체 구간을 시뮬레이션한다.
     * @param initialCapital 초기 자본
     * @param feeRate        매매 수수료율 (매수·매도 양방향 적용)
     * @param taxRate        증권거래세율 (매도 시에만 적용)
     */
    public static BacktestResult run(String symbol,
                                     List<LocalDate> dates,
                                     List<Double> closes,
                                     LocalDate simulateFrom,
                                     int shortPeriod,
                                     int longPeriod,
                                     double initialCapital,
                                     double feeRate,
                                     double taxRate) {
        validate(dates, closes, shortPeriod, longPeriod, initialCapital, feeRate, taxRate);

        int n = dates.size();
        if (n == 0) {
            return new BacktestResult(symbol, null, null, shortPeriod, longPeriod,
                    initialCapital, initialCapital, 0.0, 0.0, 0.0, 0.0, 0, 0.0, false,
                    List.of(), List.of(), List.of());
        }

        Map<LocalDate, CrossSignal.Type> signalByDate = new HashMap<>();
        for (CrossSignal s : IndicatorCalculator.detectCrosses(
                dates,
                IndicatorCalculator.sma(closes, shortPeriod),
                IndicatorCalculator.sma(closes, longPeriod))) {
            signalByDate.put(s.date(), s.type());
        }

        // 워밍업 구간을 건너뛴다. 지표는 전체 데이터로 계산하되 매매는 여기서부터 시작한다.
        int startIndex = 0;
        if (simulateFrom != null) {
            while (startIndex < n && dates.get(startIndex).isBefore(simulateFrom)) startIndex++;
            if (startIndex == n) {
                return new BacktestResult(symbol, simulateFrom, dates.get(n - 1), shortPeriod, longPeriod,
                        initialCapital, initialCapital, 0.0, 0.0, 0.0, 0.0, 0, 0.0, false,
                        List.of(), List.of(), List.of());
            }
        }

        double cash = initialCapital;
        long shares = 0;

        // 보유 중인 포지션 정보
        LocalDate buyDate = null;
        double buyPrice = 0;
        double buyCost = 0;

        // 전일 신호 - 당일 종가로 체결한다. 시작일 직전에 난 신호도 첫날에 반영된다.
        CrossSignal.Type pending = startIndex > 0 ? signalByDate.get(dates.get(startIndex - 1)) : null;
        List<Trade> trades = new ArrayList<>();
        List<EquityPoint> equityCurve = new ArrayList<>(n - startIndex);

        for (int i = startIndex; i < n; i++) {
            double close = closes.get(i);

            if (pending == CrossSignal.Type.GOLDEN && shares == 0) {
                long buyable = (long) Math.floor(cash / (close * (1 + feeRate)));
                if (buyable > 0) {
                    shares = buyable;
                    buyCost = shares * close * (1 + feeRate);
                    cash -= buyCost;
                    buyDate = dates.get(i);
                    buyPrice = close;
                }
            } else if (pending == CrossSignal.Type.DEAD && shares > 0) {
                double proceeds = shares * close * (1 - feeRate - taxRate);
                cash += proceeds;

                double profit = proceeds - buyCost;
                trades.add(new Trade(
                        buyDate, buyPrice, dates.get(i), close, shares,
                        profit, profit / buyCost * 100,
                        ChronoUnit.DAYS.between(buyDate, dates.get(i))));

                shares = 0;
                buyDate = null;
                buyCost = 0;
            }

            pending = signalByDate.get(dates.get(i));
            equityCurve.add(new EquityPoint(dates.get(i), cash + shares * close));
        }

        double finalEquity = equityCurve.get(equityCurve.size() - 1).equity();
        List<EquityPoint> buyAndHoldCurve = buyAndHoldCurve(
                dates, closes, startIndex, initialCapital, feeRate);
        double buyAndHoldFinal = buyAndHoldCurve.get(buyAndHoldCurve.size() - 1).equity();

        int wins = 0;
        long holdingDaysSum = 0;
        for (Trade t : trades) {
            if (t.isWin()) wins++;
            holdingDaysSum += t.holdingDays();
        }

        return new BacktestResult(
                symbol, dates.get(startIndex), dates.get(n - 1), shortPeriod, longPeriod,
                initialCapital,
                finalEquity,
                (finalEquity / initialCapital - 1) * 100,
                (buyAndHoldFinal / initialCapital - 1) * 100,
                maxDrawdownPct(equityCurve),
                trades.isEmpty() ? 0.0 : (double) wins / trades.size() * 100,
                trades.size(),
                trades.isEmpty() ? 0.0 : (double) holdingDaysSum / trades.size(),
                shares > 0,
                trades,
                equityCurve,
                buyAndHoldCurve);
    }

    /**
     * 구간 첫날 전량 매수해 끝까지 보유했을 때의 일별 자산 곡선.
     * 매수 수수료만 반영하고, 계속 보유가 전제이므로 매도 비용은 빼지 않는다.
     */
    private static List<EquityPoint> buyAndHoldCurve(List<LocalDate> dates, List<Double> closes,
                                                     int startIndex, double initialCapital,
                                                     double feeRate) {
        double first = closes.get(startIndex);
        long shares = (long) Math.floor(initialCapital / (first * (1 + feeRate)));
        double cash = initialCapital - shares * first * (1 + feeRate);

        List<EquityPoint> curve = new ArrayList<>(dates.size() - startIndex);
        for (int i = startIndex; i < dates.size(); i++) {
            curve.add(new EquityPoint(dates.get(i), cash + shares * closes.get(i)));
        }
        return curve;
    }

    /** 자산 곡선의 고점 대비 최대 하락률(%). */
    private static double maxDrawdownPct(List<EquityPoint> curve) {
        double peak = Double.NEGATIVE_INFINITY;
        double maxDrawdown = 0;

        for (EquityPoint p : curve) {
            double equity = p.equity();
            if (equity > peak) peak = equity;
            if (peak > 0) {
                double drawdown = (peak - equity) / peak * 100;
                if (drawdown > maxDrawdown) maxDrawdown = drawdown;
            }
        }
        return maxDrawdown;
    }

    private static void validate(List<LocalDate> dates, List<Double> closes,
                                 int shortPeriod, int longPeriod,
                                 double initialCapital, double feeRate, double taxRate) {
        if (dates.size() != closes.size()) {
            throw new IllegalArgumentException("dates 와 closes 의 길이가 같아야 합니다.");
        }
        if (shortPeriod < 1) {
            throw new IllegalArgumentException("shortPeriod 는 1 이상이어야 합니다: " + shortPeriod);
        }
        if (shortPeriod >= longPeriod) {
            throw new IllegalArgumentException(
                    "shortPeriod 는 longPeriod 보다 작아야 합니다: " + shortPeriod + " >= " + longPeriod);
        }
        if (initialCapital <= 0) {
            throw new IllegalArgumentException("initialCapital 은 0보다 커야 합니다: " + initialCapital);
        }
        if (feeRate < 0 || taxRate < 0) {
            throw new IllegalArgumentException("수수료율과 세율은 0 이상이어야 합니다.");
        }
    }
}
