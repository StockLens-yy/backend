package com.example.Stock.dto;

/** 볼린저밴드 한 지점. 계산 구간이 부족하면 각 값이 null 이다. */
public record BollingerBand(Double upper, Double middle, Double lower) {

    public static final BollingerBand EMPTY = new BollingerBand(null, null, null);
}
