package com.example.Stock.scheduler;

import com.example.Stock.service.KisRealtimeService;
import com.example.Stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 장중 시세 자동 갱신.
 * 정규장(평일 09:00~15:30) 동안
 * - 웹소켓 실시간 체결가: 시가총액 상위 40종목 (체결될 때마다 반영)
 * - REST 주기 갱신: 홈 목록 전체 (웹소켓 한도 밖의 종목 포함, 한 바퀴 후 5초 쉬고 반복)
 */
@Component
@RequiredArgsConstructor
public class StockLiveScheduler {

    private static final Logger log = LoggerFactory.getLogger(StockLiveScheduler.class);

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 0);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 30);

    private final StockService stockService;
    private final KisRealtimeService kisRealtimeService;

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void refreshDuringMarketHours() {
        if (!isMarketOpen(ZonedDateTime.now(KST))) return;
        try {
            stockService.getAllStockPrices();
        } catch (Exception e) {
            log.warn("장중 시세 갱신 실패: {}", e.getMessage());
        }
    }

    // 장중에는 KIS 웹소켓 실시간 체결가 연결을 유지하고(끊기면 재연결), 장 마감 후에는 끊는다
    @Scheduled(fixedDelay = 30000, initialDelay = 3000)
    public void manageRealtimeConnection() {
        if (isMarketOpen(ZonedDateTime.now(KST))) {
            kisRealtimeService.connectIfNeeded();
        } else {
            kisRealtimeService.disconnect();
        }
    }

    // 공휴일은 따로 거르지 않는다 (휴장일에도 KIS가 직전 시세를 돌려줄 뿐이라 문제없음)
    static boolean isMarketOpen(ZonedDateTime now) {
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) return false;
        LocalTime time = now.toLocalTime();
        return !time.isBefore(MARKET_OPEN) && !time.isAfter(MARKET_CLOSE);
    }
}
