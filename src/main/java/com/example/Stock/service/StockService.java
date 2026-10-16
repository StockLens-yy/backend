package com.example.Stock.service;

import com.example.Stock.domain.Stock;
import com.example.Stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    // 네이버 금융에서 시가총액 Top50 종목코드 크롤링
    public List<String[]> crawlTop50() {
        List<String[]> result = new ArrayList<>();
        try {
            for (int page = 1; page <= 5; page++) {
                Document doc = Jsoup.connect(
                                "https://finance.naver.com/sise/sise_market_sum.naver?sosok=0&page=" + page
                        )
                        .userAgent("Mozilla/5.0")
                        .get();

                Elements rows = doc.select("table.type_2 tbody tr");
                for (Element row : rows) {
                    Elements cols = row.select("td");
                    if (cols.size() < 2) continue;

                    Element nameEl = row.selectFirst("td a.tltle");
                    if (nameEl == null) continue;

                    String name = nameEl.text();
                    String href = nameEl.attr("href"); // /item/main.naver?code=005930
                    String code = href.replace("/item/main.naver?code=", "");

                    result.add(new String[]{code, name});
                    if (result.size() >= 50) return result;
                }
            }
        } catch (Exception e) {
            System.out.println("크롤링 실패: " + e.getMessage());
        }
        return result;
    }

    // 단일 종목 실시간 주가 조회
    public Stock getStockPrice(String symbol, String name) {
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

        Map output = (Map) response.getBody().get("output");

        Stock stock = stockRepository.findBySymbol(symbol)
                .orElse(new Stock());

        stock.setSymbol(symbol);
        stock.setName(name);
        stock.setPrice(Double.parseDouble((String) output.get("stck_prpr")));
        stock.setChange(Double.parseDouble((String) output.get("prdy_vrss")));
        stock.setChangePercent(Double.parseDouble((String) output.get("prdy_ctrt")));
        stock.setVolume(Long.parseLong((String) output.get("acml_vol")));

        return stockRepository.save(stock);
    }

    // Top50 전체 조회
    public List<Stock> getAllStockPrices() {
        List<String[]> top50 = crawlTop50();
        List<Stock> result = new ArrayList<>();

        for (String[] item : top50) {
            try {
                result.add(getStockPrice(item[0], item[1]));
                Thread.sleep(1000); // 1초
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("조회 실패: " + item[1] + " / 원인: " + e.getMessage());
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
