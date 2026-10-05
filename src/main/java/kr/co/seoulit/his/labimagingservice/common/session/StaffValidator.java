package kr.co.seoulit.his.labimagingservice.common.session;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.cache.StaffDirectoryCache;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 직원 검증. (직원 검증, 2026-10-05, 04번 지시서 Phase 2-A)
 *
 * 모드(app.staff-validation.mode, 기본 WARN):
 *   OFF     — 검증하지 않는다(과거 동작 그대로).
 *   WARN    — 검증하되 실패해도 막지 않는다. empId 만 로그로 남긴다(이름·사번은 남기지 않는다).
 *   ENFORCE — 검증 실패 시 거절한다(LabImagingBusinessException).
 *
 * 디렉터리 조회 불가 시 정책(app.staff-validation.on-unavailable, 기본 ALLOW):
 *   ALLOW   — "모른다"를 "문제없다"로 취급해 통과시킨다. admin 이 잠깐 죽었다고 접수·배정이
 *             전부 막히면 안 된다는 판단 — CommonCodeCache 의 fail-open 과 같은 결이다.
 *   REJECT  — 통과시키지 않는다(LAB112). 더 엄격한 운영에서 쓸 수 있도록 옵션만 열어둔다.
 *
 * ⚠ intake 경로(LabOrderIntakeService/ImageOrderIntakeService)는 이 클래스를 쓰지 않는다.
 *   (지시서 §0-2 — intake 는 직원/의사 사유로 절대 거절하면 안 된다, WARN 로그만)
 *   그 경로는 StaffDirectoryCache 를 직접 "로그만" 목적으로 부른다.
 */
@Slf4j
@Component
public class StaffValidator {

    public enum Mode { OFF, WARN, ENFORCE }

    public enum UnavailablePolicy { ALLOW, REJECT }

    private final StaffDirectoryCache staffDirectoryCache;
    private final Mode mode;
    private final UnavailablePolicy onUnavailable;

    public StaffValidator(
            StaffDirectoryCache staffDirectoryCache,
            @Value("${app.staff-validation.mode:WARN}") Mode mode,
            @Value("${app.staff-validation.on-unavailable:ALLOW}") UnavailablePolicy onUnavailable) {
        this.staffDirectoryCache = staffDirectoryCache;
        this.mode = mode;
        this.onUnavailable = onUnavailable;
    }

    /**
     * empId가 등록되어 있고 재직 중인지 확인한다. (지정된 담당자 전반)
     *
     * @param empId     확인할 직원ID. null/빈 값이면 통과시킨다 — 필수값 여부는 호출부가 이미
     *                  따로 검증했다고 보고, 이 메서드는 "값이 있다면 유효한가"만 본다.
     * @param fieldName 로그·오류 메시지용 필드명
     */
    public void requireActiveStaff(String empId, String fieldName) {
        if (empId == null || empId.isBlank() || mode == Mode.OFF) {
            return;
        }
        if (!staffDirectoryCache.isAvailable()) {
            handleUnavailable(fieldName);
            return;
        }
        if (!staffDirectoryCache.isActiveStaff(empId)) {
            reject(LabMessageCode.LAB110, fieldName, empId, "등록되어 있지 않거나 퇴사한 직원");
        }
    }

    /** empId가 등록되어 있고 재직 중이며 의사 역할인지 확인한다. (판독 배정 등 의사 전용 작업) */
    public void requireDoctor(String empId, String fieldName) {
        if (empId == null || empId.isBlank() || mode == Mode.OFF) {
            return;
        }
        if (!staffDirectoryCache.isAvailable()) {
            handleUnavailable(fieldName);
            return;
        }
        if (!staffDirectoryCache.isDoctor(empId)) {
            reject(LabMessageCode.LAB111, fieldName, empId, "의사 역할이 아니거나 등록되어 있지 않은 직원");
        }
    }

    /**
     * empId가 있을 때만 requireDoctor 와 동일하게 확인한다. (선택 입력 필드용 — 수동 접수의
     * 처방의 란처럼 비워 둘 수 있는 자리에서, "비었다"와 "값은 있는데 의사가 아니다"를 호출부가
     * 구분해서 쓰지 않아도 되도록 이름을 따로 둔다. requireDoctor 자체가 이미 null/blank 를
     * 통과시키므로 구현은 그대로 위임한다.)
     */
    public void requireDoctorIfPresent(String empId, String fieldName) {
        requireDoctor(empId, fieldName);
    }

    private void handleUnavailable(String fieldName) {
        if (onUnavailable == UnavailablePolicy.REJECT) {
            throw new LabImagingBusinessException(LabMessageCode.LAB112,
                    "직원 정보를 확인할 수 없습니다. (" + fieldName + ")");
        }
        log.warn("[STAFF_VALIDATION] 직원 디렉터리를 조회할 수 없어 통과시킵니다. (on-unavailable=ALLOW, field={})",
                fieldName);
    }

    private void reject(String messageCode, String fieldName, String empId, String reason) {
        if (mode == Mode.ENFORCE) {
            throw new LabImagingBusinessException(messageCode, reason + "입니다. (" + fieldName + ")");
        }
        log.warn("[STAFF_VALIDATION] {} — {} (field={}, empId={}, mode=WARN이라 통과시킵니다)",
                reason, messageCode, fieldName, empId);
    }
}
