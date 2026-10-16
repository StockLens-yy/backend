package com.example.Stock.service;

import com.example.Stock.dto.BriefingResponseDto;
import com.example.Stock.service.StockIndicatorCalculator.RangeZone;
import com.example.Stock.service.StockIndicatorCalculator.RsiZone;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 응답 후처리 검증.
 * <ul>
 *   <li>응답에 나온 숫자가 모두 프롬프트(입력 지표)에 있던 숫자인지</li>
 *   <li>매수·매도 권유, 주가 예측, 단정 표현이 없는지</li>
 *   <li>Java 가 판정한 구간(RSI, 52주 범위)과 반대로 해석하지 않았는지</li>
 *   <li>필수 항목이 비어 있지 않은지</li>
 * </ul>
 * disclaimer 는 서버가 고정 문구로 덮어쓰므로 검사하지 않는다.
 */
@Component
public class BriefingValidator {

    /** 52,300 / 1.36 / 2026 같은 숫자. 콤마는 비교 전에 제거한다 */
    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");

    /**
     * 권유·예측·단정 표현. "매수세", "매도 물량"처럼 상황을 설명하는 말은 허용하고
     * 행동을 권하는 표현만 막는다.
     */
    private static final List<Pattern> FORBIDDEN = List.of(
            Pattern.compile("(매수|매도)\\s*(하세요|하시길|하시는\\s*것|해\\s*보세요|를?\\s*권|추천|기회|타이밍|적기|시점)"),
            Pattern.compile("(사세요|파세요|사는\\s*것이\\s*좋|파는\\s*것이\\s*좋|담아\\s*보세요|팔아\\s*보세요)"),
            Pattern.compile("목표\\s*(가|주가)"),
            Pattern.compile("(가능성|전망|여력|예상|예측|추세\\s*반전)"),
            // "이어질 수 있습니다", "지속될 모습", "도달할 수 있는지" 같은 앞날 짐작
            Pattern.compile("(오를|내릴|상승할|하락할|떨어질|반등할|회복할|회복될|이어질|지속될|지속할|전환될|도달할|"
                    + "확대될|둔화될|커질|줄어들|바뀔)\\s*(수\\s*(있|도)|모습|것|겁니다|거예요|듯|조짐)"),
            Pattern.compile("(확실|틀림없|무조건|보장)")
    );

    // "침체 구간에 속합니다", "과열 구간입니다" 처럼 구간을 단정하는 문장 ("~구간이 아닌"은 제외)
    private static final Pattern CLAIM_OVERSOLD = zoneClaim("침체");
    private static final Pattern CLAIM_OVERBOUGHT = zoneClaim("과열");
    private static final Pattern CLAIM_RANGE_LOW = Pattern.compile("하단\\s*구간\\s*(에\\s*(있|속|위치|머물)|입니다)|최저가(에|와)\\s*(가깝|가까)");
    private static final Pattern CLAIM_RANGE_HIGH = Pattern.compile("상단\\s*구간\\s*(에\\s*(있|속|위치|머물)|입니다)|최고가(에|와)\\s*(가깝|가까)");
    private static final Pattern FAR_FROM_LOW = Pattern.compile("최저가(에서|와는?)\\s*(\\S+\\s*){0,2}멀");
    private static final Pattern FAR_FROM_HIGH = Pattern.compile("최고가(에서|와는?)\\s*(\\S+\\s*){0,2}멀");

    public record Result(boolean valid, List<String> violations) {}

    /** 프롬프트에 등장한 숫자 집합. 응답에는 이 숫자만 허용한다 */
    public Set<String> allowedNumbers(String... sources) {
        Set<String> allowed = new HashSet<>();
        for (String source : sources) allowed.addAll(extractNumbers(source));
        return allowed;
    }

    /**
     * @param rsiZone     Java 가 판정한 RSI 구간 (없으면 검사하지 않음)
     * @param rangeZone   Java 가 판정한 52주 범위 구간 (없으면 검사하지 않음)
     */
    public Result validate(BriefingResponseDto response, Set<String> allowedNumbers,
                           RsiZone rsiZone, RangeZone rangeZone) {
        if (response == null) return new Result(false, List.of("응답이 비어 있음"));

        List<String> violations = new ArrayList<>();
        requireText(response.summary(), "summary", violations);
        requireText(response.priceTrend(), "price_trend", violations);
        requireText(response.volume(), "volume", violations);
        requireText(response.technical(), "technical", violations);
        if (response.checkPoints() == null) violations.add("check_points 누락");

        String text = String.join("\n",
                nullToEmpty(response.summary()), nullToEmpty(response.priceTrend()),
                nullToEmpty(response.volume()), nullToEmpty(response.technical()),
                response.checkPoints() == null ? "" : String.join("\n", response.checkPoints()));

        Set<String> unknown = new LinkedHashSet<>();
        for (String n : extractNumbers(text)) {
            if (!allowedNumbers.contains(n)) unknown.add(n);
        }
        if (!unknown.isEmpty()) violations.add("입력에 없는 숫자 " + unknown);

        for (Pattern p : FORBIDDEN) {
            Matcher m = p.matcher(text);
            if (m.find()) violations.add("금지 표현 \"" + m.group() + "\" (문장: \"" + sentenceAt(text, m.start()) + "\")");
        }
        checkZones(text, rsiZone, rangeZone, violations);
        return new Result(violations.isEmpty(), violations);
    }

    private static void checkZones(String text, RsiZone rsiZone, RangeZone rangeZone, List<String> violations) {
        if (rsiZone != null) {
            if (rsiZone != RsiZone.OVERSOLD) reject(text, CLAIM_OVERSOLD, "RSI", violations);
            if (rsiZone != RsiZone.OVERBOUGHT) reject(text, CLAIM_OVERBOUGHT, "RSI", violations);
        }
        if (rangeZone != null) {
            if (rangeZone != RangeZone.LOW) reject(text, CLAIM_RANGE_LOW, "52주 범위", violations);
            if (rangeZone != RangeZone.HIGH) reject(text, CLAIM_RANGE_HIGH, "52주 범위", violations);
            if (rangeZone == RangeZone.LOW) reject(text, FAR_FROM_LOW, "52주 범위", violations);
            if (rangeZone == RangeZone.HIGH) reject(text, FAR_FROM_HIGH, "52주 범위", violations);
        }
    }

    private static void reject(String text, Pattern pattern, String label, List<String> violations) {
        Matcher m = pattern.matcher(text);
        if (m.find()) violations.add(label + " 구간 해석 오류 (문장: \"" + sentenceAt(text, m.start()) + "\")");
    }

    // 재요청 때 모델이 고칠 곳을 정확히 알도록 위반이 나온 문장 전체를 돌려준다
    private static String sentenceAt(String text, int index) {
        int start = index;
        while (start > 0 && !isSentenceEnd(text, start - 1)) start--;
        int end = index;
        while (end < text.length() && !isSentenceEnd(text, end)) end++;
        if (end < text.length() && text.charAt(end) != '\n') end++;
        return text.substring(start, end).trim();
    }

    // 줄바꿈, 또는 뒤가 공백·끝인 마침표/물음표/느낌표. "2.86" 같은 소수점은 문장 끝이 아니다
    private static boolean isSentenceEnd(String text, int i) {
        char c = text.charAt(i);
        if (c == '\n') return true;
        if (".!?".indexOf(c) < 0) return false;
        return i + 1 >= text.length() || Character.isWhitespace(text.charAt(i + 1));
    }

    private static Pattern zoneClaim(String zone) {
        return Pattern.compile(zone + "\\s*(구간|상태|권)\\s*(에\\s*(속|있|위치|진입|머물|해당|들어)|입니다|이다|으로\\s*(보|볼))");
    }

    /** 숫자를 찾아 콤마를 빼고 정규화한다 (예: "52,300" → "52300", "1.50" → "1.5", "03" → "3") */
    static List<String> extractNumbers(String text) {
        List<String> result = new ArrayList<>();
        if (text == null) return result;
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            String raw = m.group().replace(",", "");
            try {
                result.add(new BigDecimal(raw).stripTrailingZeros().toPlainString());
            } catch (NumberFormatException ignored) {
                // "1,,2" 같은 깨진 표기는 건너뛴다
            }
        }
        return result;
    }

    private static void requireText(String value, String field, List<String> violations) {
        if (value == null || value.isBlank()) violations.add(field + " 비어 있음");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
