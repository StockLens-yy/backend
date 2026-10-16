package com.example.Stock.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/**
 * 브리핑용 지표 계산. 외부 호출이 없는 순수 함수만 둔다.
 * <p>
 * 금액은 원 단위 정수({@link #PRICE_SCALE}), 비율·배율·RSI 는 소수점 둘째 자리({@link #RATE_SCALE})로
 * HALF_UP 반올림한다. 중간 계산은 반올림하지 않고 {@link #MC} 정밀도로 유지한다.
 * 데이터가 부족하거나 0으로 나눠야 하는 경우 예외 대신 {@code null} 을 돌려준다.
 */
public final class StockIndicatorCalculator {

    public static final int PRICE_SCALE = 0;
    public static final int RATE_SCALE = 2;
    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** 현재가가 기준선(이동평균) 위인지 아래인지 */
    public enum Position { ABOVE, BELOW, EQUAL }

    /** RSI 구간. 70 이상 과열, 30 이하 침체, 그 사이 중립 */
    public enum RsiZone { OVERBOUGHT, NEUTRAL, OVERSOLD }

    /** 52주 범위 구간. 30% 미만 하단, 70% 초과 상단, 그 사이 중간 */
    public enum RangeZone { LOW, MIDDLE, HIGH }

    private static final BigDecimal RANGE_LOW = BigDecimal.valueOf(30);
    private static final BigDecimal RANGE_HIGH = BigDecimal.valueOf(70);
    private static final BigDecimal RSI_OVERBOUGHT = BigDecimal.valueOf(70);
    private static final BigDecimal RSI_OVERSOLD = BigDecimal.valueOf(30);

    private StockIndicatorCalculator() {
    }

    /** 마지막 값 기준 전일 대비 등락률(%). 값이 2개 미만이거나 전일 값이 0이면 null */
    public static BigDecimal changeRate(List<BigDecimal> closes) {
        if (closes == null || closes.size() < 2) return null;
        BigDecimal prev = closes.get(closes.size() - 2);
        BigDecimal last = closes.get(closes.size() - 1);
        return percentChange(last, prev);
    }

    /** 최근 {@code period}개 값의 단순 이동평균. 값이 부족하면 null */
    public static BigDecimal sma(List<BigDecimal> values, int period) {
        BigDecimal avg = rawAverage(values, values == null ? 0 : values.size(), period);
        return avg == null ? null : avg.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    /** 현재가의 기준선 대비 괴리율(%). (현재가 - 기준) / 기준 × 100 */
    public static BigDecimal gapRate(BigDecimal current, List<BigDecimal> closes, int period) {
        BigDecimal avg = rawAverage(closes, closes == null ? 0 : closes.size(), period);
        if (current == null || avg == null) return null;
        return percentChange(current, avg);
    }

    /** 현재가가 이동평균 위/아래인지. 이동평균을 구할 수 없으면 null */
    public static Position position(BigDecimal current, List<BigDecimal> closes, int period) {
        BigDecimal avg = rawAverage(closes, closes == null ? 0 : closes.size(), period);
        if (current == null || avg == null) return null;
        int cmp = current.compareTo(avg);
        return cmp > 0 ? Position.ABOVE : cmp < 0 ? Position.BELOW : Position.EQUAL;
    }

    /**
     * 직전 {@code period}일 평균 거래량. 마지막 값(당일)은 제외한다.
     * 당일을 빼야 "평소 대비 오늘" 비교가 된다.
     */
    public static BigDecimal averageVolume(List<Long> volumes, int period) {
        BigDecimal avg = rawPriorAverage(volumes, period);
        return avg == null ? null : avg.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    /** 당일 거래량 ÷ 직전 {@code period}일 평균 거래량 (배). 평균이 0이거나 데이터가 부족하면 null */
    public static BigDecimal volumeRatio(List<Long> volumes, int period) {
        BigDecimal avg = rawPriorAverage(volumes, period);
        if (avg == null || avg.signum() == 0) return null;
        Long today = volumes.get(volumes.size() - 1);
        if (today == null) return null;
        return BigDecimal.valueOf(today).divide(avg, MC).setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    /** 최저~최고 범위 안에서 현재가의 위치(%). 최저가면 0, 최고가면 100. 범위가 없으면 null */
    public static BigDecimal rangePosition(BigDecimal current, BigDecimal low, BigDecimal high) {
        if (current == null || low == null || high == null) return null;
        BigDecimal range = high.subtract(low);
        if (range.signum() <= 0) return null;
        return current.subtract(low).multiply(HUNDRED).divide(range, MC).setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 마지막 값 기준 RSI (Wilder 방식). 첫 평균은 처음 {@code period}개 변화량의 단순평균,
     * 이후는 (이전 평균 × (period-1) + 이번 변화량) / period 로 평활한다.
     * 상승만 있으면 100, 하락만 있으면 0, 변화가 전혀 없으면 50. 값이 period+1개 미만이면 null.
     */
    public static BigDecimal rsi(List<BigDecimal> closes, int period) {
        if (closes == null || period <= 0 || closes.size() < period + 1) return null;

        BigDecimal p = BigDecimal.valueOf(period);
        BigDecimal avgGain = BigDecimal.ZERO;
        BigDecimal avgLoss = BigDecimal.ZERO;
        for (int i = 1; i <= period; i++) {
            BigDecimal diff = closes.get(i).subtract(closes.get(i - 1));
            if (diff.signum() > 0) avgGain = avgGain.add(diff);
            else avgLoss = avgLoss.subtract(diff);
        }
        avgGain = avgGain.divide(p, MC);
        avgLoss = avgLoss.divide(p, MC);

        BigDecimal pMinus1 = BigDecimal.valueOf(period - 1L);
        for (int i = period + 1; i < closes.size(); i++) {
            BigDecimal diff = closes.get(i).subtract(closes.get(i - 1));
            BigDecimal gain = diff.signum() > 0 ? diff : BigDecimal.ZERO;
            BigDecimal loss = diff.signum() < 0 ? diff.negate() : BigDecimal.ZERO;
            avgGain = avgGain.multiply(pMinus1).add(gain).divide(p, MC);
            avgLoss = avgLoss.multiply(pMinus1).add(loss).divide(p, MC);
        }

        BigDecimal rsi;
        if (avgLoss.signum() == 0) {
            rsi = avgGain.signum() == 0 ? BigDecimal.valueOf(50) : HUNDRED;
        } else {
            BigDecimal rs = avgGain.divide(avgLoss, MC);
            rsi = HUNDRED.subtract(HUNDRED.divide(BigDecimal.ONE.add(rs), MC));
        }
        return rsi.setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    /** RSI 값이 어느 구간인지. 값이 없으면 null */
    public static RsiZone rsiZone(BigDecimal rsi) {
        if (rsi == null) return null;
        if (rsi.compareTo(RSI_OVERBOUGHT) >= 0) return RsiZone.OVERBOUGHT;
        if (rsi.compareTo(RSI_OVERSOLD) <= 0) return RsiZone.OVERSOLD;
        return RsiZone.NEUTRAL;
    }

    /** 52주 범위 내 위치(%)가 어느 구간인지. 값이 없으면 null */
    public static RangeZone rangeZone(BigDecimal position) {
        if (position == null) return null;
        if (position.compareTo(RANGE_LOW) < 0) return RangeZone.LOW;
        if (position.compareTo(RANGE_HIGH) > 0) return RangeZone.HIGH;
        return RangeZone.MIDDLE;
    }

    // (값 - 기준) / 기준 × 100. 기준이 0이면 null
    private static BigDecimal percentChange(BigDecimal value, BigDecimal base) {
        if (value == null || base == null || base.signum() == 0) return null;
        return value.subtract(base).multiply(HUNDRED).divide(base, MC).setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    // 마지막 값(당일)을 뺀 직전 period개 평균 (반올림 전 값)
    private static BigDecimal rawPriorAverage(List<Long> volumes, int period) {
        if (volumes == null || period <= 0 || volumes.size() < period + 1) return null;
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = volumes.size() - 1 - period; i < volumes.size() - 1; i++) {
            Long v = volumes.get(i);
            if (v == null) return null;
            sum = sum.add(BigDecimal.valueOf(v));
        }
        return sum.divide(BigDecimal.valueOf(period), MC);
    }

    // 끝에서부터 period개 평균 (반올림 전 값)
    private static BigDecimal rawAverage(List<BigDecimal> values, int size, int period) {
        if (values == null || period <= 0 || size < period) return null;
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = size - period; i < size; i++) {
            BigDecimal v = values.get(i);
            if (v == null) return null;
            sum = sum.add(v);
        }
        return sum.divide(BigDecimal.valueOf(period), MC);
    }
}
