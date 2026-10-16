package com.example.Stock.service;

import com.example.Stock.dto.StockIndicatorDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.example.Stock.service.StockIndicatorCalculator.*;

/**
 * 브리핑 입력 지표를 만든다. KIS 현재가 1회 + 일봉 1회(최근 약 100거래일)를 조회해
 * {@link StockIndicatorCalculator} 로 계산한다.
 * <p>
 * 52주 최고·최저가는 일봉 100건으로는 계산할 수 없어 KIS 현재가 응답의 값(w52_hgpr/w52_lwpr)을 쓰고,
 * 범위 내 위치(%)만 직접 계산한다.
 */
@Service
@RequiredArgsConstructor
public class StockIndicatorService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter KIS_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 일봉 조회 구간(달력일). KIS 는 한 번에 최대 100거래일만 주므로 그보다 조금 넓게 잡는다 */
    private static final int CANDLE_LOOKBACK_DAYS = 150;

    private static final int MA_SHORT = 5;
    private static final int MA_MID = 20;
    private static final int VOLUME_PERIOD = 20;
    private static final int RSI_PERIOD = 14;

    private final StockService stockService;
    private final StockMasterService stockMasterService;

    public StockIndicatorDto getIndicators(String code) {
        LocalDateTime asOf = LocalDateTime.now(KST).truncatedTo(ChronoUnit.MINUTES);
        LocalDate today = asOf.toLocalDate();

        Map<String, Object> detail = stockService.getPriceDetail(code);
        BigDecimal currentPrice = decimal(detail.get("stck_prpr"));
        Long todayVolume = longValue(detail.get("acml_vol"));

        List<Bar> bars = loadBars(code, today.minusDays(CANDLE_LOOKBACK_DAYS), today);

        // 마지막 일봉이 오늘이면 장중 값일 수 있으므로 현재가·누적 거래량으로 맞춘다.
        // 오늘 일봉이 없으면(주말·장 시작 전) 현재가는 마지막 거래일 종가와 같으므로 그대로 둔다.
        List<BigDecimal> closes = new ArrayList<>();
        List<Long> volumes = new ArrayList<>();
        for (Bar b : bars) {
            closes.add(b.close());
            volumes.add(b.volume());
        }
        LocalDate tradeDate = bars.isEmpty() ? null : bars.get(bars.size() - 1).date();
        if (today.equals(tradeDate)) {
            if (currentPrice != null) closes.set(closes.size() - 1, currentPrice);
            if (todayVolume != null) volumes.set(volumes.size() - 1, todayVolume);
        }
        if (currentPrice == null && !closes.isEmpty()) currentPrice = closes.get(closes.size() - 1);
        Long volume = volumes.isEmpty() ? todayVolume : volumes.get(volumes.size() - 1);

        BigDecimal high52w = positive(decimal(detail.get("w52_hgpr")));
        BigDecimal low52w = positive(decimal(detail.get("w52_lwpr")));
        BigDecimal rsi14 = rsi(closes, RSI_PERIOD);
        BigDecimal position52w = rangePosition(currentPrice, low52w, high52w);

        return new StockIndicatorDto(
                code,
                stockMasterService.findName(code),
                asOf,
                tradeDate,
                closes.size(),
                currentPrice,
                changeRate(closes),
                sma(closes, MA_SHORT),
                gapRate(currentPrice, closes, MA_SHORT),
                position(currentPrice, closes, MA_SHORT),
                sma(closes, MA_MID),
                gapRate(currentPrice, closes, MA_MID),
                position(currentPrice, closes, MA_MID),
                volume,
                averageVolume(volumes, VOLUME_PERIOD),
                volumeRatio(volumes, VOLUME_PERIOD),
                high52w,
                high52w == null ? null : date(detail.get("w52_hgpr_date")),
                low52w,
                low52w == null ? null : date(detail.get("w52_lwpr_date")),
                position52w,
                rangeZone(position52w),
                rsi14,
                rsiZone(rsi14)
        );
    }

    private record Bar(LocalDate date, BigDecimal close, Long volume) {}

    // KIS 일봉은 최신순으로 오므로 날짜 오름차순으로 정렬한다. 형식이 깨진 행은 건너뛴다
    private List<Bar> loadBars(String code, LocalDate from, LocalDate to) {
        List<Bar> bars = new ArrayList<>();
        for (Map<String, Object> c : stockService.getDailyChart(code, from, to)) {
            LocalDate date = date(c.get("date"));
            BigDecimal close = positive(decimal(c.get("close")));
            if (date == null || close == null) continue;
            bars.add(new Bar(date, close, longValue(c.get("volume"))));
        }
        bars.sort(Comparator.comparing(Bar::date));
        return bars;
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) return null;
        try {
            return new BigDecimal(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long longValue(Object value) {
        BigDecimal d = decimal(value);
        return d == null ? null : d.longValue();
    }

    private static LocalDate date(Object value) {
        if (value == null) return null;
        try {
            return LocalDate.parse(value.toString().trim(), KIS_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    // KIS 는 값이 없을 때 0을 주기도 하므로 0 이하는 없는 값으로 본다
    private static BigDecimal positive(BigDecimal value) {
        return value == null || value.signum() <= 0 ? null : value;
    }
}
