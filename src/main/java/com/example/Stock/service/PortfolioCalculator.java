package com.example.Stock.service;

/**
 * 모의투자 정산 계산. 외부 의존이 없는 순수 함수라 단위 테스트로 검증한다.
 * <p>
 * 평균 매입가는 매수 수수료를 포함한 단가(총 매입비용 / 총 수량)로 관리한다.
 * 그래야 매도 시 실현손익에 매수 수수료가 자동으로 반영된다.
 */
public final class PortfolioCalculator {

    private PortfolioCalculator() {
    }

    /** 매수 시 실제 지출액. 체결금액에 수수료를 더한다. */
    public static double buyCost(long quantity, double price, double feeRate) {
        requirePositiveQuantity(quantity);
        requirePositivePrice(price);
        requireRate(feeRate, "수수료율");
        return quantity * price * (1 + feeRate);
    }

    /** 매도 시 실제 수령액. 체결금액에서 수수료와 거래세를 뺀다. */
    public static double sellProceeds(long quantity, double price, double feeRate, double taxRate) {
        requirePositiveQuantity(quantity);
        requirePositivePrice(price);
        requireRate(feeRate, "수수료율");
        requireRate(taxRate, "거래세율");
        return quantity * price * (1 - feeRate - taxRate);
    }

    /** 예수금으로 살 수 있는 최대 수량. 주식은 정수 단위로만 거래된다. */
    public static long maxBuyableQuantity(double cash, double price, double feeRate) {
        requirePositivePrice(price);
        requireRate(feeRate, "수수료율");
        if (cash <= 0) return 0;
        return (long) Math.floor(cash / (price * (1 + feeRate)));
    }

    /**
     * 추가 매수 후의 평균 매입가.
     * 기존 보유분과 신규 매수분의 매입비용을 합쳐 총 수량으로 나눈다.
     *
     * @param heldQuantity 기존 보유 수량 (0 이면 신규 매수)
     * @param heldAvgPrice 기존 평균 매입가
     * @param buyQuantity  이번에 매수한 수량
     * @param buyCost      이번 매수의 실제 지출액 (수수료 포함)
     */
    public static double newAveragePrice(long heldQuantity, double heldAvgPrice,
                                         long buyQuantity, double buyCost) {
        requirePositiveQuantity(buyQuantity);
        if (heldQuantity < 0) {
            throw new IllegalArgumentException("보유 수량은 음수일 수 없습니다: " + heldQuantity);
        }
        double totalCost = heldQuantity * heldAvgPrice + buyCost;
        return totalCost / (heldQuantity + buyQuantity);
    }

    /**
     * 매도로 확정되는 손익.
     * 실제 수령액에서 그 수량의 매입원가(평균 매입가 × 수량)를 뺀다.
     */
    public static double realizedProfit(long quantity, double avgBuyPrice, double sellProceeds) {
        requirePositiveQuantity(quantity);
        return sellProceeds - avgBuyPrice * quantity;
    }

    /** 수익률(%). 기준 금액이 0 이하면 0 을 반환한다. */
    public static double returnPct(double base, double current) {
        if (base <= 0) return 0;
        return (current / base - 1) * 100;
    }

    private static void requirePositiveQuantity(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 1주 이상이어야 합니다: " + quantity);
        }
    }

    private static void requirePositivePrice(double price) {
        if (price <= 0) {
            throw new IllegalArgumentException("가격은 0보다 커야 합니다: " + price);
        }
    }

    private static void requireRate(double rate, String label) {
        if (rate < 0) {
            throw new IllegalArgumentException(label + "은 0 이상이어야 합니다: " + rate);
        }
    }
}
