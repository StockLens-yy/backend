package com.example.Stock.scheduler;

import com.example.Stock.service.StockPriceService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 일봉 시계열 자동 수집 배치.
 * 장 마감(15:30) 이후에 실행해 당일 종가를 확정된 상태로 저장한다.
 */
@Component
@RequiredArgsConstructor
public class StockPriceScheduler {

    private static final Logger log = LoggerFactory.getLogger(StockPriceScheduler.class);

    /** 누락분까지 메우기 위해 최근 며칠 구간을 함께 재수집한다. */
    private static final int COLLECT_DAYS = 7;

    private final StockPriceService stockPriceService;

    // 평일 16:00 (한국시간)
    @Scheduled(cron = "0 0 16 * * MON-FRI", zone = "Asia/Seoul")
    public void collectDailyPrices() {
        log.info("일봉 수집 배치 시작");
        stockPriceService.collectAll(COLLECT_DAYS);
    }
}
