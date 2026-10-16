package com.example.Stock.scheduler;

import com.example.Stock.service.StockMasterService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 종목 마스터(전 종목 목록) 동기화.
 * 신규 상장·상장폐지를 반영하기 위해 서버 시작 시와 매 영업일 장 시작 전에 다시 받는다.
 */
@Component
@RequiredArgsConstructor
public class StockMasterScheduler {

    private final StockMasterService stockMasterService;

    // 서버 시작 직후 백그라운드로 실행 (기동을 막지 않도록)
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        stockMasterService.sync();
    }

    // 평일 07:30 (한국시간)
    @Scheduled(cron = "0 30 7 * * MON-FRI", zone = "Asia/Seoul")
    public void syncDaily() {
        stockMasterService.sync();
    }
}
