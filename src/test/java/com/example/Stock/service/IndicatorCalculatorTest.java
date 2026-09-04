package com.example.Stock.service;

import com.example.Stock.dto.BollingerBand;
import com.example.Stock.dto.CrossSignal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 지표 계산 검증. 기대값은 손으로 계산한 값을 그대로 박아둔다.
 */
class IndicatorCalculatorTest {

    private static final double DELTA = 1e-6;

    @Nested
    @DisplayName("단순이동평균(SMA)")
    class Sma {

        @Test
        @DisplayName("기간이 채워진 지점부터 평균을 낸다")
        void 기간이_채워진_지점부터_평균을_낸다() {
            List<Double> values = List.of(1.0, 2.0, 3.0, 4.0, 5.0);

            List<Double> result = IndicatorCalculator.sma(values, 3);

            assertEquals(5, result.size(), "입력과 같은 길이여야 한다");
            assertNull(result.get(0));
            assertNull(result.get(1));
            assertEquals(2.0, result.get(2), DELTA);   // (1+2+3)/3
            assertEquals(3.0, result.get(3), DELTA);   // (2+3+4)/3
            assertEquals(4.0, result.get(4), DELTA);   // (3+4+5)/3
        }

        @Test
        @DisplayName("기간이 데이터보다 길면 전부 null")
        void 기간이_데이터보다_길면_전부_null() {
            List<Double> result = IndicatorCalculator.sma(List.of(1.0, 2.0), 5);

            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(v -> v == null));
        }

        @Test
        @DisplayName("구간에 null이 섞이면 그 지점은 null")
        void 구간에_null이_섞이면_그_지점은_null() {
            List<Double> values = Arrays.asList(1.0, null, 3.0, 4.0, 5.0);

            List<Double> result = IndicatorCalculator.sma(values, 3);

            assertNull(result.get(2));                 // 1, null, 3
            assertNull(result.get(3));                 // null, 3, 4
            assertEquals(4.0, result.get(4), DELTA);   // 3, 4, 5 - 정상 복귀
        }

        @Test
        @DisplayName("빈 입력은 빈 결과")
        void 빈_입력은_빈_결과() {
            assertTrue(IndicatorCalculator.sma(List.of(), 5).isEmpty());
        }

        @Test
        @DisplayName("기간이 0 이하면 예외")
        void 기간이_0_이하면_예외() {
            assertThrows(IllegalArgumentException.class,
                    () -> IndicatorCalculator.sma(List.of(1.0, 2.0), 0));
        }
    }

    @Nested
    @DisplayName("RSI")
    class Rsi {

        @Test
        @DisplayName("Wilder 방식으로 계산한다")
        void Wilder_방식으로_계산한다() {
            // 변화: +1, +1, -1 -> avgGain=2/3, avgLoss=1/3, RS=2
            // RSI = 100 - 100/(1+2) = 66.666...
            List<Double> result = IndicatorCalculator.rsi(List.of(10.0, 11.0, 12.0, 11.0), 3);

            assertNull(result.get(2), "period 이전 구간은 null");
            assertEquals(66.666667, result.get(3), 1e-5);
        }

        @Test
        @DisplayName("두 번째 값부터 이전 평균을 평활해 계산한다")
        void 두_번째_값부터_이전_평균을_평활해_계산한다() {
            // 이어서 +2 -> avgGain=(2/3*2+2)/3=1.111..., avgLoss=(1/3*2+0)/3=0.222...
            // RS=5 -> RSI = 100 - 100/6 = 83.333...
            List<Double> result = IndicatorCalculator.rsi(List.of(10.0, 11.0, 12.0, 11.0, 13.0), 3);

            assertEquals(83.333333, result.get(4), 1e-5);
        }

        @Test
        @DisplayName("계속 오르면 100, 계속 내리면 0")
        void 계속_오르면_100_계속_내리면_0() {
            List<Double> up = IndicatorCalculator.rsi(List.of(1.0, 2.0, 3.0, 4.0, 5.0), 3);
            List<Double> down = IndicatorCalculator.rsi(List.of(5.0, 4.0, 3.0, 2.0, 1.0), 3);

            assertEquals(100.0, up.get(4), DELTA);
            assertEquals(0.0, down.get(4), DELTA);
        }

        @Test
        @DisplayName("가격 변동이 없으면 중립값 50")
        void 가격_변동이_없으면_중립값_50() {
            List<Double> result = IndicatorCalculator.rsi(List.of(7.0, 7.0, 7.0, 7.0, 7.0), 3);

            assertEquals(50.0, result.get(4), DELTA);
        }

        @Test
        @DisplayName("데이터가 기간보다 적으면 전부 null")
        void 데이터가_기간보다_적으면_전부_null() {
            List<Double> result = IndicatorCalculator.rsi(List.of(1.0, 2.0, 3.0), 3);

            assertEquals(3, result.size());
            assertTrue(result.stream().allMatch(v -> v == null));
        }
    }

    @Nested
    @DisplayName("볼린저밴드")
    class Bollinger {

        @Test
        @DisplayName("중심선 ± k×표준편차를 반환한다")
        void 중심선_표준편차를_반환한다() {
            // [1,2,3,4] 평균 2.5, 모분산 1.25, 표준편차 1.118034
            List<BollingerBand> result = IndicatorCalculator.bollinger(List.of(1.0, 2.0, 3.0, 4.0), 4, 2.0);

            BollingerBand band = result.get(3);
            assertEquals(2.5, band.middle(), DELTA);
            assertEquals(4.736068, band.upper(), 1e-5);
            assertEquals(0.263932, band.lower(), 1e-5);
        }

        @Test
        @DisplayName("가격이 일정하면 세 선이 겹친다")
        void 가격이_일정하면_세_선이_겹친다() {
            List<BollingerBand> result = IndicatorCalculator.bollinger(List.of(5.0, 5.0, 5.0), 3, 2.0);

            BollingerBand band = result.get(2);
            assertEquals(5.0, band.middle(), DELTA);
            assertEquals(5.0, band.upper(), DELTA);
            assertEquals(5.0, band.lower(), DELTA);
        }

        @Test
        @DisplayName("구간이 부족한 앞부분은 값이 없다")
        void 구간이_부족한_앞부분은_값이_없다() {
            List<BollingerBand> result = IndicatorCalculator.bollinger(List.of(1.0, 2.0, 3.0), 3, 2.0);

            assertNull(result.get(0).middle());
            assertNull(result.get(1).upper());
            assertNotNull(result.get(2).middle());
        }
    }

    @Nested
    @DisplayName("이동평균 교차")
    class Crosses {

        private final List<LocalDate> dates = List.of(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 2),
                LocalDate.of(2026, 1, 3),
                LocalDate.of(2026, 1, 4),
                LocalDate.of(2026, 1, 5));

        @Test
        @DisplayName("상향 돌파는 골든, 하향 돌파는 데드로 잡는다")
        void 상향_돌파는_골든_하향_돌파는_데드로_잡는다() {
            List<Double> shortMa = List.of(1.0, 1.0, 3.0, 3.0, 1.0);
            List<Double> longMa = List.of(2.0, 2.0, 2.0, 2.0, 2.0);

            List<CrossSignal> signals = IndicatorCalculator.detectCrosses(dates, shortMa, longMa);

            assertEquals(2, signals.size());
            assertEquals(CrossSignal.Type.GOLDEN, signals.get(0).type());
            assertEquals(LocalDate.of(2026, 1, 3), signals.get(0).date());
            assertEquals(CrossSignal.Type.DEAD, signals.get(1).type());
            assertEquals(LocalDate.of(2026, 1, 5), signals.get(1).date());
        }

        @Test
        @DisplayName("교차가 없으면 신호가 없다")
        void 교차가_없으면_신호가_없다() {
            List<Double> shortMa = List.of(3.0, 3.1, 3.2, 3.3, 3.4);
            List<Double> longMa = List.of(1.0, 1.1, 1.2, 1.3, 1.4);

            assertTrue(IndicatorCalculator.detectCrosses(dates, shortMa, longMa).isEmpty());
        }

        @Test
        @DisplayName("이동평균이 아직 없는 구간은 건너뛴다")
        void 이동평균이_아직_없는_구간은_건너뛴다() {
            List<Double> shortMa = Arrays.asList(null, null, 3.0, 3.0, 1.0);
            List<Double> longMa = Arrays.asList(null, null, 2.0, 2.0, 2.0);

            List<CrossSignal> signals = IndicatorCalculator.detectCrosses(dates, shortMa, longMa);

            // null 구간에서는 돌파 판단 불가 -> 데드크로스 하나만 남는다
            assertEquals(1, signals.size());
            assertEquals(CrossSignal.Type.DEAD, signals.get(0).type());
        }

        @Test
        @DisplayName("길이가 다르면 예외")
        void 길이가_다르면_예외() {
            assertThrows(IllegalArgumentException.class, () -> IndicatorCalculator.detectCrosses(
                    dates, List.of(1.0), List.of(1.0, 2.0)));
        }
    }
}
