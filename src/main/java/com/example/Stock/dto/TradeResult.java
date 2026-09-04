package com.example.Stock.dto;

/** 매매 체결 결과. 체결 내역과 체결 직후의 예수금을 함께 준다. */
public record TradeResult(OrderView order, Double cash) {}
