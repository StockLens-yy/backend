package com.example.Stock.service;

import com.example.Stock.dto.BacktestResult;
import com.example.Stock.dto.Trade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 백테스팅 엔진 검증.
 * <p>
 * 아래 시나리오의 기대값은 모두 손으로 계산한 값이다. 단기 2일선 / 장기 3일선 기준으로
 * 종가 [10,10,10,10,11,12,13,14,15,16,15,14,13,12,11,10] 을 넣으면
 * idx4 에 골든크로스, idx11 에 데드크로스가 발생한다.
 * 체결은 신호 다음 거래일이므로 idx5(종가 12)에 매수, idx12(종가 13)에 매도한다.
 */
class BacktestEngineTest {

    private static final double DELTA = 1e-6;
    private static final double CAPITAL = 1200d;

    /** 2026-01-01 부터 하루씩 증가하는 날짜 */
    private static List<LocalDate> datesOf(int count) {
        List<LocalDate> dates = new ArrayList<>(count);
        for (int i = 0; i < count; i++) dates.add(LocalDate.of(2026, 1, 1).plusDays(i));
        return dates;
    }

    private static final List<Double> UPTREND_THEN_DOWN = List.of(
            10.0, 10.0, 10.0, 10.0, 11.0, 12.0, 13.0, 14.0,
            15.0, 16.0, 15.0, 14.0, 13.0, 12.0, 11.0, 10.0);

    private static BacktestResult runDefault(List<Double> closes, double feeRate, double taxRate) {
        return BacktestEngine.run("TEST", datesOf(closes.size()), closes, null,
                2, 3, CAPITAL, feeRate, taxRate);
    }

    @Nested
    @DisplayName("수익 거래")
    class WinningTrade {

        @Test
        @DisplayName("골든크로스에 매수하고 데드크로스에 매도한다")
        void 골든크로스에_매수하고_데드크로스에_매도한다() {
            BacktestResult result = runDefault(UPTREND_THEN_DOWN, 0, 0);

            assertEquals(1, result.tradeCount());

            Trade trade = result.trades().get(0);
            assertEquals(12.0, trade.buyPrice(), DELTA);
            assertEquals(13.0, trade.sellPrice(), DELTA);
            assertEquals(100, trade.shares(), "자본 1200 / 종가 12 = 100주");
            assertEquals(100.0, trade.profit(), DELTA);
            assertEquals(8.333333, trade.returnPct(), 1e-5);
        }

        @Test
        @DisplayName("최종 자산과 누적 수익률을 계산한다")
        void 최종_자산과_누적_수익률을_계산한다() {
            BacktestResult result = runDefault(UPTREND_THEN_DOWN, 0, 0);

            assertEquals(1300.0, result.finalEquity(), DELTA);
            assertEquals(8.333333, result.totalReturnPct(), 1e-5);
            assertEquals(100.0, result.winRate(), DELTA);
            assertFalse(result.openPosition(), "매도까지 끝났으므로 보유 포지션이 없다");
        }

        @Test
        @DisplayName("고점 대비 최대낙폭(MDD)을 계산한다")
        void 고점_대비_최대낙폭을_계산한다() {
            // 자산 곡선 고점은 idx9 의 1600, 이후 매도 후 1300까지 내려간다 -> 300/1600
            BacktestResult result = runDefault(UPTREND_THEN_DOWN, 0, 0);

            assertEquals(18.75, result.maxDrawdownPct(), DELTA);
        }

        @Test
        @DisplayName("보유일수를 계산한다")
        void 보유일수를_계산한다() {
            BacktestResult result = runDefault(UPTREND_THEN_DOWN, 0, 0);

            // 2026-01-06 매수 -> 2026-01-13 매도
            assertEquals(LocalDate.of(2026, 1, 6), result.trades().get(0).buyDate());
            assertEquals(LocalDate.of(2026, 1, 13), result.trades().get(0).sellDate());
            assertEquals(7, result.trades().get(0).holdingDays());
            assertEquals(7.0, result.avgHoldingDays(), DELTA);
        }
    }

    @Nested
    @DisplayName("미래참조 방지")
    class LookAhead {

        @Test
        @DisplayName("신호 당일이 아니라 다음 거래일 종가로 체결한다")
        void 신호_당일이_아니라_다음_거래일_종가로_체결한다() {
            BacktestResult result = runDefault(UPTREND_THEN_DOWN, 0, 0);
            Trade trade = result.trades().get(0);

            // 골든크로스는 idx4(2026-01-05)에 발생했지만 체결은 idx5(01-06)
            assertNotEquals(LocalDate.of(2026, 1, 5), trade.buyDate(),
                    "신호 당일 종가로 체결하면 미래참조가 된다");
            assertEquals(LocalDate.of(2026, 1, 6), trade.buyDate());
            assertEquals(12.0, trade.buyPrice(), DELTA, "idx5 종가");
        }
    }

    @Nested
    @DisplayName("손실 거래")
    class LosingTrade {

        // idx4 골든크로스 -> idx5(12원) 매수, idx7 데드크로스 -> idx8(7원) 매도
        private final List<Double> crashAfterBuy = List.of(
                10.0, 10.0, 10.0, 10.0, 11.0, 12.0, 11.0, 9.0, 7.0, 5.0, 4.0, 3.0);

        @Test
        @DisplayName("손실로 끝나면 승률이 0이다")
        void 손실로_끝나면_승률이_0이다() {
            BacktestResult result = runDefault(crashAfterBuy, 0, 0);

            assertEquals(1, result.tradeCount());
            assertEquals(-500.0, result.trades().get(0).profit(), DELTA);   // 700 - 1200
            assertEquals(-41.666667, result.trades().get(0).returnPct(), 1e-5);
            assertEquals(0.0, result.winRate(), DELTA);
            assertEquals(700.0, result.finalEquity(), DELTA);
            assertEquals(-41.666667, result.totalReturnPct(), 1e-5);
            assertEquals(41.666667, result.maxDrawdownPct(), 1e-5);         // 고점 1200 -> 700
        }
    }

    @Nested
    @DisplayName("수수료와 세금")
    class Fees {

        @Test
        @DisplayName("수수료가 수익률을 깎는다")
        void 수수료가_수익률을_깎는다() {
            BacktestResult noFee = runDefault(UPTREND_THEN_DOWN, 0, 0);
            BacktestResult withFee = runDefault(UPTREND_THEN_DOWN, 0.01, 0);

            // 수수료 1%: 1200/(12*1.01) -> 99주, 매도 99*13*0.99 = 1274.13 (+잔여현금 0.12)
            assertEquals(99, withFee.trades().get(0).shares());
            assertEquals(1274.25, withFee.finalEquity(), 1e-9);
            assertEquals(6.1875, withFee.totalReturnPct(), 1e-9);
            assertTrue(withFee.totalReturnPct() < noFee.totalReturnPct());
        }

        @Test
        @DisplayName("거래세는 매도 시에만 반영된다")
        void 거래세는_매도_시에만_반영된다() {
            BacktestResult noTax = runDefault(UPTREND_THEN_DOWN, 0, 0);
            BacktestResult withTax = runDefault(UPTREND_THEN_DOWN, 0, 0.1);

            // 매수 주수는 그대로, 매도 대금만 10% 줄어든다
            assertEquals(noTax.trades().get(0).shares(), withTax.trades().get(0).shares());
            assertEquals(1300.0 * 0.9, withTax.finalEquity(), DELTA);
        }
    }

    @Nested
    @DisplayName("단순 보유 비교")
    class BuyAndHold {

        @Test
        @DisplayName("같은 구간을 보유했을 때의 수익률을 함께 준다")
        void 같은_구간을_보유했을_때의_수익률을_함께_준다() {
            // 첫 종가 10, 마지막 종가 10 -> 보유 수익률 0%
            BacktestResult result = runDefault(UPTREND_THEN_DOWN, 0, 0);

            assertEquals(0.0, result.buyAndHoldReturnPct(), DELTA);
            assertTrue(result.totalReturnPct() > result.buyAndHoldReturnPct(),
                    "이 구간에서는 전략이 단순 보유보다 낫다");
        }

        @Test
        @DisplayName("보유 자산곡선을 전략 곡선과 같은 길이로 함께 준다")
        void 보유_자산곡선을_전략_곡선과_같은_길이로_함께_준다() {
            BacktestResult result = runDefault(UPTREND_THEN_DOWN, 0, 0);

            assertEquals(result.equityCurve().size(), result.buyAndHoldCurve().size());
            // 자본 1200 / 첫 종가 10 = 120주 보유
            assertEquals(1200.0, result.buyAndHoldCurve().get(0).equity(), DELTA);
            assertEquals(1920.0, result.buyAndHoldCurve().get(9).equity(), DELTA, "idx9 종가 16");

            // 곡선의 마지막 값이 보고된 보유 수익률과 일치해야 한다
            double last = result.buyAndHoldCurve().get(result.buyAndHoldCurve().size() - 1).equity();
            assertEquals(result.buyAndHoldReturnPct(), (last / CAPITAL - 1) * 100, DELTA);
        }
    }

    @Nested
    @DisplayName("워밍업 구간")
    class Warmup {

        @Test
        @DisplayName("시작일 앞 데이터는 지표 계산에만 쓰고 자산곡선에서 제외한다")
        void 시작일_앞_데이터는_지표_계산에만_쓴다() {
            LocalDate simulateFrom = LocalDate.of(2026, 1, 6);   // idx5

            BacktestResult result = BacktestEngine.run("TEST",
                    datesOf(UPTREND_THEN_DOWN.size()), UPTREND_THEN_DOWN, simulateFrom,
                    2, 3, CAPITAL, 0, 0);

            assertEquals(simulateFrom, result.from());
            assertEquals(11, result.equityCurve().size(), "16개 중 앞 5개는 제외");
            assertEquals(simulateFrom, result.equityCurve().get(0).date());

            // 시작일 직전(idx4)에 난 골든크로스가 첫날 체결로 반영된다
            assertEquals(1, result.tradeCount());
            assertEquals(simulateFrom, result.trades().get(0).buyDate());

            // 보유 비교도 시뮬레이션 구간(12 -> 10) 기준으로 계산된다
            assertEquals(-16.666667, result.buyAndHoldReturnPct(), 1e-5);
        }

        @Test
        @DisplayName("전체 구간을 돌리면 자산곡선 길이가 데이터 길이와 같다")
        void 전체_구간을_돌리면_자산곡선_길이가_데이터_길이와_같다() {
            BacktestResult result = runDefault(UPTREND_THEN_DOWN, 0, 0);

            assertEquals(UPTREND_THEN_DOWN.size(), result.equityCurve().size());
        }
    }

    @Nested
    @DisplayName("거래가 없는 경우")
    class NoTrade {

        @Test
        @DisplayName("교차가 없으면 자본이 그대로 남는다")
        void 교차가_없으면_자본이_그대로_남는다() {
            List<Double> flat = List.of(10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0);

            BacktestResult result = runDefault(flat, 0, 0);

            assertEquals(0, result.tradeCount());
            assertEquals(CAPITAL, result.finalEquity(), DELTA);
            assertEquals(0.0, result.totalReturnPct(), DELTA);
            assertEquals(0.0, result.maxDrawdownPct(), DELTA);
            assertEquals(0.0, result.winRate(), DELTA);
            assertEquals(0.0, result.avgHoldingDays(), DELTA);
        }

        @Test
        @DisplayName("데이터가 없으면 초기 자본을 그대로 반환한다")
        void 데이터가_없으면_초기_자본을_그대로_반환한다() {
            BacktestResult result = BacktestEngine.run("TEST", List.of(), List.of(), null,
                    2, 3, CAPITAL, 0, 0);

            assertEquals(0, result.tradeCount());
            assertEquals(CAPITAL, result.finalEquity(), DELTA);
            assertTrue(result.equityCurve().isEmpty());
        }
    }

    @Nested
    @DisplayName("파라미터 검증")
    class Validation {

        @Test
        @DisplayName("단기 기간이 장기 기간보다 크거나 같으면 예외")
        void 단기_기간이_장기보다_크거나_같으면_예외() {
            assertThrows(IllegalArgumentException.class, () -> BacktestEngine.run(
                    "TEST", datesOf(3), List.of(1.0, 2.0, 3.0), null, 5, 5, CAPITAL, 0, 0));
        }

        @Test
        @DisplayName("초기 자본이 0 이하면 예외")
        void 초기_자본이_0_이하면_예외() {
            assertThrows(IllegalArgumentException.class, () -> BacktestEngine.run(
                    "TEST", datesOf(3), List.of(1.0, 2.0, 3.0), null, 2, 3, 0, 0, 0));
        }

        @Test
        @DisplayName("날짜와 종가 길이가 다르면 예외")
        void 날짜와_종가_길이가_다르면_예외() {
            assertThrows(IllegalArgumentException.class, () -> BacktestEngine.run(
                    "TEST", datesOf(2), List.of(1.0, 2.0, 3.0), null, 2, 3, CAPITAL, 0, 0));
        }

        @Test
        @DisplayName("수수료율이 음수면 예외")
        void 수수료율이_음수면_예외() {
            assertThrows(IllegalArgumentException.class, () -> BacktestEngine.run(
                    "TEST", datesOf(3), List.of(1.0, 2.0, 3.0), null, 2, 3, CAPITAL, -0.1, 0));
        }
    }
}
