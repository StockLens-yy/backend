package com.example.Stock.controller;

import com.example.Stock.dto.OrderView;
import com.example.Stock.dto.PortfolioSummary;
import com.example.Stock.dto.TradeResult;
import com.example.Stock.service.PortfolioService;
import com.example.Stock.util.PrincipalUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Portfolio", description = "모의투자 API")
@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    @Operation(summary = "모의투자 계좌 개설",
            description = "사용자당 하나의 계좌를 개설한다. 이미 있으면 400 을 반환한다.")
    @PostMapping
    public ResponseEntity<?> openAccount(
            @Parameter(description = "초기 자본 (미지정 시 1천만원)")
            @RequestParam(required = false) Double initialCapital,
            @AuthenticationPrincipal Object principal) {
        String email = requireEmail(principal);
        if (email == null) return unauthorized();

        return ResponseEntity.ok(portfolioService.openAccount(email,
                initialCapital == null ? PortfolioService.DEFAULT_INITIAL_CAPITAL : initialCapital));
    }

    @Operation(summary = "계좌 현황 조회",
            description = "예수금, 보유 종목, 평가손익과 수익률을 반환한다.")
    @GetMapping
    public ResponseEntity<?> getPortfolio(@AuthenticationPrincipal Object principal) {
        String email = requireEmail(principal);
        if (email == null) return unauthorized();

        PortfolioSummary summary = portfolioService.getSummary(email);
        return ResponseEntity.ok(summary);
    }

    @Operation(summary = "매수", description = "현재가로 즉시 전량 체결한다.")
    @PostMapping("/orders/buy")
    public ResponseEntity<?> buy(
            @Parameter(description = "종목코드 (예: 005930)") @RequestParam String symbol,
            @Parameter(description = "수량(주)") @RequestParam long quantity,
            @AuthenticationPrincipal Object principal) {
        String email = requireEmail(principal);
        if (email == null) return unauthorized();

        TradeResult result = portfolioService.buy(email, symbol, quantity);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "매도", description = "현재가로 즉시 체결하고 손익을 확정한다.")
    @PostMapping("/orders/sell")
    public ResponseEntity<?> sell(
            @Parameter(description = "종목코드") @RequestParam String symbol,
            @Parameter(description = "수량(주)") @RequestParam long quantity,
            @AuthenticationPrincipal Object principal) {
        String email = requireEmail(principal);
        if (email == null) return unauthorized();

        TradeResult result = portfolioService.sell(email, symbol, quantity);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "체결 내역 조회", description = "최신 체결부터 반환한다.")
    @GetMapping("/orders")
    public ResponseEntity<?> getOrders(@AuthenticationPrincipal Object principal) {
        String email = requireEmail(principal);
        if (email == null) return unauthorized();

        List<OrderView> orders = portfolioService.getOrders(email);
        return ResponseEntity.ok(orders);
    }

    // 예수금 부족, 보유 수량 부족 등 요청이 잘못된 경우
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    private String requireEmail(Object principal) {
        return PrincipalUtils.emailOf(principal);
    }

    private ResponseEntity<String> unauthorized() {
        return ResponseEntity.status(401).body("로그인이 필요합니다.");
    }
}
