package com.example.Stock.controller;

import com.example.Stock.domain.Stock;
import com.example.Stock.domain.StockPrice;
import com.example.Stock.dto.BacktestResult;
import com.example.Stock.dto.CrossSignal;
import com.example.Stock.dto.IndicatorPoint;
import com.example.Stock.repository.UserRepository;
import com.example.Stock.service.AiRecommendService;
import com.example.Stock.service.BacktestService;
import com.example.Stock.service.IndicatorService;
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
    private final IndicatorService indicatorService;
    private final BacktestService backtestService;

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

    // 기술적 지표
    @Operation(summary = "기술적 지표 조회",
            description = "누적 일봉으로 이동평균(5/20/60), RSI(14), 볼린저밴드(20,2σ)를 계산한다. "
                    + "구간 시작일의 지표도 정확하도록 앞쪽 데이터를 추가로 읽어 계산한다.")
    @GetMapping("/{symbol}/indicators")
    public ResponseEntity<List<IndicatorPoint>> getIndicators(
            @Parameter(description = "종목코드 (예: 005930)") @PathVariable String symbol,
            @Parameter(description = "시작일 (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "종료일 (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(indicatorService.getIndicators(symbol, from, to));
    }

    // 이동평균 교차 신호
    @Operation(summary = "골든/데드크로스 신호 조회",
            description = "단기 이동평균이 장기 이동평균을 돌파한 지점을 찾는다.")
    @GetMapping("/{symbol}/signals")
    public ResponseEntity<List<CrossSignal>> getCrossSignals(
            @Parameter(description = "종목코드") @PathVariable String symbol,
            @Parameter(description = "단기 이동평균 기간") @RequestParam(defaultValue = "20") int shortPeriod,
            @Parameter(description = "장기 이동평균 기간") @RequestParam(defaultValue = "60") int longPeriod,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(
                indicatorService.getCrossSignals(symbol, shortPeriod, longPeriod, from, to));
    }

    // 백테스팅
    @Operation(summary = "이동평균 교차 전략 백테스팅",
            description = "골든크로스 매수 / 데드크로스 매도 전략을 과거 데이터로 시뮬레이션한다. "
                    + "누적 수익률·최대낙폭(MDD)·승률과 단순 보유(Buy&Hold) 대비 성과를 함께 반환한다. "
                    + "체결은 미래참조를 피하기 위해 신호 다음 거래일 종가로 처리한다.")
    @GetMapping("/{symbol}/backtest")
    public ResponseEntity<BacktestResult> backtest(
            @Parameter(description = "종목코드 (예: 005930)") @PathVariable String symbol,
            @Parameter(description = "단기 이동평균 기간") @RequestParam(defaultValue = "5") int shortPeriod,
            @Parameter(description = "장기 이동평균 기간") @RequestParam(defaultValue = "20") int longPeriod,
            @Parameter(description = "시작일 (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "종료일 (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "초기 자본") @RequestParam(required = false) Double initialCapital,
            @Parameter(description = "매매 수수료율 (0.00015 = 0.015%)")
            @RequestParam(required = false) Double feeRate,
            @Parameter(description = "증권거래세율 (0.0018 = 0.18%)")
            @RequestParam(required = false) Double taxRate) {
        return ResponseEntity.ok(backtestService.run(
                symbol, shortPeriod, longPeriod, from, to,
                initialCapital == null ? BacktestService.DEFAULT_CAPITAL : initialCapital,
                feeRate == null ? BacktestService.DEFAULT_FEE_RATE : feeRate,
                taxRate == null ? BacktestService.DEFAULT_TAX_RATE : taxRate));
    }

    // 잘못된 요청 파라미터는 400으로 응답한다 (기간 조합 오류 등)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
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