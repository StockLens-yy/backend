package com.example.Stock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * AI(Ollama) 기능 설정.
 * 캐시는 별도 라이브러리 없이 스프링 기본 메모리 캐시(ConcurrentMap)를 쓴다.
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(OllamaProperties.class)
public class AiConfig {
}
