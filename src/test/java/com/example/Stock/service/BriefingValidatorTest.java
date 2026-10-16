package com.example.Stock.service;

import com.example.Stock.dto.BriefingResponseDto;
import com.example.Stock.service.StockIndicatorCalculator.RangeZone;
import com.example.Stock.service.StockIndicatorCalculator.RsiZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BriefingValidatorTest {

    private final BriefingValidator validator = new BriefingValidator();
    private final Set<String> allowed = validator.allowedNumbers(
            "현재가: 52,300원 / 등락률: +1.36% / RSI(14): 58.40 / 5일 이동평균 / 2026-03-12 14:20");

    private BriefingResponseDto response(String summary) {
        return new BriefingResponseDto(summary, "현재가 52,300원입니다.", "거래량 데이터 없음",
                "RSI(14)는 58.4입니다.", List.of("5일 이동평균을 살펴보세요."), "");
    }

    @Test
    @DisplayName("입력에 있던 숫자만 쓰면 통과한다 (콤마·소수점 끝 0 표기 차이는 같은 숫자로 본다)")
    void 통과() {
        BriefingValidator.Result result = validator.validate(response("전일 대비 +1.36%, 52300원입니다."), allowed, null, null);

        assertTrue(result.valid(), result.violations().toString());
    }

    @Test
    @DisplayName("입력에 없는 숫자나 반올림한 숫자는 위반이다")
    void 없는_숫자() {
        BriefingValidator.Result result = validator.validate(response("등락률은 약 1.4%이고 목표는 60,000원"), allowed, null, null);

        assertFalse(result.valid());
        assertTrue(result.violations().toString().contains("1.4"));
        assertTrue(result.violations().toString().contains("60000"));
    }

    @Test
    @DisplayName("권유·예측·단정 표현은 위반이고, 매수세 같은 설명은 허용한다")
    void 금지_표현() {
        assertFalse(validator.validate(response("지금이 매수 기회입니다."), allowed, null, null).valid());
        assertFalse(validator.validate(response("앞으로 오를 것으로 보입니다."), allowed, null, null).valid());
        assertFalse(validator.validate(response("상승이 확실합니다."), allowed, null, null).valid());
        assertFalse(validator.validate(response("반등 가능성을 염두에 두세요."), allowed, null, null).valid());
        assertFalse(validator.validate(response("하락 추세가 이어질 수 있습니다."), allowed, null, null).valid());
        assertFalse(validator.validate(response("추가 하락을 예상할 수 있습니다."), allowed, null, null).valid());
        assertFalse(validator.validate(response("하락세가 이어질 모습입니다."), allowed, null, null).valid());
        assertTrue(validator.validate(response("매수세가 이어지는 편입니다."), allowed, null, null).valid());
    }

    @Test
    @DisplayName("Java 가 판정한 구간과 반대로 단정하면 위반이다")
    void 구간_해석() {
        BriefingResponseDto oversold = response("RSI는 침체 구간에 속합니다.");
        assertFalse(validator.validate(oversold, allowed, RsiZone.NEUTRAL, null).valid());
        assertTrue(validator.validate(oversold, allowed, RsiZone.OVERSOLD, null).valid());
        // 부정문은 단정이 아니다
        assertTrue(validator.validate(response("과열이나 침체 구간이 아닌 중간 수준입니다."), allowed, RsiZone.NEUTRAL, null).valid());

        BriefingResponseDto farFromLow = response("현재가는 최저가에서 비교적 멀리 떨어져 있습니다.");
        assertFalse(validator.validate(farFromLow, allowed, null, RangeZone.LOW).valid());
        assertTrue(validator.validate(farFromLow, allowed, null, RangeZone.MIDDLE).valid());
    }

    @Test
    @DisplayName("위반 내용에는 소수점에서 끊기지 않은 문장 전체가 들어간다")
    void 위반_문장() {
        BriefingValidator.Result result = validator.validate(
                response("전일 대비 +1.36% 움직였고 반등 가능성이 있습니다. 다음 문장입니다."), allowed, null, null);

        assertTrue(result.violations().toString().contains("\"전일 대비 +1.36% 움직였고 반등 가능성이 있습니다.\""),
                result.violations().toString());
    }

    @Test
    @DisplayName("필수 항목이 비어 있으면 위반이다")
    void 필수_항목() {
        BriefingResponseDto empty = new BriefingResponseDto("", null, "a", "b", null, null);

        BriefingValidator.Result result = validator.validate(empty, allowed, null, null);

        assertFalse(result.valid());
        assertTrue(result.violations().containsAll(List.of("summary 비어 있음", "price_trend 비어 있음", "check_points 누락")));
    }
}
