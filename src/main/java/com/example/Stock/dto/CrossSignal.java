package com.example.Stock.dto;

import java.time.LocalDate;

/** 이동평균 교차 신호. */
public record CrossSignal(LocalDate date, Type type, Double shortMa, Double longMa) {

    public enum Type {
        /** 단기선이 장기선을 상향 돌파 (매수 신호) */
        GOLDEN,
        /** 단기선이 장기선을 하향 돌파 (매도 신호) */
        DEAD
    }
}
