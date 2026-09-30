package kr.co.seoulit.his.labimagingservice.common.session;

/**
 * HttpSession 속성 이름. (5차 Phase 2, 후속조치 #7)
 *
 * ⚠ admin-service 의 AuthService.SESSION_USER_KEY 와 반드시 같아야 한다.
 *   로그인은 admin 이 처리하고(session.setAttribute(LOGIN_USER, SessionUser)), 세션은 Redis 로
 *   서비스 간 공유된다. lab-imaging 은 같은 키로 꺼내기만 한다. 이 문자열이 다르면 에러 없이
 *   "로그인 사용자 없음"이 되어 담당자ID 가 전부 요청값(D2 과도기)으로 돌아간다.
 *   (확인 근거: SEOUL-IT-HIS/adminservice develop 900de73, 2026-09-17 — admin 주석에도
 *    "다른 서비스도 이 키로 꺼낸다, 팀 합의 없이 변경 금지"로 되어 있다)
 *
 * ⚠ 이 서비스는 세션에 값을 쓰지 않는다. 읽기 전용이다.
 */
public final class SessionKeys {

    private SessionKeys() {
    }

    /** 로그인 사용자(kr.co.seoulit.his.common.session.SessionUser) */
    public static final String LOGIN_USER = "LOGIN_USER";
}
