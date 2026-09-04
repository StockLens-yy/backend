package com.example.Stock.controller;

import com.example.Stock.domain.Stock;
import com.example.Stock.domain.StockPrice;
import com.example.Stock.repository.UserRepository;
import com.example.Stock.service.AiRecommendService;
import com.example.Stock.service.StockPriceService;
import com.example.Stock.service.StockService;
import com.example.Stock.service.WatchListService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "Stock", description = "주식 관련 API")
@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;
    private final AiRecommendService aiRecommendService;
    private final UserRepository userRepository;
    private final WatchListService watchListService;
    private final StockPriceService stockPriceService;

    // 전체 종목 조회
    @Operation(summary = "전체 종목 조회", description = "DB에 저장된 전체 종목 조회")
    @GetMapping
    public ResponseEntity<List<Stock>> getAllStocks() {
        return ResponseEntity.ok(stockService.getAllStocks());
    }

    // Top50 실시간 업데이트
    @Operation(summary = "Top50 실시간 업데이트", description = "네이버 크롤링 + KIS API로 실시간 주가 조회")
    @PostMapping("/refresh")
    public ResponseEntity<List<Stock>> refresh() {
        return ResponseEntity.ok(stockService.getAllStockPrices());
    }

    // 단일 종목 조회
    @Operation(summary = "단일 종목 조회", description = "종목코드로 주가 조회")
    @GetMapping("/{symbol}")
    public ResponseEntity<Stock> getStock(
            @Parameter(description = "종목코드 (예: 005930)") @PathVariable String symbol) {
        return ResponseEntity.ok(stockService.getAllStocks()
                .stream()
                .filter(s -> s.getSymbol().equals(symbol))
                .findFirst()
                .orElseThrow());
    }

    // AI 추천
    @Operation(summary = "AI 종목 추천", description = "Ollama AI가 Top50 데이터 분석 후 추천")
    @GetMapping("/ai-recommend")
    public ResponseEntity<String> aiRecommend() {
        return ResponseEntity.ok(aiRecommendService.recommend());
    }

    // 관심종목 추가
    @Operation(summary = "관심종목 추가")
    @PostMapping("/watchlist/add")
    public ResponseEntity<String> addWatchList(
            @RequestParam String symbol,
            @RequestParam String name,
            @AuthenticationPrincipal Object principal) {
        String email = getEmail(principal);
        if (email == null) return ResponseEntity.status(401).body("로그인이 필요합니다.");
        watchListService.addWatchList(email, symbol, name);
        return ResponseEntity.ok("추가됨");
    }

    // 관심종목 삭제
    @Operation(summary = "관심종목 삭제")
    @PostMapping("/watchlist/remove")
    public ResponseEntity<String> removeWatchList(
            @RequestParam String symbol,
            @AuthenticationPrincipal Object principal) {
        String email = getEmail(principal);
        if (email == null) return ResponseEntity.status(401).body("로그인이 필요합니다.");
        watchListService.removeWatchList(email, symbol);
        return ResponseEntity.ok("삭제됨");
    }

    // 관심종목 조회
    @Operation(summary = "관심종목 조회")
    @GetMapping("/watchlist")
    public ResponseEntity<?> getWatchList(@AuthenticationPrincipal Object principal) {
        String email = getEmail(principal);
        if (email == null) return ResponseEntity.status(401).body("로그인이 필요합니다.");
        return ResponseEntity.ok(watchListService.getWatchList(email));
    }

    // 일봉 차트
    @Operation(summary = "일봉 차트 데이터")
    @GetMapping("/chart/{symbol}/daily")
    public ResponseEntity<?> getDailyChart(
            @Parameter(description = "종목코드") @PathVariable String symbol) {
        try {
            return ResponseEntity.ok(stockService.getDailyChart(symbol));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("차트 데이터 조회 실패: " + e.getMessage());
        }
    }

    // 주봉 차트
    @Operation(summary = "주봉 차트 데이터")
    @GetMapping("/chart/{symbol}/weekly")
    public ResponseEntity<?> getWeeklyChart(
            @Parameter(description = "종목코드") @PathVariable String symbol) {
        try {
            return ResponseEntity.ok(stockService.getWeeklyChart(symbol));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("차트 데이터 조회 실패: " + e.getMessage());
        }
    }

    // 일봉 시계열 조회 (DB 누적분)
    @Operation(summary = "일봉 시계열 조회",
            description = "수집·누적된 일봉 데이터를 조회한다. from/to 미지정 시 전체 이력 반환")
    @GetMapping("/{symbol}/history")
    public ResponseEntity<List<StockPrice>> getHistory(
            @Parameter(description = "종목코드 (예: 005930)") @PathVariable String symbol,
            @Parameter(description = "시작일 (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "종료일 (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(stockPriceService.getHistory(symbol, from, to));
    }

    // 시계열 수집 (백그라운드 실행)
    @Operation(summary = "일봉 시계열 수집",
            description = "전체 종목의 최근 N일 일봉을 백그라운드로 수집한다. 즉시 202 응답")
    @PostMapping("/prices/collect")
    public ResponseEntity<String> collectPrices(
            @Parameter(description = "수집할 기간(일)") @RequestParam(defaultValue = "100") int days) {
        stockPriceService.collectAllAsync(days);
        return ResponseEntity.accepted().body("수집을 시작했습니다. 최근 " + days + "일치");
    }

    // 단일 종목 시계열 수집 (동기)
    @Operation(summary = "단일 종목 일봉 수집", description = "지정 종목의 최근 N일 일봉을 즉시 수집한다")
    @PostMapping("/{symbol}/prices/collect")
    public ResponseEntity<String> collectSymbolPrices(
            @Parameter(description = "종목코드") @PathVariable String symbol,
            @RequestParam(defaultValue = "100") int days) {
        int saved = stockPriceService.collectSymbol(symbol, LocalDate.now().minusDays(days), LocalDate.now());
        return ResponseEntity.ok(symbol + " " + saved + "행 저장");
    }

    // 수집 현황
    @Operation(summary = "시계열 수집 현황", description = "누적된 행 수와 종목 수")
    @GetMapping("/prices/status")
    public ResponseEntity<Map<String, Object>> pricesStatus() {
        return ResponseEntity.ok(stockPriceService.status());
    }

    // 이메일 추출 헬퍼
    private String getEmail(Object principal) {
        if (principal instanceof org.springframework.security.core.userdetails.User u) {
            return u.getUsername();
        } else if (principal instanceof org.springframework.security.oauth2.core.user.OAuth2User o) {
            return o.getAttribute("email");
        }
        return null;
    }
}