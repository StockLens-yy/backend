package com.example.Stock.dto;

import java.time.LocalDate;

/** 날짜별 종가와 기술적 지표 묶음. 계산 구간이 부족한 지표는 null 이다. */
public record IndicatorPoint(
        LocalDate date,
        Double close,
        Double ma5,
        Double ma20,
        Double ma60,
        Double rsi14,
        Double bbUpper,
        Double bbMiddle,
        Double bbLower
) {}
