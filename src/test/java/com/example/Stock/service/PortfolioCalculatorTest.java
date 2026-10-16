package com.example.Stock.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 모의투자 정산 계산 검증. 기대값은 손으로 계산한 값을 그대로 박아둔다.
 * 수수료 0.015%, 거래세 0.18% 를 기준으로 한다.
 */
class PortfolioCalculatorTest {

    private static final double DELTA = 1e-6;
    private static final double FEE = 0.00015;
    private static final double TAX = 0.0018;

    @Nested
    @DisplayName("매수 정산")
    class Buy {

        @Test
        @DisplayName("체결금액에 수수료를 더한 금액이 지출된다")
        void 체결금액에_수수료를_더한_금액이_지출된다() {
            // 10주 x 10,000원 = 100,000원, 수수료 0.015% = 15원
            assertEquals(100_015.0, PortfolioCalculator.buyCost(10, 10_000, FEE), DELTA);
        }

        @Test
        @DisplayName("수수료가 0이면 체결금액과 같다")
        void 수수료가_0이면_체결금액과_같다() {
            assertEquals(100_000.0, PortfolioCalculator.buyCost(10, 10_000, 0), DELTA);
        }

        @Test
        @DisplayName("예수금으로 살 수 있는 최대 수량은 정수로 내림한다")
        void 예수금으로_살_수_있는_최대_수량은_정수로_내림한다() {
            // 1,000,000 / (10,000 x 1.00015) = 99.985 -> 99주
            assertEquals(99, PortfolioCalculator.maxBuyableQuantity(1_000_000, 10_000, FEE));
            assertEquals(100, PortfolioCalculator.maxBuyableQuantity(1_000_000, 10_000, 0));
        }

        @Test
        @DisplayName("예수금이 한 주 값에 못 미치면 0주")
        void 예수금이_한_주_값에_못_미치면_0주() {
            assertEquals(0, PortfolioCalculator.maxBuyableQuantity(9_999, 10_000, FEE));
            assertEquals(0, PortfolioCalculator.maxBuyableQuantity(0, 10_000, FEE));
        }
    }

    @Nested
    @DisplayName("매도 정산")
    class Sell {

        @Test
        @DisplayName("체결금액에서 수수료와 거래세를 뺀 금액을 받는다")
        void 체결금액에서_수수료와_거래세를_뺀_금액을_받는다() {
            // 100,000 x (1 - 0.00015 - 0.0018) = 99,805원
            assertEquals(99_805.0, PortfolioCalculator.sellProceeds(10, 10_000, FEE, TAX), DELTA);
        }

        @Test
        @DisplayName("거래세는 매도에만 적용되므로 매수 지출보다 적게 받는다")
        void 거래세는_매도에만_적용된다() {
            double cost = PortfolioCalculator.buyCost(10, 10_000, FEE);
            double proceeds = PortfolioCalculator.sellProceeds(10, 10_000, FEE, TAX);

            assertTrue(proceeds < cost, "같은 가격에 사고팔면 비용만큼 손해다");
            assertEquals(210.0, cost - proceeds, DELTA);
        }
    }

    @Nested
    @DisplayName("평균 매입가")
    class AveragePrice {

        @Test
        @DisplayName("신규 매수면 지출액을 수량으로 나눈 값이다")
        void 신규_매수면_지출액을_수량으로_나눈_값이다() {
            double cost = PortfolioCalculator.buyCost(10, 10_000, FEE);   // 100,015

            assertEquals(10_001.5, PortfolioCalculator.newAveragePrice(0, 0, 10, cost), DELTA);
        }

        @Test
        @DisplayName("추가 매수하면 기존 매입비용과 합산해 다시 계산한다")
        void 추가_매수하면_기존_매입비용과_합산해_다시_계산한다() {
            // 기존 10주 @10,001.5 (매입비용 100,015) + 신규 10주 @20,000 (지출 200,030)
            // -> 300,045 / 20주 = 15,002.25
            double cost = PortfolioCalculator.buyCost(10, 20_000, FEE);

            assertEquals(15_002.25,
                    PortfolioCalculator.newAveragePrice(10, 10_001.5, 10, cost), DELTA);
        }

        @Test
        @DisplayName("평균가에 매수 수수료가 포함된다")
        void 평균가에_매수_수수료가_포함된다() {
            double withFee = PortfolioCalculator.newAveragePrice(
                    0, 0, 10, PortfolioCalculator.buyCost(10, 10_000, FEE));
            double withoutFee = PortfolioCalculator.newAveragePrice(
                    0, 0, 10, PortfolioCalculator.buyCost(10, 10_000, 0));

            assertTrue(withFee > withoutFee);
            assertEquals(10_000.0, withoutFee, DELTA);
        }
    }

    @Nested
    @DisplayName("실현손익")
    class RealizedProfit {

        @Test
        @DisplayName("오른 가격에 팔면 비용을 빼고도 이익이 남는다")
        void 오른_가격에_팔면_이익이_남는다() {
            // 10주를 10,000원에 매수(지출 100,015) 후 10,500원에 매도(수령 104,795.25)
            double avg = PortfolioCalculator.newAveragePrice(
                    0, 0, 10, PortfolioCalculator.buyCost(10, 10_000, FEE));
            double proceeds = PortfolioCalculator.sellProceeds(10, 10_500, FEE, TAX);

            assertEquals(104_795.25, proceeds, DELTA);
            assertEquals(4_780.25, PortfolioCalculator.realizedProfit(10, avg, proceeds), DELTA);
        }

        @Test
        @DisplayName("같은 가격에 팔면 수수료와 세금만큼 손실이다")
        void 같은_가격에_팔면_비용만큼_손실이다() {
            double avg = PortfolioCalculator.newAveragePrice(
                    0, 0, 10, PortfolioCalculator.buyCost(10, 10_000, FEE));
            double proceeds = PortfolioCalculator.sellProceeds(10, 10_000, FEE, TAX);

            assertEquals(-210.0, PortfolioCalculator.realizedProfit(10, avg, proceeds), DELTA);
        }

        @Test
        @DisplayName("일부만 팔면 판 수량만큼의 손익이 확정된다")
        void 일부만_팔면_판_수량만큼의_손익이_확정된다() {
            double avg = 10_000;
            double proceeds = PortfolioCalculator.sellProceeds(5, 12_000, 0, 0);   // 60,000

            assertEquals(10_000.0, PortfolioCalculator.realizedProfit(5, avg, proceeds), DELTA);
        }
    }

    @Nested
    @DisplayName("수익률")
    class ReturnRate {

        @Test
        @DisplayName("기준 금액 대비 증감을 퍼센트로 준다")
        void 기준_금액_대비_증감을_퍼센트로_준다() {
            assertEquals(10.0, PortfolioCalculator.returnPct(10_000_000, 11_000_000), DELTA);
            assertEquals(-25.0, PortfolioCalculator.returnPct(10_000_000, 7_500_000), DELTA);
            assertEquals(0.0, PortfolioCalculator.returnPct(10_000_000, 10_000_000), DELTA);
        }

        @Test
        @DisplayName("기준 금액이 0이면 0을 반환한다")
        void 기준_금액이_0이면_0을_반환한다() {
            assertEquals(0.0, PortfolioCalculator.returnPct(0, 1_000_000), DELTA);
        }
    }

    @Nested
    @DisplayName("잘못된 입력")
    class Validation {

        @Test
        @DisplayName("수량이 0 이하면 예외")
        void 수량이_0_이하면_예외() {
            assertThrows(IllegalArgumentException.class,
                    () -> PortfolioCalculator.buyCost(0, 10_000, FEE));
            assertThrows(IllegalArgumentException.class,
                    () -> PortfolioCalculator.sellProceeds(-1, 10_000, FEE, TAX));
        }

        @Test
        @DisplayName("가격이 0 이하면 예외")
        void 가격이_0_이하면_예외() {
            assertThrows(IllegalArgumentException.class,
                    () -> PortfolioCalculator.buyCost(10, 0, FEE));
            assertThrows(IllegalArgumentException.class,
                    () -> PortfolioCalculator.maxBuyableQuantity(1_000_000, -100, FEE));
        }

        @Test
        @DisplayName("수수료율이 음수면 예외")
        void 수수료율이_음수면_예외() {
            assertThrows(IllegalArgumentException.class,
                    () -> PortfolioCalculator.buyCost(10, 10_000, -0.01));
        }

        @Test
        @DisplayName("보유 수량이 음수면 예외")
        void 보유_수량이_음수면_예외() {
            assertThrows(IllegalArgumentException.class,
                    () -> PortfolioCalculator.newAveragePrice(-1, 10_000, 10, 100_000));
        }
    }
}
