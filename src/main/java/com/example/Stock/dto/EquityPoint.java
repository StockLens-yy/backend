package com.example.Stock.dto;

import java.time.LocalDate;

/** 자산 곡선의 한 지점. 보유 주식은 당일 종가로 평가한다. */
public record EquityPoint(LocalDate date, Double equity) {}
