package com.example.Stock.service;

import com.example.Stock.dto.BriefingResponseDto;
import com.example.Stock.dto.StockBriefingDto;
import com.example.Stock.dto.StockIndicatorDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.example.Stock.service.OllamaClient.message;

/**
 * 종목 현재 상황 정리(브리핑).
 * 숫자는 {@link StockIndicatorService} 가 계산하고, LLM 은 그 값을 해석·요약만 한다.
 * <p>
 * 흐름: 지표 계산 → 프롬프트 구성 → Ollama 호출 → 검증
 * → (실패 시) 위반 내용을 알려 1회 재요청 → (또 실패 시) 계산값만으로 만든 기본 메시지
 */
@Service
@RequiredArgsConstructor
public class BriefingService {

    private static final Logger log = LoggerFactory.getLogger(BriefingService.class);

    /** 첫 요청 + 재요청 1회 */
    private static final int MAX_ATTEMPTS = 2;

    private final StockIndicatorService stockIndicatorService;
    private final PromptBuilder promptBuilder;
    private final OllamaClient ollamaClient;
    private final BriefingValidator validator;
    private final ObjectMapper objectMapper;

    /**
     * 종목코드 + 기준일자(한국시간)로 캐싱한다. 기본 메시지(AI 실패)는 캐싱하지 않아 다음 요청에서 다시 시도한다.
     */
    @Cacheable(cacheNames = "briefing",
            key = "#code + ':' + T(java.time.LocalDate).now(T(java.time.ZoneId).of('Asia/Seoul'))",
            unless = "!#result.aiGenerated()")
    public StockBriefingDto brief(String code) {
        StockIndicatorDto indicators = stockIndicatorService.getIndicators(code);

        String userPrompt = promptBuilder.userPrompt(indicators);
        Set<String> allowed = validator.allowedNumbers(promptBuilder.systemPrompt(), userPrompt);
        List<Map<String, String>> messages = new ArrayList<>(promptBuilder.messages(userPrompt));

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String raw;
            try {
                raw = ollamaClient.chat(messages, promptBuilder.schema());
            } catch (RestClientException | IllegalStateException e) {
                log.warn("브리핑 생성 실패 - Ollama 호출 오류 ({}): {}", code, e.getMessage());
                return fallback(indicators);
            }

            BriefingResponseDto response = parse(raw);
            BriefingValidator.Result result = response == null
                    ? new BriefingValidator.Result(false, List.of("JSON 형식 오류"))
                    : validator.validate(response, allowed, indicators.rsiZone(), indicators.range52wZone());

            if (result.valid()) {
                return new StockBriefingDto(indicators, response.withDisclaimer(promptBuilder.disclaimer()), true);
            }

            log.warn("브리핑 검증 실패 ({}/{}회) {}: {}", attempt, MAX_ATTEMPTS, code, result.violations());
            log.debug("브리핑 검증 실패 응답 원문 ({}): {}", code, raw);
            messages.add(message("assistant", raw));
            messages.add(message("user", promptBuilder.retryPrompt(result.violations())));
        }

        log.warn("브리핑 재요청도 검증 실패 - 기본 메시지로 대체 ({})", code);
        return fallback(indicators);
    }

    private BriefingResponseDto parse(String raw) {
        try {
            return objectMapper.readValue(raw, BriefingResponseDto.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private StockBriefingDto fallback(StockIndicatorDto indicators) {
        BriefingResponseDto response = parse(promptBuilder.fallbackJson(indicators));
        if (response == null) {
            // 템플릿 자체가 깨진 경우에도 요청이 실패하지 않도록 최소 응답을 만든다
            response = new BriefingResponseDto(PromptBuilder.NO_DATA, PromptBuilder.NO_DATA,
                    PromptBuilder.NO_DATA, PromptBuilder.NO_DATA, List.of(), null);
        }
        return new StockBriefingDto(indicators, response.withDisclaimer(promptBuilder.disclaimer()), false);
    }
}
