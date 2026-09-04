package com.example.Stock.service;

import com.example.Stock.domain.StockPrice;
import com.example.Stock.dto.BollingerBand;
import com.example.Stock.dto.CrossSignal;
import com.example.Stock.dto.IndicatorPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 누적된 일봉 시계열로 기술적 지표를 계산해 제공한다.
 * 계산 자체는 {@link IndicatorCalculator} 가 담당하고, 이 클래스는 데이터 적재와 조립만 맡는다.
 */
@Service
@RequiredArgsConstructor
public class IndicatorService {

    private static final int MA_SHORT = 5;
    private static final int MA_MID = 20;
    private static final int MA_LONG = 60;
    private static final int RSI_PERIOD = 14;
    private static final int BOLLINGER_PERIOD = 20;
    private static final double BOLLINGER_K = 2.0;

    private final StockPriceService stockPriceService;

    /**
     * 지정 구간의 지표 시계열.
     * 구간 시작일의 이동평균도 제대로 나오도록 앞쪽 데이터를 더 읽어 계산한 뒤 잘라낸다.
     */
    public List<IndicatorPoint> getIndicators(String symbol, LocalDate from, LocalDate to) {
        int maxPeriod = Math.max(MA_LONG, Math.max(MA_MID, RSI_PERIOD));
        List<StockPrice> prices = loadWithWarmup(symbol, from, to, maxPeriod);
        if (prices.isEmpty()) return List.of();

        List<LocalDate> dates = new ArrayList<>(prices.size());
        List<Double> closes = new ArrayList<>(prices.size());
        for (StockPrice p : prices) {
            dates.add(p.getTradeDate());
            closes.add(p.getClose());
        }

        List<Double> ma5 = IndicatorCalculator.sma(closes, MA_SHORT);
        List<Double> ma20 = IndicatorCalculator.sma(closes, MA_MID);
        List<Double> ma60 = IndicatorCalculator.sma(closes, MA_LONG);
        List<Double> rsi = IndicatorCalculator.rsi(closes, RSI_PERIOD);
        List<BollingerBand> bands = IndicatorCalculator.bollinger(closes, BOLLINGER_PERIOD, BOLLINGER_K);

        List<IndicatorPoint> result = new ArrayList<>();
        for (int i = 0; i < dates.size(); i++) {
            LocalDate date = dates.get(i);
            if (from != null && date.isBefore(from)) continue;  // 워밍업 구간 제외

            BollingerBand band = bands.get(i);
            result.add(new IndicatorPoint(
                    date, closes.get(i),
                    ma5.get(i), ma20.get(i), ma60.get(i),
                    rsi.get(i),
                    band.upper(), band.middle(), band.lower()
            ));
        }
        return result;
    }

    /**
     * 이동평균 교차(골든/데드크로스) 신호 목록.
     *
     * @param shortPeriod 단기 이동평균 기간
     * @param longPeriod  장기 이동평균 기간
     */
    public List<CrossSignal> getCrossSignals(String symbol, int shortPeriod, int longPeriod,
                                             LocalDate from, LocalDate to) {
        if (shortPeriod >= longPeriod) {
            throw new IllegalArgumentException(
                    "shortPeriod 는 longPeriod 보다 작아야 합니다: " + shortPeriod + " >= " + longPeriod);
        }

        List<StockPrice> prices = loadWithWarmup(symbol, from, to, longPeriod);
        if (prices.isEmpty()) return List.of();

        List<LocalDate> dates = new ArrayList<>(prices.size());
        List<Double> closes = new ArrayList<>(prices.size());
        for (StockPrice p : prices) {
            dates.add(p.getTradeDate());
            closes.add(p.getClose());
        }

        List<CrossSignal> signals = IndicatorCalculator.detectCrosses(
                dates,
                IndicatorCalculator.sma(closes, shortPeriod),
                IndicatorCalculator.sma(closes, longPeriod));

        if (from == null) return signals;
        List<CrossSignal> filtered = new ArrayList<>();
        for (CrossSignal s : signals) {
            if (!s.date().isBefore(from)) filtered.add(s);
        }
        return filtered;
    }

    /** 지표 계산에 필요한 워밍업 구간을 포함해 종가 데이터를 읽고, 종가가 없는 행은 제외한다. */
    private List<StockPrice> loadWithWarmup(String symbol, LocalDate from, LocalDate to, int maxPeriod) {
        LocalDate loadFrom = null;
        if (from != null) {
            // 거래일 기준 maxPeriod 개를 확보하려면 휴일을 감안해 넉넉히 잡는다
            loadFrom = from.minusDays(maxPeriod * 2L + 10);
        }
        List<StockPrice> prices = stockPriceService.getHistory(symbol, loadFrom, to);

        List<StockPrice> result = new ArrayList<>(prices.size());
        for (StockPrice p : prices) {
            if (p.getClose() != null) result.add(p);
        }
        return result;
    }
}
