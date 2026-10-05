package kr.co.seoulit.his.labimagingservice.common.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** UUID 형식 검증 — 동의서양식ID 등. (04번 지시서 Phase 3-C) */
class InputFormatValidatorTest {

    @Test
    @DisplayName("표준 UUID(하이픈 포함 36자)만 true")
    void isUuidAcceptsStandardFormat() {
        assertThat(InputFormatValidator.isUuid("d0a1b2c3-4d5e-6f70-8192-a3b4c5d6e7f8")).isTrue();
    }

    @Test
    @DisplayName("하이픈 없음·자릿수 틀림·null·빈 값은 전부 false")
    void isUuidRejectsInvalidFormats() {
        assertThat(InputFormatValidator.isUuid("tpl-1")).isFalse();
        assertThat(InputFormatValidator.isUuid("d0a1b2c34d5e6f708192a3b4c5d6e7f8")).isFalse();
        assertThat(InputFormatValidator.isUuid("")).isFalse();
        assertThat(InputFormatValidator.isUuid(null)).isFalse();
    }
}
