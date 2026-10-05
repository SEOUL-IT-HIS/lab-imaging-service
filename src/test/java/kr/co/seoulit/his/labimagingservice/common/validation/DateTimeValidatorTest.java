package kr.co.seoulit.his.labimagingservice.common.validation;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 날짜·시각 입력 유효성 — 미래/순서/과거 판정 + app.validation.datetime-strict 토글.
 * (04번 지시서 Phase 3-B/3-E 테스트 매트릭스: 지금, +4분, +6분, 과거, 설정 off)
 */
class DateTimeValidatorTest {

    private static final DateTimeValidator strict = new DateTimeValidator(true);
    private static final DateTimeValidator lenient = new DateTimeValidator(false);

    // ---------- rejectIfFuture(LocalDateTime) ----------

    @Test
    @DisplayName("지금/+4분(허용 오차 5분 안)은 통과한다")
    void rejectIfFutureAllowsWithinTolerance() {
        assertThatCode(() -> strict.rejectIfFuture(LocalDateTime.now(), "collectedAt"))
                .doesNotThrowAnyException();
        assertThatCode(() -> strict.rejectIfFuture(LocalDateTime.now().plusMinutes(4), "collectedAt"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("+6분(허용 오차 5분 밖)은 LAB107로 거절한다")
    void rejectIfFutureRejectsBeyondTolerance() {
        assertThatThrownBy(() -> strict.rejectIfFuture(LocalDateTime.now().plusMinutes(6), "collectedAt"))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB107);
    }

    @Test
    @DisplayName("과거 시각은 통과한다")
    void rejectIfFutureAllowsPast() {
        assertThatCode(() -> strict.rejectIfFuture(LocalDateTime.now().minusDays(1), "collectedAt"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("null은 통과한다 — 필수 여부는 이 메서드의 책임이 아니다")
    void rejectIfFutureAllowsNull() {
        assertThatCode(() -> strict.rejectIfFuture((LocalDateTime) null, "collectedAt")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("설정 off(app.validation.datetime-strict=false)면 미래 시각도 통과한다")
    void rejectIfFutureSkippedWhenNotStrict() {
        assertThatCode(() -> lenient.rejectIfFuture(LocalDateTime.now().plusDays(1), "collectedAt"))
                .doesNotThrowAnyException();
    }

    // ---------- rejectIfFuture(LocalDate) ----------

    @Test
    @DisplayName("동의일 — 오늘은 통과, 내일은 LAB107")
    void rejectIfFutureDate() {
        assertThatCode(() -> strict.rejectIfFuture(LocalDate.now(), "consentDt")).doesNotThrowAnyException();
        assertThatThrownBy(() -> strict.rejectIfFuture(LocalDate.now().plusDays(1), "consentDt"))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB107);
    }

    // ---------- rejectIfEarlierThan ----------

    @Test
    @DisplayName("인수일시가 채취일시보다 빠르면 LAB108")
    void rejectIfEarlierThanRejects() {
        LocalDateTime collectedAt = LocalDateTime.now();
        LocalDateTime acceptedAt = collectedAt.minusMinutes(1);

        assertThatThrownBy(() -> strict.rejectIfEarlierThan(acceptedAt, collectedAt, "acceptedAt", "collectedAt"))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB108);
    }

    @Test
    @DisplayName("인수일시가 채취일시와 같거나 그 이후면 통과한다")
    void rejectIfEarlierThanAllowsSameOrLater() {
        LocalDateTime collectedAt = LocalDateTime.now().minusHours(1);

        assertThatCode(() -> strict.rejectIfEarlierThan(collectedAt, collectedAt, "acceptedAt", "collectedAt"))
                .doesNotThrowAnyException();
        assertThatCode(() -> strict.rejectIfEarlierThan(
                collectedAt.plusMinutes(10), collectedAt, "acceptedAt", "collectedAt"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("설정 off면 순서가 뒤집혀도 통과한다")
    void rejectIfEarlierThanSkippedWhenNotStrict() {
        LocalDateTime collectedAt = LocalDateTime.now();
        assertThatCode(() -> lenient.rejectIfEarlierThan(
                collectedAt.minusMinutes(1), collectedAt, "acceptedAt", "collectedAt"))
                .doesNotThrowAnyException();
    }

    // ---------- rejectIfPastDate ----------

    @Test
    @DisplayName("일정 — 어제 날짜는 LAB116, 오늘 이른 시각은 통과(날짜만 비교)")
    void rejectIfPastDate() {
        assertThatThrownBy(() -> strict.rejectIfPastDate(LocalDateTime.now().minusDays(1), "scheduledAt"))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB116);

        LocalDateTime todayEarly = LocalDate.now().atTime(0, 1);
        assertThatCode(() -> strict.rejectIfPastDate(todayEarly, "scheduledAt")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("일정 — 미래 날짜는 통과한다")
    void rejectIfPastDateAllowsFuture() {
        assertThatCode(() -> strict.rejectIfPastDate(LocalDateTime.now().plusDays(1), "scheduledAt"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("설정 off면 과거 날짜 일정도 통과한다")
    void rejectIfPastDateSkippedWhenNotStrict() {
        assertThatCode(() -> lenient.rejectIfPastDate(LocalDateTime.now().minusDays(1), "scheduledAt"))
                .doesNotThrowAnyException();
    }
}
