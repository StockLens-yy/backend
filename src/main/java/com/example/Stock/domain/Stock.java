package com.example.Stock.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter @Setter
@NoArgsConstructor
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;
    private String name;
    private Double price;

    @Column(name = "stock_change")
    private Double change;

    @Column(name = "change_percent")
    private Double changePercent;

    private Long volume;

    /** 시세를 마지막으로 갱신한 시각 */
    private LocalDateTime updatedAt;
}