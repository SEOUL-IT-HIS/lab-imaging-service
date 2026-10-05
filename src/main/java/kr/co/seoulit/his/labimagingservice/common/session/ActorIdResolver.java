package kr.co.seoulit.his.labimagingservice.common.session;

import kr.co.seoulit.his.common.session.SessionUser;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.common.exception.LoginRequiredException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 담당자ID(접수자·채취자·판정자·입력자·확정자·서명자·업로더·확인자)를 정한다. (5차 Phase 2, 후속조치 #7)
 *
 * 규칙 (D2)
 *   1) 로그인 세션이 있으면 → SessionUser.empId. 요청에 담긴 값은 무시한다.
 *      화면에서 남의 ID 를 적어 보내도 "실제로 처리한 사람"으로 기록되도록 하기 위해서다.
 *   2) 세션이 없고 app.auth.actor-from-session-required=true → 401 (LAB067)
 *   3) 세션이 없고 false(기본값, 과도기) → 요청값을 쓰고 WARN 을 남긴다. 요청값도 없으면 LAB998.
 *
 * ⚠ 기본값을 false 로 둔 이유 — admin 세션 공유는 원격 통합 서버에서만 확실히 된다.
 *   개발 PC 에서 Postman 으로 lab-imaging 만 띄워 테스트하거나, admin 과 다른 Redis 를 볼 때도
 *   기존처럼 요청값으로 동작해야 개발이 막히지 않는다. 운영 전환 시 true 로 바꾸면 된다.
 *
 * ⚠ 컨트롤러에서만 부른다. Kafka 연계 경로(LabOrderIntakeService 의 "SYSTEM")처럼
 *   사람이 없는 호출은 이 클래스를 거치지 않고 서비스에 바로 들어간다.
 *
 * ⚠ empId 는 admin EMPLOYEE.EMP_ID(VARCHAR2(36) UUID)다. 담당자ID 컬럼을 20 → 36 으로
 *   늘린 이유가 이것이다(lab_imaging_schema_최종보강.sql PART 1-1).
 *
 * ⚠ 세션이 있을 때(loginUser.getEmpId())는 StaffValidator 를 거치지 않는다. 로그인 자체가
 *   admin 인증을 통과했다는 뜻이라 다시 디렉터리에 물을 필요가 없다. 세션이 없어 요청값을
 *   그대로 쓰는 과도기 분기(③)에서만 StaffValidator.requireActiveStaff 로 확인한다 —
 *   화면이 아무 문자열이나 적어 보내도 디렉터리에 없는 값이면 모드에 따라 걸러진다.
 *   (직원 검증, 2026-10-05, 04번 지시서 Phase 2-B)
 */
@Slf4j
@Component
public class ActorIdResolver {

    private final boolean sessionRequired;
    private final StaffValidator staffValidator;

    public ActorIdResolver(
            @Value("${app.auth.actor-from-session-required:false}") boolean sessionRequired,
            StaffValidator staffValidator) {
        this.sessionRequired = sessionRequired;
        this.staffValidator = staffValidator;
    }

    /**
     * @param loginUser   @LoginUser 로 받은 세션 사용자 (없으면 null)
     * @param requestedId 요청 DTO 에 담겨 온 담당자ID (없을 수 있음)
     * @param fieldName   로그·오류 메시지용 필드명 (예: "recordedById")
     * @return 기록할 담당자ID
     */
    public String resolve(SessionUser loginUser, String requestedId, String fieldName) {

        if (loginUser != null && hasText(loginUser.getEmpId())) {
            if (hasText(requestedId) && !requestedId.equals(loginUser.getEmpId())) {
                // 무시하되 흔적은 남긴다. 화면이 아직 수동 입력값을 보내고 있다는 신호다.
                log.info("[ACTOR] {} 요청값을 무시하고 로그인 사용자로 기록합니다. (요청={}, 로그인 empId={})",
                        fieldName, requestedId, loginUser.getEmpId());
            }
            return loginUser.getEmpId();
        }

        if (sessionRequired) {
            throw new LoginRequiredException("로그인이 필요합니다. (" + fieldName + ")");
        }

        if (!hasText(requestedId)) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB998,
                    "담당자ID(" + fieldName + ")가 없습니다. 로그인 세션도 요청값도 없습니다.");
        }

        log.warn("[ACTOR] 로그인 세션이 없어 {} 에 요청값을 사용합니다. (과도기 — app.auth.actor-from-session-required=false)",
                fieldName);
        staffValidator.requireActiveStaff(requestedId, fieldName);
        return requestedId;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
