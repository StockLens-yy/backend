package com.example.Stock.service;

import com.example.Stock.dto.StockIndicatorDto;
import com.example.Stock.service.StockIndicatorCalculator.Position;
import com.example.Stock.service.StockIndicatorCalculator.RangeZone;
import com.example.Stock.service.StockIndicatorCalculator.RsiZone;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.example.Stock.service.OllamaClient.message;

/**
 * 브리핑 프롬프트 구성. 문구는 모두 resources/prompts 아래 템플릿 파일에 두고,
 * 여기서는 {{이름}} 자리에 지표 값을 표기 규칙대로 채워 넣기만 한다.
 * <p>
 * 대화 순서: system → (few-shot) 예시 입력 → 예시 출력 → 실제 입력
 */
@Component
public class PromptBuilder {

    public static final String NO_DATA = "데이터 없음";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");
    private static final DateTimeFormatter AS_OF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final String systemTemplate;
    private final String userTemplate;
    private final String exampleInput;
    private final String exampleOutput;
    private final String retryTemplate;
    private final String fallbackTemplate;
    private final String disclaimer;
    private final Map<String, Object> schema;

    public PromptBuilder(ObjectMapper objectMapper) {
        this.systemTemplate = load("prompts/briefing-system.txt");
        this.userTemplate = load("prompts/briefing-user.txt");
        this.exampleInput = load("prompts/briefing-example-input.txt");
        this.exampleOutput = load("prompts/briefing-example-output.json");
        this.retryTemplate = load("prompts/briefing-retry.txt");
        this.fallbackTemplate = load("prompts/briefing-fallback.json");
        this.disclaimer = load("prompts/briefing-disclaimer.txt").trim();
        try {
            this.schema = objectMapper.readValue(load("prompts/briefing-schema.json"), new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException("briefing-schema.json 을 읽을 수 없습니다", e);
        }
    }

    public String systemPrompt() {
        return systemTemplate;
    }

    public String userPrompt(StockIndicatorDto d) {
        return render(userTemplate, values(d));
    }

    /** few-shot 예시를 포함한 첫 요청 대화 */
    public List<Map<String, String>> messages(String userPrompt) {
        return List.of(
                message("system", systemTemplate),
                message("user", exampleInput),
                message("assistant", exampleOutput),
                message("user", userPrompt)
        );
    }

    /** 검증 실패 시 이어 붙일 재요청 문구 */
    public String retryPrompt(List<String> violations) {
        return render(retryTemplate, Map.of("violations", String.join(", ", violations)));
    }

    /** AI 응답을 쓸 수 없을 때 계산된 지표만으로 만드는 기본 메시지(JSON) */
    public String fallbackJson(StockIndicatorDto d) {
        Map<String, String> escaped = new LinkedHashMap<>();
        values(d).forEach((k, v) -> escaped.put(k, v.replace("\\", "\\\\").replace("\"", "\\\"")));
        return render(fallbackTemplate, escaped);
    }

    public String disclaimer() {
        return disclaimer;
    }

    public Map<String, Object> schema() {
        return schema;
    }

    // 템플릿에 채울 값. 금액은 "52,300원", 비율은 "+1.36%", 없는 값은 "데이터 없음"
    private Map<String, String> values(StockIndicatorDto d) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("asOf", d.asOf() == null ? NO_DATA : d.asOf().format(AS_OF));
        v.put("name", d.name());
        v.put("code", d.code());
        v.put("tradeDate", date(d.tradeDate()));
        v.put("dataDays", String.valueOf(d.dataDays()));
        v.put("currentPrice", won(d.currentPrice()));
        v.put("changeRate", signedPercent(d.changeRate()));
        v.put("ma5", won(d.ma5()));
        v.put("ma5Position", position(d.ma5Position()));
        v.put("ma5GapRate", signedPercent(d.ma5GapRate()));
        v.put("ma20", won(d.ma20()));
        v.put("ma20Position", position(d.ma20Position()));
        v.put("ma20GapRate", signedPercent(d.ma20GapRate()));
        v.put("volume", d.volume() == null ? NO_DATA : grouped(BigDecimal.valueOf(d.volume())) + "주");
        v.put("avgVolume20", d.avgVolume20() == null ? NO_DATA : grouped(d.avgVolume20()) + "주");
        v.put("volumeRatio", d.volumeRatio() == null ? NO_DATA : d.volumeRatio().toPlainString() + "배");
        v.put("high52w", won(d.high52w()));
        v.put("high52wDate", date(d.high52wDate()));
        v.put("low52w", won(d.low52w()));
        v.put("low52wDate", date(d.low52wDate()));
        v.put("position52w", d.position52w() == null ? NO_DATA : d.position52w().toPlainString() + "%");
        v.put("rsi14", d.rsi14() == null ? NO_DATA : d.rsi14().toPlainString());
        v.put("rsiZone", rsiZone(d.rsiZone()));
        v.put("range52wZone", rangeZone(d.range52wZone()));
        return v;
    }

    private static String won(BigDecimal value) {
        return value == null ? NO_DATA : grouped(value) + "원";
    }

    private static String signedPercent(BigDecimal value) {
        if (value == null) return NO_DATA;
        return (value.signum() > 0 ? "+" : "") + value.toPlainString() + "%";
    }

    private static String position(Position p) {
        if (p == null) return NO_DATA;
        return switch (p) {
            case ABOVE -> "위";
            case BELOW -> "아래";
            case EQUAL -> "같음";
        };
    }

    private static String rsiZone(RsiZone zone) {
        if (zone == null) return NO_DATA;
        return switch (zone) {
            case OVERBOUGHT -> "과열 구간";
            case NEUTRAL -> "중립 구간";
            case OVERSOLD -> "침체 구간";
        };
    }

    private static String rangeZone(RangeZone zone) {
        if (zone == null) return NO_DATA;
        return switch (zone) {
            case LOW -> "하단 구간";
            case MIDDLE -> "중간 구간";
            case HIGH -> "상단 구간";
        };
    }

    private static String date(LocalDate date) {
        return date == null ? NO_DATA : date.toString();
    }

    private static String grouped(BigDecimal value) {
        return new DecimalFormat("#,##0.##").format(value);
    }

    static String render(String template, Map<String, String> values) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = values.getOrDefault(m.group(1), NO_DATA);
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String load(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("프롬프트 템플릿을 읽을 수 없습니다: " + path, e);
        }
    }
}
