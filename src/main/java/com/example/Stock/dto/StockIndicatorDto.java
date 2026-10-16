package com.example.Stock.dto;

import com.example.Stock.service.StockIndicatorCalculator.Position;
import com.example.Stock.service.StockIndicatorCalculator.RangeZone;
import com.example.Stock.service.StockIndicatorCalculator.RsiZone;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 브리핑의 입력이 되는 계산된 지표. 모든 수치는 Java 에서 계산하고, LLM 은 이 값만 해석한다.
 * 데이터가 부족해 계산할 수 없는 항목은 null 이다.
 * 금액은 원 단위 정수, 비율(%)·배율·RSI 는 소수점 둘째 자리.
 */
public record StockIndicatorDto(
        String code,
        String name,
        LocalDateTime asOf,            // 기준일시 (조회 시각)
        LocalDate tradeDate,           // 마지막 거래일
        int dataDays,                  // 계산에 쓴 일봉 수

        BigDecimal currentPrice,
        BigDecimal changeRate,         // 전일 대비 등락률 (%)

        BigDecimal ma5,
        BigDecimal ma5GapRate,         // 5일 이동평균 대비 괴리율 (%)
        Position ma5Position,
        BigDecimal ma20,
        BigDecimal ma20GapRate,
        Position ma20Position,

        Long volume,                   // 당일 누적 거래량
        BigDecimal avgVolume20,        // 직전 20일 평균 거래량
        BigDecimal volumeRatio,        // 20일 평균 대비 배율

        BigDecimal high52w,
        LocalDate high52wDate,
        BigDecimal low52w,
        LocalDate low52wDate,
        BigDecimal position52w,        // 52주 범위 내 위치 (%, 최저 0 ~ 최고 100)
        RangeZone range52wZone,        // 52주 범위 구간 (하단/중간/상단)

        BigDecimal rsi14,
        RsiZone rsiZone                // RSI 구간 (과열/중립/침체)
) {}
