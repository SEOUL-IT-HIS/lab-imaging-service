package kr.co.seoulit.his.labimagingservice.common.session;

import kr.co.seoulit.his.common.session.SessionUser;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.cache.StaffDirectoryCache;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.common.exception.LoginRequiredException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 담당자ID 결정 규칙 (D2, 5차 Phase 2 / 직원 검증 연동, 04번 지시서 Phase 2-B).
 */
class ActorIdResolverTest {

    private static final String EMP_ID = "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0"; // 36자 UUID

    /**
     * StaffValidator.Mode.OFF — 이 테스트들은 ActorIdResolver 자체의 결정 규칙(D2)만 본다.
     * 직원 검증의 모드별 동작은 StaffValidatorTest 가 이미 담당한다(중복 검증 방지).
     */
    private static ActorIdResolver resolver(boolean sessionRequired) {
        StaffValidator staffValidator = new StaffValidator(
                mock(StaffDirectoryCache.class), StaffValidator.Mode.OFF, StaffValidator.UnavailablePolicy.ALLOW);
        return new ActorIdResolver(sessionRequired, staffValidator);
    }

    private SessionUser login() {
        SessionUser user = new SessionUser();
        user.setEmpId(EMP_ID);
        return user;
    }

    @Test
    @DisplayName("세션이 있으면 요청값을 무시하고 empId 를 쓴다")
    void sessionWins() {
        ActorIdResolver resolver = resolver(false);
        assertThat(resolver.resolve(login(), "someone-else", "recordedById")).isEqualTo(EMP_ID);
        assertThat(resolver.resolve(login(), null, "recordedById")).isEqualTo(EMP_ID);
    }

    @Test
    @DisplayName("과도기(false): 세션이 없으면 요청값을 쓴다")
    void transitionalUsesRequest() {
        ActorIdResolver resolver = resolver(false);
        assertThat(resolver.resolve(null, "STF00021", "recordedById")).isEqualTo("STF00021");
    }

    @Test
    @DisplayName("과도기(false): 세션도 요청값도 없으면 LAB998")
    void transitionalNothing() {
        ActorIdResolver resolver = resolver(false);
        assertThatThrownBy(() -> resolver.resolve(null, " ", "recordedById"))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB998);
    }

    @Test
    @DisplayName("필수(true): 세션이 없으면 요청값이 있어도 401(LAB067)")
    void requiredRejects() {
        ActorIdResolver resolver = resolver(true);
        assertThatThrownBy(() -> resolver.resolve(null, "STF00021", "confirmedById"))
                .isInstanceOf(LoginRequiredException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB067);
    }

    @Test
    @DisplayName("세션이 있으면 StaffValidator 를 거치지 않는다 — 로그인 자체가 인증이다")
    void sessionSkipsStaffValidation() {
        StaffDirectoryCache staffDirectoryCache = mock(StaffDirectoryCache.class);
        StaffValidator staffValidator = new StaffValidator(
                staffDirectoryCache, StaffValidator.Mode.ENFORCE, StaffValidator.UnavailablePolicy.ALLOW);
        ActorIdResolver resolver = new ActorIdResolver(false, staffValidator);

        resolver.resolve(login(), null, "recordedById");

        verify(staffDirectoryCache, never()).isAvailable();
    }

    @Test
    @DisplayName("과도기(false): 세션이 없는 요청값은 StaffValidator 를 거친다 — ENFORCE면 거절")
    void transitionalFallbackGoesThroughStaffValidation() {
        StaffDirectoryCache staffDirectoryCache = mock(StaffDirectoryCache.class);
        when(staffDirectoryCache.isAvailable()).thenReturn(true);
        when(staffDirectoryCache.isActiveStaff("not-a-real-emp")).thenReturn(false);
        StaffValidator staffValidator = new StaffValidator(
                staffDirectoryCache, StaffValidator.Mode.ENFORCE, StaffValidator.UnavailablePolicy.ALLOW);
        ActorIdResolver resolver = new ActorIdResolver(false, staffValidator);

        assertThatThrownBy(() -> resolver.resolve(null, "not-a-real-emp", "recordedById"))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB110);
    }
}
