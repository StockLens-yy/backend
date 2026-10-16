package com.example.Stock.service;

import com.example.Stock.dto.LiveQuote;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 실시간 시세 보관소 + 화면(SSE) 전송.
 * 체결은 1초에도 여러 번 들어오므로, 종목별 최신값만 모아 두었다가 0.2초마다 한 번에 보낸다.
 */
@Component
public class LiveQuoteHub {

    private static final long FLUSH_INTERVAL_MS = 200L;

    private final Map<String, LiveQuote> latest = new ConcurrentHashMap<>();
    private final Map<String, LiveQuote> pending = new ConcurrentHashMap<>();
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    // 스프링 스케줄러 스레드는 장중 시세 갱신이 오래 붙잡고 있으므로 전용 스레드를 쓴다
    private final ScheduledExecutorService flusher = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    void start() {
        flusher.scheduleAtFixedRate(this::flush, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        flusher.shutdownNow();
        emitters.forEach(SseEmitter::complete);
    }

    public void publish(LiveQuote quote) {
        latest.put(quote.symbol(), quote);
        pending.put(quote.symbol(), quote);
    }

    public Optional<LiveQuote> get(String symbol) {
        return Optional.ofNullable(latest.get(symbol));
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L); // 시간 제한 없음 (끊기면 브라우저가 자동 재연결)
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    private void flush() {
        if (pending.isEmpty()) return;

        List<LiveQuote> batch = new ArrayList<>();
        for (String symbol : new ArrayList<>(pending.keySet())) {
            LiveQuote q = pending.remove(symbol);
            if (q != null) batch.add(q);
        }
        if (batch.isEmpty() || emitters.isEmpty()) return;

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("quotes").data(batch));
            } catch (Exception e) {
                emitters.remove(emitter); // 브라우저 탭이 닫힌 경우 등
            }
        }
    }
}
