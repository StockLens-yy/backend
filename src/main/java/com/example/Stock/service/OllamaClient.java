package com.example.Stock.service;

import com.example.Stock.OllamaProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ollama /api/chat 호출.
 * format 에 JSON 스키마를 넘겨 출력 형식을 강제하고, 응답 메시지 본문(JSON 문자열)을 그대로 돌려준다.
 * 타임아웃이 KIS 호출과 달라 공용 RestTemplate 대신 전용 인스턴스를 쓴다.
 */
@Component
public class OllamaClient {

    private final RestTemplate restTemplate;
    private final OllamaProperties properties;

    public OllamaClient(RestTemplateBuilder builder, OllamaProperties properties) {
        this.properties = properties;
        this.restTemplate = builder
                .connectTimeout(properties.connectTimeout())
                .readTimeout(properties.timeout())
                .build();
    }

    /**
     * @param messages role(system/user/assistant) 과 content 로 이루어진 대화
     * @param format   출력 JSON 스키마
     * @return assistant 응답 본문
     */
    public String chat(List<Map<String, String>> messages, Map<String, Object> format) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("messages", messages);
        body.put("stream", false);
        body.put("format", format);
        body.put("options", Map.of("temperature", properties.temperature()));

        Map<?, ?> response = restTemplate.postForObject(properties.baseUrl() + "/api/chat", body, Map.class);
        if (response == null || !(response.get("message") instanceof Map<?, ?> message)) {
            throw new IllegalStateException("Ollama 응답에 message 가 없습니다");
        }
        Object content = message.get("content");
        return content == null ? "" : content.toString();
    }

    public static Map<String, String> message(String role, String content) {
        return Map.of("role", role, "content", content);
    }
}
