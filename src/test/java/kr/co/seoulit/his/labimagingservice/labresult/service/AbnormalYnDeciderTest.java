package kr.co.seoulit.his.labimagingservice.labresult.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 참고범위·결과값 비교 판정 (ZP2-99, 6차 결과항목).
 * 마무리 점검(2026-10-01) T1 — decideDirection 의 정성 비교 분기에서 resultValue 가 null 이면
 * NPE 가 나던 결함을 고치면서 회귀 테스트를 추가한다.
 */
class AbnormalYnDeciderTest {

    @Test
    @DisplayName("decideDirection: 참고범위 없음(null/blank) → 판정 불가(null)")
    void decideDirectionNoReferenceRange() {
        assertThat(AbnormalYnDecider.decideDirection("4.2", null)).isNull();
        assertThat(AbnormalYnDecider.decideDirection("4.2", "")).isNull();
        assertThat(AbnormalYnDecider.decideDirection("4.2", "   ")).isNull();
    }

    @Test
    @DisplayName("decideDirection: 정량 — 범위 안이면 N, 하한 미만이면 L, 상한 초과면 H")
    void decideDirectionQuantitative() {
        assertThat(AbnormalYnDecider.decideDirection("5.0", "3.5-6.0")).isEqualTo("N");
        assertThat(AbnormalYnDecider.decideDirection("2.0", "3.5-6.0")).isEqualTo("L");
        assertThat(AbnormalYnDecider.decideDirection("7.0", "3.5-6.0")).isEqualTo("H");
    }

    @Test
    @DisplayName("decideDirection: 정성 — 쉼표 목록과 일치하면 N, 불일치하면 null(방향 없음)")
    void decideDirectionQualitative() {
        assertThat(AbnormalYnDecider.decideDirection("정상", "음성,정상")).isEqualTo("N");
        assertThat(AbnormalYnDecider.decideDirection("음성", "음성,정상")).isEqualTo("N");
        assertThat(AbnormalYnDecider.decideDirection("양성", "음성,정상")).isNull();
    }

    @Test
    @DisplayName("decideDirection: resultValue 가 null/blank 면 NPE 대신 null(판정 불가) — T1 결함 수정")
    void decideDirectionNullResultValueDoesNotThrow() {
        assertThat(AbnormalYnDecider.decideDirection(null, "음성,정상")).isNull();
        assertThat(AbnormalYnDecider.decideDirection("", "음성,정상")).isNull();
        assertThat(AbnormalYnDecider.decideDirection("   ", "음성,정상")).isNull();
    }

    @Test
    @DisplayName("decide: 기존 Y/N 판정 로직은 이번 수정으로 바뀌지 않는다 (회귀)")
    void decideUnaffectedByFix() {
        assertThat(AbnormalYnDecider.decide("5.0", "3.5-6.0")).isEqualTo("N");
        assertThat(AbnormalYnDecider.decide("7.0", "3.5-6.0")).isEqualTo("Y");
        assertThat(AbnormalYnDecider.decide("4.2", null)).isEqualTo("N");
        assertThat(AbnormalYnDecider.decide("정상", "음성,정상")).isEqualTo("N");
        assertThat(AbnormalYnDecider.decide("양성", "음성,정상")).isEqualTo("Y");
    }
}
