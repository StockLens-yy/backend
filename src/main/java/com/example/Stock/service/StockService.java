package com.example.Stock.service;

import com.example.Stock.domain.Stock;
import com.example.Stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final RestTemplate restTemplate;
    private final KisTokenService kisTokenService;

    @Value("${kis.api.appkey}")
    private String appKey;

    @Value("${kis.api.appsecret}")
    private String appSecret;

    @Value("${kis.api.base-url}")
    private String baseUrl;

    private static final long API_CALL_INTERVAL_MS = 500L; // KIS 초당 호출 한도 대비
    private static final int MAX_RETRY = 3;
    private static final long RETRY_BACKOFF_MS = 1000L;
    private static final String RATE_LIMIT_CODE = "EGW00201";

    // KIS 시가총액 순위 조회 (코스피, API 한도상 최대 30건) - 현재가가 함께 내려와 바로 저장한다
    public List<Stock> fetchMarketCapTop() {
        String token = kisTokenService.getAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + token);
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", "FHPST01740000");
        headers.set("custtype", "P");

        HttpEntity<Void> request = new HttpEntity<>(headers);

        String url = baseUrl + "/uapi/domestic-stock/v1/ranking/market-cap"
                + "?fid_input_price_2=&fid_cond_mrkt_div_code=J&fid_cond_scr_div_code=20174"
                + "&fid_div_cls_code=0&fid_input_iscd=0001&fid_trgt_cls_code=0"
                + "&fid_trgt_exls_cls_code=0&fid_input_price_1=&fid_vol_cnt=";

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
        List<Map<String, Object>> output = (List<Map<String, Object>>) response.getBody().get("output");

        List<Stock> result = new ArrayList<>();
        if (output == null) return result;

        for (Map<String, Object> item : output) {
            String symbol = (String) item.get("mksc_shrn_iscd");
            if (symbol == null || symbol.isBlank()) continue;

            Stock stock = stockRepository.findBySymbol(symbol)
                    .orElse(new Stock());

            stock.setSymbol(symbol);
            stock.setName((String) item.get("hts_kor_isnm"));
            applyQuote(stock, item);

            result.add(stockRepository.save(stock));
        }
        return result;
    }

    // 단일 종목 실시간 주가 조회 후 DB(홈 화면 목록)에 저장
    public Stock getStockPrice(String symbol, String name) {
        Map output = inquirePrice(symbol);

        Stock stock = stockRepository.findBySymbol(symbol)
                .orElse(new Stock());

        stock.setSymbol(symbol);
        stock.setName(name);
        applyQuote(stock, output);

        return stockRepository.save(stock);
    }

    // 단일 종목 실시간 주가 조회 (저장하지 않음) - 검색한 종목 조회용
    public Stock getQuote(String symbol, String name) {
        Stock stock = new Stock();
        stock.setSymbol(symbol);
        stock.setName(name);
        applyQuote(stock, inquirePrice(symbol));
        return stock;
    }

    // KIS 현재가 시세 API 호출. 초당 호출 한도(EGW00201)에 걸리면 잠시 대기 후 재시도한다
    private Map inquirePrice(String symbol) {
        for (int attempt = 1; ; attempt++) {
            try {
                return requestPrice(symbol);
            } catch (RestClientResponseException e) {
                boolean rateLimited = e.getResponseBodyAsString().contains(RATE_LIMIT_CODE);
                if (!rateLimited || attempt >= MAX_RETRY) throw e;
                try {
                    Thread.sleep(RETRY_BACKOFF_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    private Map requestPrice(String symbol) {
        String token = kisTokenService.getAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + token);
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", "FHKST01010100");
        headers.set("custtype", "P");

        HttpEntity<Void> request = new HttpEntity<>(headers);

        String url = baseUrl + "/uapi/domestic-stock/v1/quotations/inquire-price"
                + "?fid_cond_mrkt_div_code=J&fid_input_iscd=" + symbol;

        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, request, Map.class
        );

        return (Map) response.getBody().get("output");
    }

    // KIS 응답의 시세 필드를 Stock에 반영 (현재가 API와 순위 API의 필드명이 같다)
    private void applyQuote(Stock stock, Map output) {
        stock.setPrice(Double.parseDouble((String) output.get("stck_prpr")));
        stock.setChange(Double.parseDouble((String) output.get("prdy_vrss")));
        stock.setChangePercent(Double.parseDouble((String) output.get("prdy_ctrt")));
        stock.setVolume(Long.parseLong((String) output.get("acml_vol")));
    }

    // 홈 목록 시세 갱신: 시가총액 순위(30건)를 한 번에 받고, 그 외 DB에 등록된 종목은 개별 조회한다
    public List<Stock> getAllStockPrices() {
        List<Stock> result = new ArrayList<>();
        Set<String> done = new HashSet<>();

        try {
            for (Stock stock : fetchMarketCapTop()) {
                result.add(stock);
                done.add(stock.getSymbol());
            }
        } catch (Exception e) {
            System.out.println("시가총액 순위 조회 실패, DB 종목으로 대체: " + e.getMessage());
        }

        for (Stock saved : stockRepository.findAll()) {
            if (saved.getSymbol() == null || !done.add(saved.getSymbol())) continue;
            try {
                result.add(getStockPrice(saved.getSymbol(), saved.getName()));
                Thread.sleep(API_CALL_INTERVAL_MS);
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("조회 실패: " + saved.getName() + " / 원인: " + e.getMessage());
            }
        }
        return result;
    }

    public List<Stock> getAllStocks() {
        return stockRepository.findAll();
    }
    // 일봉 데이터 조회 (최근 30일)
    public List<Map<String, Object>> getDailyChart(String symbol) {
        return getDailyChart(symbol, java.time.LocalDate.now().minusDays(30), java.time.LocalDate.now());
    }

    // 일봉 데이터 조회 (기간 지정) - 시계열 수집 배치에서 사용
    public List<Map<String, Object>> getDailyChart(String symbol, java.time.LocalDate from, java.time.LocalDate to) {
        return fetchCandles(symbol, "D", from, to);
    }

    // 주봉 데이터 조회
    public List<Map<String, Object>> getWeeklyChart(String symbol) {
        return fetchCandles(symbol, "W", java.time.LocalDate.now().minusMonths(3), java.time.LocalDate.now());
    }

    // KIS 기간별 시세 조회 공통 로직 (D: 일봉, W: 주봉, M: 월봉)
    private List<Map<String, Object>> fetchCandles(String symbol, String periodDivCode,
                                                   java.time.LocalDate from, java.time.LocalDate to) {
        String token = kisTokenService.getAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + token);
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", "FHKST03010100");
        headers.set("custtype", "P");

        HttpEntity<Void> request = new HttpEntity<>(headers);

        java.time.format.DateTimeFormatter fmt =
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd");

        String url = baseUrl + "/uapi/domestic-stock/v1/quotations/inquire-daily-price"
                + "?fid_cond_mrkt_div_code=J"
                + "&fid_input_iscd=" + symbol
                + "&fid_period_div_code=" + periodDivCode
                + "&fid_org_adj_prc=0"
                + "&fid_input_date_1=" + from.format(fmt)
                + "&fid_input_date_2=" + to.format(fmt);

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
        List<Map<String, Object>> output = (List<Map<String, Object>>) response.getBody().get("output2");

        List<Map<String, Object>> result = new ArrayList<>();
        if (output != null) {
            for (Map<String, Object> item : output) {
                if (item == null || item.get("stck_bsop_date") == null) continue;
                Map<String, Object> data = new HashMap<>();
                data.put("date", item.get("stck_bsop_date"));
                data.put("close", item.get("stck_clpr"));
                data.put("open", item.get("stck_oprc"));
                data.put("high", item.get("stck_hgpr"));
                data.put("low", item.get("stck_lwpr"));
                data.put("volume", item.get("acml_vol"));
                result.add(data);
            }
        }
        return result;
    }
}
