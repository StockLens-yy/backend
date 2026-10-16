package com.example.Stock;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** application.yml 의 ollama.* 설정 */
@ConfigurationProperties(prefix = "ollama")
public record OllamaProperties(
        @DefaultValue("http://localhost:11434") String baseUrl,
        @DefaultValue("gemma3:4b") String model,
        @DefaultValue("120s") Duration timeout,
        @DefaultValue("3s") Duration connectTimeout,
        @DefaultValue("0.2") double temperature
) {}
