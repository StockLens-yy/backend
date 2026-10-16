package com.example.Stock.service;

import com.example.Stock.domain.*;
import com.example.Stock.dto.*;
import com.example.Stock.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 모의투자 서비스.
 * <p>
 * 주문은 현재가로 즉시 전량 체결된다. 호가·부분체결·미체결 주문은 재현하지 않으며,
 * 장 운영시간과 무관하게 체결된다.
 */
@Service
@RequiredArgsConstructor
public class PortfolioService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);

    /** 계좌 개설 기본 자본 (1천만원) */
    public static final double DEFAULT_INITIAL_CAPITAL = 10_000_000d;

    /** 위탁 매매 수수료율 (0.015%) */
    public static final double FEE_RATE = 0.00015d;

    /** 증권거래세율 (0.18%, 매도 시에만 부과) */
    public static final double TAX_RATE = 0.0018d;

    private final PortfolioRepository portfolioRepository;
    private final HoldingRepository holdingRepository;
    private final TradeOrderRepository tradeOrderRepository;
    private final UserRepository userRepository;
    private final StockRepository stockRepository;
    private final StockPriceRepository stockPriceRepository;
    private final StockService stockService;

    /** 계좌 개설. 이미 있으면 예외. */
    @Transactional
    public PortfolioSummary openAccount(String email, double initialCapital) {
        if (initialCapital <= 0) {
            throw new IllegalArgumentException("초기 자본은 0보다 커야 합니다: " + initialCapital);
        }
        User user = findUser(email);
        if (portfolioRepository.existsByUser(user)) {
            throw new IllegalArgumentException("이미 모의투자 계좌가 있습니다.");
        }
        Portfolio portfolio = portfolioRepository.save(new Portfolio(user, initialCapital));
        return summarize(portfolio);
    }

    /** 계좌 현황. 보유 종목은 현재가로 평가한다. */
    public PortfolioSummary getSummary(String email) {
        return summarize(findPortfolio(email));
    }

    /** 매수. 현재가로 즉시 체결한다. */
    @Transactional
    public TradeResult buy(String email, String symbol, long quantity) {
        requireQuantity(quantity);
        Portfolio portfolio = findPortfolio(email);
        Quote quote = currentQuote(symbol);

        double cost = PortfolioCalculator.buyCost(quantity, quote.price(), FEE_RATE);
        if (cost > portfolio.getCash()) {
            long buyable = PortfolioCalculator.maxBuyableQuantity(portfolio.getCash(), quote.price(), FEE_RATE);
            throw new IllegalArgumentException(String.format(
                    "예수금이 부족합니다. 필요 %,.0f원 / 보유 %,.0f원 (최대 %d주 매수 가능)",
                    cost, portfolio.getCash(), buyable));
        }

        portfolio.setCash(portfolio.getCash() - cost);

        Holding holding = holdingRepository.findByPortfolioAndSymbol(portfolio, symbol).orElse(null);
        if (holding == null) {
            holding = new Holding(portfolio, symbol, quote.name(), quantity, cost / quantity);
        } else {
            holding.setAvgBuyPrice(PortfolioCalculator.newAveragePrice(
                    holding.getQuantity(), holding.getAvgBuyPrice(), quantity, cost));
            holding.setQuantity(holding.getQuantity() + quantity);
        }
        holdingRepository.save(holding);

        TradeOrder order = record(portfolio, symbol, quote.name(), TradeOrder.Side.BUY,
                quote.price(), quantity, cost - quantity * quote.price(), 0d, cost, null);

        log.info("모의 매수: {} {} {}주 @{} (지출 {})", email, symbol, quantity, quote.price(), cost);
        return new TradeResult(OrderView.from(order), portfolio.getCash());
    }

    /** 매도. 현재가로 즉시 체결하고 손익을 확정한다. */
    @Transactional
    public TradeResult sell(String email, String symbol, long quantity) {
        requireQuantity(quantity);
        Portfolio portfolio = findPortfolio(email);

        Holding holding = holdingRepository.findByPortfolioAndSymbol(portfolio, symbol)
                .orElseThrow(() -> new IllegalArgumentException("보유하지 않은 종목입니다: " + symbol));
        if (holding.getQuantity() < quantity) {
            throw new IllegalArgumentException(String.format(
                    "보유 수량이 부족합니다. 요청 %d주 / 보유 %d주", quantity, holding.getQuantity()));
        }

        Quote quote = currentQuote(symbol);
        double proceeds = PortfolioCalculator.sellProceeds(quantity, quote.price(), FEE_RATE, TAX_RATE);
        double profit = PortfolioCalculator.realizedProfit(quantity, holding.getAvgBuyPrice(), proceeds);

        portfolio.setCash(portfolio.getCash() + proceeds);
        portfolio.setRealizedProfit(portfolio.getRealizedProfit() + profit);

        // 전량 매도면 보유 종목에서 제거하고, 일부 매도면 평균 매입가는 유지한 채 수량만 줄인다
        if (holding.getQuantity() == quantity) {
            holdingRepository.delete(holding);
        } else {
            holding.setQuantity(holding.getQuantity() - quantity);
            holdingRepository.save(holding);
        }

        double gross = quantity * quote.price();
        TradeOrder order = record(portfolio, symbol, holding.getName(), TradeOrder.Side.SELL,
                quote.price(), quantity, gross * FEE_RATE, gross * TAX_RATE, proceeds, profit);

        log.info("모의 매도: {} {} {}주 @{} (수령 {}, 실현손익 {})",
                email, symbol, quantity, quote.price(), proceeds, profit);
        return new TradeResult(OrderView.from(order), portfolio.getCash());
    }

    /** 체결 내역 (최신순) */
    public List<OrderView> getOrders(String email) {
        List<OrderView> result = new ArrayList<>();
        for (TradeOrder o : tradeOrderRepository.findByPortfolioOrderByExecutedAtDesc(findPortfolio(email))) {
            result.add(OrderView.from(o));
        }
        return result;
    }

    private TradeOrder record(Portfolio portfolio, String symbol, String name, TradeOrder.Side side,
                              double price, long quantity, double fee, double tax,
                              double settlement, Double realizedProfit) {
        TradeOrder order = new TradeOrder();
        order.setPortfolio(portfolio);
        order.setSymbol(symbol);
        order.setName(name);
        order.setSide(side);
        order.setPrice(price);
        order.setQuantity(quantity);
        order.setFee(fee);
        order.setTax(tax);
        order.setSettlementAmount(settlement);
        order.setRealizedProfit(realizedProfit);
        order.setExecutedAt(LocalDateTime.now());
        return tradeOrderRepository.save(order);
    }

    private PortfolioSummary summarize(Portfolio portfolio) {
        List<HoldingView> views = new ArrayList<>();
        double stockValue = 0;
        double unrealized = 0;

        for (Holding h : holdingRepository.findByPortfolio(portfolio)) {
            double price = quoteQuietly(h.getSymbol());
            double eval = price * h.getQuantity();
            double cost = h.getAvgBuyPrice() * h.getQuantity();

            stockValue += eval;
            unrealized += eval - cost;
            views.add(new HoldingView(h.getSymbol(), h.getName(), h.getQuantity(),
                    h.getAvgBuyPrice(), price, eval, eval - cost,
                    PortfolioCalculator.returnPct(cost, eval)));
        }

        double totalValue = portfolio.getCash() + stockValue;
        return new PortfolioSummary(
                portfolio.getOpenedAt(), portfolio.getInitialCapital(), portfolio.getCash(),
                stockValue, totalValue,
                PortfolioCalculator.returnPct(portfolio.getInitialCapital(), totalValue),
                unrealized, portfolio.getRealizedProfit(), views);
    }

    /** 체결에 쓸 현재가. KIS 실시간 조회가 실패하면 마지막으로 저장된 가격을 쓴다. */
    private Quote currentQuote(String symbol) {
        String name = stockRepository.findBySymbol(symbol).map(Stock::getName).orElse(symbol);
        try {
            Stock stock = stockService.getStockPrice(symbol, name);
            if (stock.getPrice() != null && stock.getPrice() > 0) {
                return new Quote(stock.getPrice(), stock.getName());
            }
        } catch (Exception e) {
            log.warn("실시간 시세 조회 실패, 저장된 가격으로 대체: {} / {}", symbol, e.getMessage());
        }

        Double fallback = storedPrice(symbol);
        if (fallback == null) {
            throw new IllegalArgumentException("시세를 조회할 수 없는 종목입니다: " + symbol);
        }
        return new Quote(fallback, name);
    }

    /** 평가용 현재가. 조회에 실패해도 예외를 던지지 않고 마지막 가격을 쓴다. */
    private double quoteQuietly(String symbol) {
        Double stored = storedPrice(symbol);
        return stored == null ? 0d : stored;
    }

    private Double storedPrice(String symbol) {
        Double price = stockRepository.findBySymbol(symbol).map(Stock::getPrice).orElse(null);
        if (price != null && price > 0) return price;

        return stockPriceRepository.findTopBySymbolOrderByTradeDateDesc(symbol)
                .map(StockPrice::getClose)
                .orElse(null);
    }

    private Portfolio findPortfolio(String email) {
        return portfolioRepository.findByUser(findUser(email))
                .orElseThrow(() -> new IllegalArgumentException(
                        "모의투자 계좌가 없습니다. 먼저 계좌를 개설하세요."));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다: " + email));
    }

    private void requireQuantity(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 1주 이상이어야 합니다: " + quantity);
        }
    }

    /** 체결가와 종목명 */
    private record Quote(double price, String name) {}
}
