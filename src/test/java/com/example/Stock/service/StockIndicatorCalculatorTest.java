package com.example.Stock.service;

import com.example.Stock.service.StockIndicatorCalculator.Position;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 브리핑 지표 계산 검증. 기대값은 손으로 계산한 값(반올림 후)을 그대로 박아둔다.
 * BigDecimal 은 scale 까지 비교되도록 문자열로 기대값을 만든다.
 */
class StockIndicatorCalculatorTest {

    private static List<BigDecimal> prices(String... values) {
        return Arrays.stream(values).map(BigDecimal::new).toList();
    }

    private static List<BigDecimal> flat(int count, String value) {
        return Collections.nCopies(count, new BigDecimal(value));
    }

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    @Nested
    @DisplayName("이동평균")
    class MovingAverage {

        @Test
        @DisplayName("마지막 N개 값의 평균을 원 단위로 반올림한다")
        void 마지막_N개_평균() {
            List<BigDecimal> closes = prices("100", "200", "300", "400", "500", "601");

            // (200+300+400+500+601)/5 = 400.2 → 400
            assertEquals(d("400"), StockIndicatorCalculator.sma(closes, 5));
        }

        @Test
        @DisplayName("0.5는 올림(HALF_UP)한다")
        void 반올림_HALF_UP() {
            // (1000+1001)/2 = 1000.5 → 1001
            assertEquals(d("1001"), StockIndicatorCalculator.sma(prices("1000", "1001"), 2));
        }

        @Test
        @DisplayName("괴리율은 반올림 전 평균으로 계산한다")
        void 괴리율() {
            List<BigDecimal> closes = prices("100", "100", "100", "100", "110");
            // 평균 102, (110-102)/102×100 = 7.843... → 7.84
            assertEquals(d("7.84"), StockIndicatorCalculator.gapRate(d("110"), closes, 5));
            assertEquals(Position.ABOVE, StockIndicatorCalculator.position(d("110"), closes, 5));
            assertEquals(Position.BELOW, StockIndicatorCalculator.position(d("90"), closes, 5));
            assertEquals(Position.EQUAL, StockIndicatorCalculator.position(d("102"), closes, 5));
        }

        @Test
        @DisplayName("기간보다 데이터가 적으면 null")
        void 데이터_부족() {
            List<BigDecimal> closes = flat(19, "1000");

            assertNull(StockIndicatorCalculator.sma(closes, 20));
            assertNull(StockIndicatorCalculator.gapRate(d("1000"), closes, 20));
            assertNull(StockIndicatorCalculator.position(d("1000"), closes, 20));
            assertNotNull(StockIndicatorCalculator.sma(closes, 5));
        }
    }

    @Nested
    @DisplayName("전일 대비 등락률")
    class ChangeRate {

        @Test
        @DisplayName("마지막 두 값으로 계산하고 소수 둘째 자리로 반올림한다")
        void 등락률() {
            // (217500-210000)/210000×100 = 3.5714... → 3.57
            assertEquals(d("3.57"), StockIndicatorCalculator.changeRate(prices("210000", "217500")));
            // (99-100)/100×100 = -1
            assertEquals(d("-1.00"), StockIndicatorCalculator.changeRate(prices("100", "99")));
        }

        @Test
        @DisplayName("값이 하나뿐이면 null")
        void 값_하나() {
            assertNull(StockIndicatorCalculator.changeRate(prices("100")));
            assertNull(StockIndicatorCalculator.changeRate(List.of()));
        }
    }

    @Nested
    @DisplayName("RSI(14)")
    class Rsi {

        // Wilder RSI 예제로 널리 쓰이는 종가. 기대값은 중간 평균을 반올림하지 않고 계산한 값이다.
        // (중간값을 반올림하는 자료에서는 첫 RSI 를 70.53 으로 표기하기도 한다)
        private final List<BigDecimal> closes = prices(
                "44.34", "44.09", "44.15", "43.61", "44.33", "44.83", "45.10", "45.42", "45.84", "46.08",
                "45.89", "46.03", "45.61", "46.28", "46.28", "46.00", "46.03", "46.41", "46.22", "45.64");

        @Test
        @DisplayName("값이 정확히 15개면 첫 평균만으로 계산한다")
        void 첫_RSI() {
            assertEquals(d("70.46"), StockIndicatorCalculator.rsi(closes.subList(0, 15), 14));
        }

        @Test
        @DisplayName("이후 값은 Wilder 평활로 갱신한다")
        void 평활() {
            assertEquals(d("66.25"), StockIndicatorCalculator.rsi(closes.subList(0, 16), 14));
            assertEquals(d("57.92"), StockIndicatorCalculator.rsi(closes, 14));
        }

        @Test
        @DisplayName("상승만 있으면 100, 하락만 있으면 0, 변화가 없으면 50")
        void 극단값() {
            List<BigDecimal> up = new ArrayList<>();
            List<BigDecimal> down = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                up.add(BigDecimal.valueOf(100 + i));
                down.add(BigDecimal.valueOf(100 - i));
            }
            assertEquals(d("100.00"), StockIndicatorCalculator.rsi(up, 14));
            assertEquals(d("0.00"), StockIndicatorCalculator.rsi(down, 14));
            assertEquals(d("50.00"), StockIndicatorCalculator.rsi(flat(15, "100"), 14));
        }

        @Test
        @DisplayName("70 이상 과열, 30 이하 침체, 그 사이 중립")
        void 구간() {
            assertEquals(StockIndicatorCalculator.RsiZone.OVERBOUGHT, StockIndicatorCalculator.rsiZone(d("70.00")));
            assertEquals(StockIndicatorCalculator.RsiZone.NEUTRAL, StockIndicatorCalculator.rsiZone(d("31.96")));
            assertEquals(StockIndicatorCalculator.RsiZone.OVERSOLD, StockIndicatorCalculator.rsiZone(d("30.00")));
            assertNull(StockIndicatorCalculator.rsiZone(null));
        }

        @Test
        @DisplayName("값이 15개 미만이면 null")
        void 데이터_부족() {
            assertNull(StockIndicatorCalculator.rsi(closes.subList(0, 14), 14));
            assertNull(StockIndicatorCalculator.rsi(List.of(), 14));
            assertNull(StockIndicatorCalculator.rsi(null, 14));
        }
    }

    @Nested
    @DisplayName("거래량")
    class Volume {

        @Test
        @DisplayName("당일을 뺀 직전 20일 평균과 비교한다")
        void 배율() {
            List<Long> volumes = new ArrayList<>(Collections.nCopies(20, 1_000L));
            volumes.add(0, 999_999L); // 21일 전 값은 계산에 쓰이지 않아야 한다
            volumes.add(2_500L);      // 당일

            assertEquals(d("1000"), StockIndicatorCalculator.averageVolume(volumes, 20));
            assertEquals(d("2.50"), StockIndicatorCalculator.volumeRatio(volumes, 20));
        }

        @Test
        @DisplayName("직전 20일이 채워지지 않거나 평균이 0이면 null")
        void 데이터_부족() {
            List<Long> short20 = new ArrayList<>(Collections.nCopies(20, 1_000L)); // 당일 포함 20개뿐
            assertNull(StockIndicatorCalculator.averageVolume(short20, 20));
            assertNull(StockIndicatorCalculator.volumeRatio(short20, 20));

            List<Long> zeros = new ArrayList<>(Collections.nCopies(21, 0L));
            assertNull(StockIndicatorCalculator.volumeRatio(zeros, 20));
        }
    }

    @Nested
    @DisplayName("52주 범위 내 위치")
    class Range {

        @Test
        @DisplayName("최저가는 0%, 최고가는 100%")
        void 위치() {
            // (52300-41200)/(61800-41200)×100 = 53.883... → 53.88
            assertEquals(d("53.88"), StockIndicatorCalculator.rangePosition(d("52300"), d("41200"), d("61800")));
            assertEquals(d("0.00"), StockIndicatorCalculator.rangePosition(d("41200"), d("41200"), d("61800")));
            assertEquals(d("100.00"), StockIndicatorCalculator.rangePosition(d("61800"), d("41200"), d("61800")));
        }

        @Test
        @DisplayName("30% 미만 하단, 70% 초과 상단, 그 사이 중간")
        void 구간() {
            assertEquals(StockIndicatorCalculator.RangeZone.LOW, StockIndicatorCalculator.rangeZone(d("22.28")));
            assertEquals(StockIndicatorCalculator.RangeZone.MIDDLE, StockIndicatorCalculator.rangeZone(d("30.00")));
            assertEquals(StockIndicatorCalculator.RangeZone.MIDDLE, StockIndicatorCalculator.rangeZone(d("70.00")));
            assertEquals(StockIndicatorCalculator.RangeZone.HIGH, StockIndicatorCalculator.rangeZone(d("70.01")));
            assertNull(StockIndicatorCalculator.rangeZone(null));
        }

        @Test
        @DisplayName("최고가와 최저가가 같거나 값이 없으면 null")
        void 범위_없음() {
            assertNull(StockIndicatorCalculator.rangePosition(d("100"), d("100"), d("100")));
            assertNull(StockIndicatorCalculator.rangePosition(d("100"), null, d("200")));
        }
    }

    @Test
    @DisplayName("상장 10일 종목: 5일 지표만 계산되고 나머지는 null, 예외 없음")
    void 상장_초기_종목() {
        List<BigDecimal> closes = flat(10, "5000");
        List<Long> volumes = new ArrayList<>(Collections.nCopies(10, 100L));

        assertDoesNotThrow(() -> {
            assertNotNull(StockIndicatorCalculator.sma(closes, 5));
            assertNull(StockIndicatorCalculator.sma(closes, 20));
            assertNull(StockIndicatorCalculator.rsi(closes, 14));
            assertNull(StockIndicatorCalculator.volumeRatio(volumes, 20));
        });
    }
}
