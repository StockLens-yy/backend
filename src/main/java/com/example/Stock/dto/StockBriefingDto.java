package com.example.Stock.dto;

/**
 * GET /api/stocks/{code}/briefing 응답.
 *
 * @param aiGenerated false 면 AI 응답이 검증을 통과하지 못해 계산된 지표로 만든 기본 메시지다
 */
public record StockBriefingDto(
        StockIndicatorDto indicators,
        BriefingResponseDto briefing,
        boolean aiGenerated
) {}
