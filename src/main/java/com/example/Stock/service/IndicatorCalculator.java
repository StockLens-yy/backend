package com.example.Stock.service;

import com.example.Stock.dto.BollingerBand;
import com.example.Stock.dto.CrossSignal;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 기술적 지표 계산. 외부 의존이 없는 순수 함수 모음이라 단위 테스트로 검증한다.
 * <p>
 * 모든 메서드는 입력과 같은 길이의 리스트를 반환하며, 계산에 필요한 구간이
 * 확보되지 않은 앞부분은 {@code null} 로 채운다. (프론트에서 그대로 차트에 넘기기 위함)
 */
public final class IndicatorCalculator {

    private IndicatorCalculator() {
    }

    /**
     * 단순이동평균(SMA).
     *
     * @param values 시간 오름차순 값
     * @param period 기간 (1 이상)
     */
    public static List<Double> sma(List<Double> values, int period) {
        requirePositive(period);
        List<Double> result = new ArrayList<>(values.size());

        for (int i = 0; i < values.size(); i++) {
            if (i < period - 1) {
                result.add(null);
                continue;
            }
            double sum = 0;
            boolean valid = true;
            for (int j = i - period + 1; j <= i; j++) {
                Double v = values.get(j);
                if (v == null) {
                    valid = false;
                    break;
                }
                sum += v;
            }
            result.add(valid ? sum / period : null);
        }
        return result;
    }

    /**
     * RSI (Wilder 방식). 첫 값은 초기 {@code period} 구간의 단순평균으로 구하고,
     * 이후는 지수적 평활(avg * (period-1) + 최신값) / period 로 갱신한다.
     * <p>
     * 상승분만 있으면 100, 하락분만 있으면 0, 가격이 전혀 변하지 않으면 중립값 50 을 반환한다.
     */
    public static List<Double> rsi(List<Double> closes, int period) {
        requirePositive(period);
        List<Double> result = new ArrayList<>(closes.size());
        for (int i = 0; i < closes.size(); i++) result.add(null);

        if (closes.size() <= period) return result;

        double avgGain = 0;
        double avgLoss = 0;

        // 초기 구간: 단순평균
        for (int i = 1; i <= period; i++) {
            double diff = closes.get(i) - closes.get(i - 1);
            if (diff > 0) avgGain += diff;
            else avgLoss -= diff;
        }
        avgGain /= period;
        avgLoss /= period;
        result.set(period, toRsi(avgGain, avgLoss));

        // 이후: Wilder 평활
        for (int i = period + 1; i < closes.size(); i++) {
            double diff = closes.get(i) - closes.get(i - 1);
            double gain = diff > 0 ? diff : 0;
            double loss = diff < 0 ? -diff : 0;

            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;
            result.set(i, toRsi(avgGain, avgLoss));
        }
        return result;
    }

    /**
     * 볼린저밴드. 중심선은 SMA, 상·하단은 중심선 ± {@code k} × 표준편차(모표준편차)다.
     */
    public static List<BollingerBand> bollinger(List<Double> closes, int period, double k) {
        requirePositive(period);
        List<Double> middle = sma(closes, period);
        List<BollingerBand> result = new ArrayList<>(closes.size());

        for (int i = 0; i < closes.size(); i++) {
            Double mid = middle.get(i);
            if (mid == null) {
                result.add(BollingerBand.EMPTY);
                continue;
            }
            double variance = 0;
            for (int j = i - period + 1; j <= i; j++) {
                double diff = closes.get(j) - mid;
                variance += diff * diff;
            }
            double sd = Math.sqrt(variance / period);
            result.add(new BollingerBand(mid + k * sd, mid, mid - k * sd));
        }
        return result;
    }

    /**
     * 단기·장기 이동평균의 교차 지점을 찾는다.
     * 상향 돌파는 골든크로스, 하향 돌파는 데드크로스다.
     *
     * @param dates    날짜 (shortMa/longMa 와 같은 길이·순서)
     * @param shortMa  단기 이동평균
     * @param longMa   장기 이동평균
     */
    public static List<CrossSignal> detectCrosses(List<LocalDate> dates,
                                                  List<Double> shortMa,
                                                  List<Double> longMa) {
        if (dates.size() != shortMa.size() || dates.size() != longMa.size()) {
            throw new IllegalArgumentException("dates, shortMa, longMa 의 길이가 같아야 합니다.");
        }

        List<CrossSignal> signals = new ArrayList<>();
        for (int i = 1; i < dates.size(); i++) {
            Double prevShort = shortMa.get(i - 1);
            Double prevLong = longMa.get(i - 1);
            Double curShort = shortMa.get(i);
            Double curLong = longMa.get(i);
            if (prevShort == null || prevLong == null || curShort == null || curLong == null) continue;

            if (prevShort <= prevLong && curShort > curLong) {
                signals.add(new CrossSignal(dates.get(i), CrossSignal.Type.GOLDEN, curShort, curLong));
            } else if (prevShort >= prevLong && curShort < curLong) {
                signals.add(new CrossSignal(dates.get(i), CrossSignal.Type.DEAD, curShort, curLong));
            }
        }
        return signals;
    }

    private static double toRsi(double avgGain, double avgLoss) {
        if (avgLoss == 0 && avgGain == 0) return 50.0;  // 가격 변동 없음 - 중립
        if (avgLoss == 0) return 100.0;
        if (avgGain == 0) return 0.0;
        return 100.0 - (100.0 / (1.0 + avgGain / avgLoss));
    }

    private static void requirePositive(int period) {
        if (period < 1) throw new IllegalArgumentException("period 는 1 이상이어야 합니다: " + period);
    }
}
