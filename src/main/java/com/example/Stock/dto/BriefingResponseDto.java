package com.example.Stock.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** LLM 브리핑 응답. Ollama format 스키마(prompts/briefing-schema.json)와 필드가 같다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BriefingResponseDto(
        String summary,
        @JsonProperty("price_trend") String priceTrend,
        String volume,
        String technical,
        @JsonProperty("check_points") List<String> checkPoints,
        String disclaimer
) {
    public BriefingResponseDto withDisclaimer(String fixedDisclaimer) {
        return new BriefingResponseDto(summary, priceTrend, volume, technical, checkPoints, fixedDisclaimer);
    }
}
