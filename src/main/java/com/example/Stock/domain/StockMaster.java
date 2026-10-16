package com.example.Stock.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 국내 상장 전 종목 목록 (KIS 종목 마스터 파일 기준).
 * 검색용이라 시세는 담지 않는다. 시세는 검색한 종목만 KIS 현재가 API로 조회한다.
 */
@Entity
@Table(indexes = @Index(name = "uk_stock_master_symbol", columnList = "symbol", unique = true))
@Getter @Setter
@NoArgsConstructor
public class StockMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;
    private String name;

    /** KOSPI / KOSDAQ */
    private String market;

    /** 마스터 파일의 증권그룹구분코드 (ST: 주권, EF: ETF, FS: 외국주권, DR: 예탁증서) */
    private String type;
}
