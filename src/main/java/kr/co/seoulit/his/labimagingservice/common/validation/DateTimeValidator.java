package kr.co.seoulit.his.labimagingservice.common.validation;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 날짜·시각 입력 유효성. (04번 지시서 Phase 3-B, 2026-10-05)
 *
 * 채취일시·인수일시·동의일·검사·영상 일정에서 같은 기준으로 재사용한다(지시서 §3-B 공통 유틸
 * 방침 — 서버 규칙을 common/validation/ 아래로 모아 여러 서비스가 재사용한다).
 *
 * ⚠ app.validation.datetime-strict(기본 true) — false 면 이 클래스의 모든 메서드가 아무것도
 *   하지 않는다. 시연 중 서버·클라이언트 시계가 어긋나거나 과거 데이터를 입력해야 할 때
 *   즉시 끌 수 있게 하기 위한 탈출구다(지시서 §3-B). 운영에서는 true 로 둔다.
 *
 * ⚠ 시간대는 서버·브라우저 모두 Asia/Seoul 이라고 가정한다(지시서 §3-B). 서버는 LocalDateTime.now()를
 *   그대로 쓰고 별도 타임존 변환을 하지 않는다 — 다르면 이 가정이 깨진 것이므로 별도로 보고해야 한다.
 */
@Component
public class DateTimeValidator {

    /** 미래 판정 허용 오차. 서버·클라이언트 시계가 몇 분 어긋나도 걸리지 않게 둔다. (지시서 §3-B) */
    private static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(5);

    private final boolean strict;

    public DateTimeValidator(@Value("${app.validation.datetime-strict:true}") boolean strict) {
        this.strict = strict;
    }

    /**
     * 미래 시각이면 거절한다(LAB107). 채취일시·인수일시 등에 쓴다.
     * value 가 null 이면(필수 여부는 별도 검증이 담당) 통과시킨다.
     */
    public void rejectIfFuture(LocalDateTime value, String fieldLabel) {
        if (!strict || value == null) {
            return;
        }
        if (value.isAfter(LocalDateTime.now().plus(FUTURE_TOLERANCE))) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB107,
                    "미래 시각/일자는 입력할 수 없습니다. (" + fieldLabel + "=" + value + ")");
        }
    }

    /** 미래 날짜면 거절한다(LAB107). 동의일(LocalDate)처럼 날짜만 있는 필드에 쓴다. */
    public void rejectIfFuture(LocalDate value, String fieldLabel) {
        if (!strict || value == null) {
            return;
        }
        if (value.isAfter(LocalDate.now())) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB107,
                    "미래 시각/일자는 입력할 수 없습니다. (" + fieldLabel + "=" + value + ")");
        }
    }

    /**
     * later 가 earlier 보다 빠르면 거절한다(LAB108). 검체 인수일시가 채취일시보다 빠를 때 쓴다.
     * 둘 중 하나라도 null 이면(필수 여부는 별도 검증이 담당) 통과시킨다.
     */
    public void rejectIfEarlierThan(
            LocalDateTime later, LocalDateTime earlier, String laterLabel, String earlierLabel) {
        if (!strict || later == null || earlier == null) {
            return;
        }
        if (later.isBefore(earlier)) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB108,
                    "시각 순서가 올바르지 않습니다. (" + laterLabel + "=" + later
                            + " < " + earlierLabel + "=" + earlier + ")");
        }
    }

    /**
     * 날짜(시각 제외)가 오늘보다 과거면 거절한다(LAB116). 검사·영상 일정 등록·재조정에 쓴다.
     * 당일의 이른 시각은 허용한다 — 날짜만 비교하기 때문이다(지시서 §3-B).
     */
    public void rejectIfPastDate(LocalDateTime value, String fieldLabel) {
        if (!strict || value == null) {
            return;
        }
        if (value.toLocalDate().isBefore(LocalDate.now())) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB116,
                    "과거 날짜로 일정을 등록할 수 없습니다. (" + fieldLabel + "=" + value + ")");
        }
    }
}
