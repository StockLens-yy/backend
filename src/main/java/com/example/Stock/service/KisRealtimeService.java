package com.example.Stock.service;

import com.example.Stock.domain.Stock;
import com.example.Stock.dto.LiveQuote;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * KIS 웹소켓 실시간 체결가(H0STCNT0) 수신.
 * 체결이 날 때마다 들어오는 가격을 {@link LiveQuoteHub}로 넘겨 화면에 바로 반영되게 한다.
 */
@Service
@RequiredArgsConstructor
public class KisRealtimeService {

    private static final Logger log = LoggerFactory.getLogger(KisRealtimeService.class);

    private static final String WS_URL = "ws://ops.koreainvestment.com:21000/tryitout/H0STCNT0";
    private static final String TR_ID = "H0STCNT0";
    /** KIS 웹소켓 세션당 실시간 등록 한도는 41건 */
    private static final int MAX_SUBSCRIPTIONS = 40;
    private static final long SUBSCRIBE_INTERVAL_MS = 100L;

    // H0STCNT0 응답 필드 위치 (^ 구분)
    private static final int F_SYMBOL = 0;
    private static final int F_PRICE = 2;
    private static final int F_CHANGE = 4;
    private static final int F_CHANGE_PERCENT = 5;
    private static final int F_VOLUME = 13;

    private final RestTemplate restTemplate;
    private final StockService stockService;
    private final LiveQuoteHub liveQuoteHub;

    @Value("${kis.api.appkey}")
    private String appKey;

    @Value("${kis.api.appsecret}")
    private String appSecret;

    @Value("${kis.api.base-url}")
    private String baseUrl;

    private volatile WebSocket socket;

    /** 연결이 없으면 새로 연결하고 시가총액 상위 종목을 실시간 등록한다 */
    public synchronized void connectIfNeeded() {
        if (isConnected()) return;
        try {
            String approvalKey = issueApprovalKey();
            socket = HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(URI.create(WS_URL), new Listener())
                    .join();

            Set<String> symbols = pickSymbols();
            for (String symbol : symbols) {
                socket.sendText(subscribeMessage(approvalKey, symbol), true).join();
                Thread.sleep(SUBSCRIBE_INTERVAL_MS);
            }
            log.info("KIS 실시간 체결가 연결: {}종목 등록", symbols.size());
        } catch (Exception e) {
            log.warn("KIS 실시간 연결 실패: {}", e.getMessage());
            disconnect();
        }
    }

    public synchronized void disconnect() {
        if (socket == null) return;
        try {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
        } catch (Exception ignored) {
            // 이미 끊긴 경우
        }
        socket = null;
        log.info("KIS 실시간 체결가 연결 종료");
    }

    @PreDestroy
    void shutdown() {
        disconnect();
    }

    private boolean isConnected() {
        WebSocket ws = socket;
        return ws != null && !ws.isInputClosed() && !ws.isOutputClosed();
    }

    // 시가총액 순위 상위 종목을 우선 등록하고, 남는 자리는 홈 목록(DB) 종목으로 채운다
    private Set<String> pickSymbols() {
        Set<String> symbols = new LinkedHashSet<>();
        try {
            for (Stock s : stockService.fetchMarketCapTop()) symbols.add(s.getSymbol());
        } catch (Exception e) {
            log.warn("시가총액 순위 조회 실패, DB 종목으로 등록: {}", e.getMessage());
        }
        for (Stock s : stockService.getAllStocks()) {
            if (symbols.size() >= MAX_SUBSCRIPTIONS) break;
            if (s.getSymbol() != null) symbols.add(s.getSymbol());
        }
        return symbols;
    }

    // 웹소켓 접속키 발급 (REST 접근토큰과 별개)
    private String issueApprovalKey() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = new HashMap<>();
        body.put("grant_type", "client_credentials");
        body.put("appkey", appKey);
        body.put("secretkey", appSecret);

        Map response = restTemplate.postForObject(
                baseUrl + "/oauth2/Approval", new HttpEntity<>(body, headers), Map.class);
        return (String) response.get("approval_key");
    }

    private String subscribeMessage(String approvalKey, String symbol) {
        return "{\"header\":{\"approval_key\":\"" + approvalKey + "\",\"custtype\":\"P\",\"tr_type\":\"1\","
                + "\"content-type\":\"utf-8\"},\"body\":{\"input\":{\"tr_id\":\"" + TR_ID + "\",\"tr_key\":\""
                + symbol + "\"}}}";
    }

    private void handleMessage(WebSocket ws, String message) {
        // 체결 데이터: 암호화여부|TR_ID|건수|필드^필드^...
        if (message.startsWith("0|") || message.startsWith("1|")) {
            String[] parts = message.split("\\|", 4);
            if (parts.length < 4 || !TR_ID.equals(parts[1])) return;
            parseTicks(Integer.parseInt(parts[2]), parts[3].split("\\^"));
            return;
        }
        // 그 외는 JSON 제어 메시지 (등록 응답, PINGPONG 등)
        if (message.contains("PINGPONG")) {
            ws.sendText(message, true); // 받은 그대로 돌려줘야 연결이 유지된다
        } else if (message.contains("\"rt_cd\":\"1\"")) {
            log.warn("KIS 실시간 등록 오류: {}", message);
        }
    }

    // 한 메시지에 여러 체결이 이어 붙어 올 수 있다
    private void parseTicks(int count, String[] fields) {
        if (count <= 0) return;
        int size = fields.length / count;
        for (int i = 0; i < count; i++) {
            int base = i * size;
            try {
                liveQuoteHub.publish(new LiveQuote(
                        fields[base + F_SYMBOL],
                        Double.parseDouble(fields[base + F_PRICE]),
                        Double.parseDouble(fields[base + F_CHANGE]),
                        Double.parseDouble(fields[base + F_CHANGE_PERCENT]),
                        Long.parseLong(fields[base + F_VOLUME]),
                        LocalDateTime.now()));
            } catch (RuntimeException e) {
                log.debug("체결 데이터 해석 실패: {}", e.getMessage());
            }
        }
    }

    private class Listener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String message = buffer.toString();
                buffer.setLength(0);
                try {
                    handleMessage(ws, message);
                } catch (Exception e) {
                    log.debug("실시간 메시지 처리 실패: {}", e.getMessage());
                }
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            log.info("KIS 실시간 연결이 닫힘: {} {}", statusCode, reason);
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            log.warn("KIS 실시간 연결 오류: {}", error.getMessage());
        }
    }
}
