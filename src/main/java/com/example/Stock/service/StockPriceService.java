package com.example.Stock.service;

import com.example.Stock.domain.Stock;
import com.example.Stock.domain.StockPrice;
import com.example.Stock.repository.StockPriceRepository;
import com.example.Stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 일봉 시계열 수집/조회 서비스.
 * KIS API 응답을 stock_price 테이블에 누적한다. (symbol, trade_date) 기준 upsert 이므로
 * 같은 구간을 여러 번 수집해도 행이 중복되지 않는다.
 */
@Service
@RequiredArgsConstructor
public class StockPriceService {

    private static final Logger log = LoggerFactory.getLogger(StockPriceService.class);

    /** KIS 호출 간 최소 간격(ms). 초당 호출 한도 회피용. */
    private static final long API_CALL_INTERVAL_MS = 300L;

    /** 호출 한도 초과(EGW00201) 시 재시도 횟수와 대기 시간. */
    private static final int MAX_RETRY = 3;
    private static final long RETRY_BACKOFF_MS = 1000L;
    private static final String RATE_LIMIT_CODE = "EGW00201";

    private static final DateTimeFormatter KIS_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StockService stockService;
    private final StockRepository stockRepository;
    private final StockPriceRepository stockPriceRepository;

    /**
     * 한 종목의 지정 구간 일봉을 수집해 저장한다.
     * @return 신규 저장 + 갱신된 행 수
     */
    public int collectSymbol(String symbol, LocalDate from, LocalDate to) {
        List<Map<String, Object>> candles = fetchWithRetry(symbol, from, to);
        if (candles.isEmpty()) return 0;

        // 구간 내 기존 행을 한 번에 읽어 캔들마다 조회하지 않도록 한다
        Map<LocalDate, StockPrice> existing = new HashMap<>();
        for (StockPrice p : stockPriceRepository.findBySymbolAndTradeDateBetween(symbol, from, to)) {
            existing.put(p.getTradeDate(), p);
        }

        List<StockPrice> toSave = new ArrayList<>();
        for (Map<String, Object> c : candles) {
            LocalDate date = parseDate(c.get("date"));
            if (date == null) continue;

            StockPrice price = existing.get(date);
            if (price == null) price = new StockPrice(symbol, date);

            price.setOpen(parseDouble(c.get("open")));
            price.setHigh(parseDouble(c.get("high")));
            price.setLow(parseDouble(c.get("low")));
            price.setClose(parseDouble(c.get("close")));
            price.setVolume(parseLong(c.get("volume")));
            toSave.add(price);
        }

        stockPriceRepository.saveAll(toSave);
        return toSave.size();
    }

    /**
     * DB에 등록된 전체 종목의 최근 {@code days}일 일봉을 수집한다.
     * 종목이 하나도 없으면 KIS 시가총액 순위 결과를 사용한다.
     */
    public Map<String, Object> collectAll(int days) {
        long startedAt = System.currentTimeMillis();
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days);

        List<String> symbols = new ArrayList<>();
        for (Stock s : stockRepository.findAll()) {
            if (s.getSymbol() != null) symbols.add(s.getSymbol());
        }
        if (symbols.isEmpty()) {
            for (Stock s : stockService.fetchMarketCapTop()) symbols.add(s.getSymbol());
        }

        int savedRows = 0;
        List<String> failed = new ArrayList<>();

        for (String symbol : symbols) {
            try {
                savedRows += collectSymbol(symbol, from, to);
            } catch (Exception e) {
                failed.add(symbol);
                log.warn("일봉 수집 실패: {} / 원인: {}", symbol, e.getMessage());
            }
            try {
                Thread.sleep(API_CALL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("일봉 수집이 중단되었습니다.");
                break;
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("from", from.format(KIS_DATE));
        summary.put("to", to.format(KIS_DATE));
        summary.put("symbolCount", symbols.size());
        summary.put("savedRows", savedRows);
        summary.put("failedSymbols", failed);
        summary.put("elapsedMs", System.currentTimeMillis() - startedAt);

        log.info("일봉 수집 완료: 종목 {}개, 저장 {}행, 실패 {}개, {}ms",
                symbols.size(), savedRows, failed.size(), summary.get("elapsedMs"));
        return summary;
    }

    /** 수집을 백그라운드로 실행한다. (수십 초 걸리므로 HTTP 요청을 붙잡지 않는다) */
    @Async
    public void collectAllAsync(int days) {
        collectAll(days);
    }

    /** 저장된 시계열 조회. 기간 미지정 시 전체 이력을 반환한다. */
    public List<StockPrice> getHistory(String symbol, LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return stockPriceRepository.findBySymbolOrderByTradeDateAsc(symbol);
        }
        return stockPriceRepository.findBySymbolAndTradeDateBetweenOrderByTradeDateAsc(symbol, from, to);
    }

    /** 현재 누적된 시계열 규모 요약. */
    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalRows", stockPriceRepository.count());
        result.put("symbolCount", stockPriceRepository.countDistinctSymbols());
        return result;
    }

    /**
     * KIS 초당 호출 한도(EGW00201)에 걸리면 대기 후 재시도한다.
     * 한도 외의 오류는 그대로 던져 상위에서 실패 종목으로 집계되게 한다.
     */
    private List<Map<String, Object>> fetchWithRetry(String symbol, LocalDate from, LocalDate to) {
        for (int attempt = 1; ; attempt++) {
            try {
                return stockService.getDailyChart(symbol, from, to);
            } catch (RestClientResponseException e) {
                boolean rateLimited = e.getResponseBodyAsString().contains(RATE_LIMIT_CODE);
                if (!rateLimited || attempt >= MAX_RETRY) throw e;

                long wait = RETRY_BACKOFF_MS * attempt;
                log.warn("호출 한도 초과, {}ms 후 재시도 ({}/{}): {}", wait, attempt, MAX_RETRY, symbol);
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    private LocalDate parseDate(Object value) {
        if (value == null) return null;
        try {
            return LocalDate.parse(value.toString().trim(), KIS_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    private Double parseDouble(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        try {
            return Double.parseDouble(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLong(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
