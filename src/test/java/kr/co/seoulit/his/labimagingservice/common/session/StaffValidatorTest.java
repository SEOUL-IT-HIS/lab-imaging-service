package kr.co.seoulit.his.labimagingservice.common.session;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.cache.StaffDirectoryCache;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * StaffValidator — mode(OFF/WARN/ENFORCE) × on-unavailable(ALLOW/REJECT) 조합 확인.
 * (04번 지시서 Phase 2-E)
 */
class StaffValidatorTest {

    @Test
    @DisplayName("OFF면 디렉터리를 조회하지도 않고 통과시킨다")
    void offModeSkipsEntirely() {
        StaffDirectoryCache cache = mock(StaffDirectoryCache.class);
        StaffValidator validator = new StaffValidator(cache, StaffValidator.Mode.OFF, StaffValidator.UnavailablePolicy.ALLOW);

        assertThatCode(() -> validator.requireActiveStaff("not-a-real-emp", "field")).doesNotThrowAnyException();
        assertThatCode(() -> validator.requireDoctor("not-a-real-emp", "field")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("empId가 없으면(null/빈값) 모드와 무관하게 통과시킨다")
    void blankEmpIdAlwaysPasses() {
        StaffDirectoryCache cache = mock(StaffDirectoryCache.class);
        StaffValidator validator = new StaffValidator(cache, StaffValidator.Mode.ENFORCE, StaffValidator.UnavailablePolicy.REJECT);

        assertThatCode(() -> validator.requireActiveStaff(null, "field")).doesNotThrowAnyException();
        assertThatCode(() -> validator.requireActiveStaff("", "field")).doesNotThrowAnyException();
        assertThatCode(() -> validator.requireDoctorIfPresent(null, "field")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("WARN이면 유효하지 않은 직원이어도 막지 않는다")
    void warnModeLogsButDoesNotReject() {
        StaffDirectoryCache cache = mock(StaffDirectoryCache.class);
        when(cache.isAvailable()).thenReturn(true);
        when(cache.isActiveStaff("bad-emp")).thenReturn(false);
        StaffValidator validator = new StaffValidator(cache, StaffValidator.Mode.WARN, StaffValidator.UnavailablePolicy.ALLOW);

        assertThatCode(() -> validator.requireActiveStaff("bad-emp", "field")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ENFORCE면 등록되지 않았거나 퇴사한 직원을 거절한다(LAB110)")
    void enforceModeRejectsInvalidStaff() {
        StaffDirectoryCache cache = mock(StaffDirectoryCache.class);
        when(cache.isAvailable()).thenReturn(true);
        when(cache.isActiveStaff("bad-emp")).thenReturn(false);
        StaffValidator validator = new StaffValidator(cache, StaffValidator.Mode.ENFORCE, StaffValidator.UnavailablePolicy.ALLOW);

        assertThatThrownBy(() -> validator.requireActiveStaff("bad-emp", "field"))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting(e -> ((LabImagingBusinessException) e).getMessageCode())
                .isEqualTo(LabMessageCode.LAB110);
    }

    @Test
    @DisplayName("ENFORCE면 유효한 직원은 통과시킨다")
    void enforceModePassesValidStaff() {
        StaffDirectoryCache cache = mock(StaffDirectoryCache.class);
        when(cache.isAvailable()).thenReturn(true);
        when(cache.isActiveStaff("good-emp")).thenReturn(true);
        StaffValidator validator = new StaffValidator(cache, StaffValidator.Mode.ENFORCE, StaffValidator.UnavailablePolicy.ALLOW);

        assertThatCode(() -> validator.requireActiveStaff("good-emp", "field")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ENFORCE면 의사가 아닌 직원을 거절한다(LAB111)")
    void enforceModeRejectsNonDoctor() {
        StaffDirectoryCache cache = mock(StaffDirectoryCache.class);
        when(cache.isAvailable()).thenReturn(true);
        when(cache.isDoctor("nurse-emp")).thenReturn(false);
        StaffValidator validator = new StaffValidator(cache, StaffValidator.Mode.ENFORCE, StaffValidator.UnavailablePolicy.ALLOW);

        assertThatThrownBy(() -> validator.requireDoctor("nurse-emp", "field"))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting(e -> ((LabImagingBusinessException) e).getMessageCode())
                .isEqualTo(LabMessageCode.LAB111);
    }

    @Test
    @DisplayName("디렉터리 조회 불가 + on-unavailable=ALLOW면 통과시킨다")
    void unavailableWithAllowPasses() {
        StaffDirectoryCache cache = mock(StaffDirectoryCache.class);
        when(cache.isAvailable()).thenReturn(false);
        StaffValidator validator = new StaffValidator(cache, StaffValidator.Mode.ENFORCE, StaffValidator.UnavailablePolicy.ALLOW);

        assertThatCode(() -> validator.requireActiveStaff("any-emp", "field")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("디렉터리 조회 불가 + on-unavailable=REJECT면 거절한다(LAB112) — WARN 모드에서도 거절")
    void unavailableWithRejectRejectsEvenInWarnMode() {
        StaffDirectoryCache cache = mock(StaffDirectoryCache.class);
        when(cache.isAvailable()).thenReturn(false);
        StaffValidator validator = new StaffValidator(cache, StaffValidator.Mode.WARN, StaffValidator.UnavailablePolicy.REJECT);

        assertThatThrownBy(() -> validator.requireActiveStaff("any-emp", "field"))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting(e -> ((LabImagingBusinessException) e).getMessageCode())
                .isEqualTo(LabMessageCode.LAB112);
    }

    @Test
    @DisplayName("requireDoctorIfPresent: 값이 있으면 requireDoctor와 동일하게 검증한다")
    void requireDoctorIfPresentValidatesWhenGiven() {
        StaffDirectoryCache cache = mock(StaffDirectoryCache.class);
        when(cache.isAvailable()).thenReturn(true);
        when(cache.isDoctor("bad-emp")).thenReturn(false);
        StaffValidator validator = new StaffValidator(cache, StaffValidator.Mode.ENFORCE, StaffValidator.UnavailablePolicy.ALLOW);

        assertThatThrownBy(() -> validator.requireDoctorIfPresent("bad-emp", "field"))
                .isInstanceOf(LabImagingBusinessException.class);
    }
}
