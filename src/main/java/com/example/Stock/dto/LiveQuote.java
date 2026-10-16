package com.example.Stock.dto;

import java.time.LocalDateTime;

/** KIS 웹소켓으로 받은 실시간 체결 시세 */
public record LiveQuote(
        String symbol,
        double price,
        double change,
        double changePercent,
        long volume,
        LocalDateTime updatedAt
) {}
