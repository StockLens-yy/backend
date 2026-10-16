package com.example.Stock.service;

import com.example.Stock.domain.StockPrice;
import com.example.Stock.dto.BacktestResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 누적된 일봉으로 이동평균 교차 전략을 백테스팅한다.
 * 시뮬레이션은 {@link BacktestEngine} 이 담당하고, 이 클래스는 데이터 적재만 맡는다.
 */
@Service
@RequiredArgsConstructor
public class BacktestService {

    /** 초기 자본 기본값 (1천만원) */
    public static final double DEFAULT_CAPITAL = 10_000_000d;

    /** 위탁 매매 수수료율 기본값 (0.015%) */
    public static final double DEFAULT_FEE_RATE = 0.00015d;

    /** 증권거래세율 기본값 (0.18%, 매도 시에만 적용) */
    public static final double DEFAULT_TAX_RATE = 0.0018d;

    private final StockPriceService stockPriceService;

    public BacktestResult run(String symbol, int shortPeriod, int longPeriod,
                              LocalDate from, LocalDate to,
                              double initialCapital, double feeRate, double taxRate) {
        if (shortPeriod >= longPeriod) {
            throw new IllegalArgumentException(
                    "shortPeriod 는 longPeriod 보다 작아야 합니다: " + shortPeriod + " >= " + longPeriod);
        }

        // 시작일의 이동평균도 유효하도록 앞쪽 데이터를 더 읽는다 (휴일 감안해 넉넉히)
        LocalDate loadFrom = from == null ? null : from.minusDays(longPeriod * 2L + 10);
        List<StockPrice> prices = stockPriceService.getHistory(symbol, loadFrom, to);

        List<LocalDate> dates = new ArrayList<>(prices.size());
        List<Double> closes = new ArrayList<>(prices.size());
        for (StockPrice p : prices) {
            if (p.getClose() == null) continue;
            dates.add(p.getTradeDate());
            closes.add(p.getClose());
        }

        return BacktestEngine.run(symbol, dates, closes, from,
                shortPeriod, longPeriod, initialCapital, feeRate, taxRate);
    }
}
