package kr.co.seoulit.his.labimagingservice.common.session;

import kr.co.seoulit.his.common.session.SessionUser;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.common.exception.LoginRequiredException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 담당자ID 결정 규칙 (D2, 5차 Phase 2).
 */
class ActorIdResolverTest {

    private static final String EMP_ID = "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0"; // 36자 UUID

    private SessionUser login() {
        SessionUser user = new SessionUser();
        user.setEmpId(EMP_ID);
        return user;
    }

    @Test
    @DisplayName("세션이 있으면 요청값을 무시하고 empId 를 쓴다")
    void sessionWins() {
        ActorIdResolver resolver = new ActorIdResolver(false);
        assertThat(resolver.resolve(login(), "someone-else", "recordedById")).isEqualTo(EMP_ID);
        assertThat(resolver.resolve(login(), null, "recordedById")).isEqualTo(EMP_ID);
    }

    @Test
    @DisplayName("과도기(false): 세션이 없으면 요청값을 쓴다")
    void transitionalUsesRequest() {
        ActorIdResolver resolver = new ActorIdResolver(false);
        assertThat(resolver.resolve(null, "STF00021", "recordedById")).isEqualTo("STF00021");
    }

    @Test
    @DisplayName("과도기(false): 세션도 요청값도 없으면 LAB998")
    void transitionalNothing() {
        ActorIdResolver resolver = new ActorIdResolver(false);
        assertThatThrownBy(() -> resolver.resolve(null, " ", "recordedById"))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB998);
    }

    @Test
    @DisplayName("필수(true): 세션이 없으면 요청값이 있어도 401(LAB067)")
    void requiredRejects() {
        ActorIdResolver resolver = new ActorIdResolver(true);
        assertThatThrownBy(() -> resolver.resolve(null, "STF00021", "confirmedById"))
                .isInstanceOf(LoginRequiredException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB067);
    }
}
