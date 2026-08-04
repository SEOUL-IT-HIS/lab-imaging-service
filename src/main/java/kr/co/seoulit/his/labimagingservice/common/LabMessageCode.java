package kr.co.seoulit.his.labimagingservice.common;

/**
 * 메시지 코드 체계 (개발표준가이드 15.2)
 * {서비스코드 3자리}{일련번호 3자리}
 * — "LAB"은 검사(Lab) 도메인이 아니라 lab-imaging-service 전체를 가리키는 서비스 코드다.
 *   검사/영상 도메인 메시지가 모두 이 클래스에 모여 있는 이유가 이것이다.
 * (프론트 features/labImaging/messages.ts 와 코드-문구를 반드시 맞춰야 함)
 */
public final class LabMessageCode {

    private LabMessageCode() {
    }

    // ---- 검사(LAB) 오더/접수/조회 ----
    public static final String LAB001 = "LAB001"; // 검사 접수가 생성되었습니다.
    public static final String LAB002 = "LAB002"; // 조회된 검사 오더가 없습니다.
    public static final String LAB003 = "LAB003"; // 검사 접수 조회가 성공했습니다.
    public static final String LAB004 = "LAB004"; // 이미 접수된 오더입니다. (중복)

    // ---- 검사(LAB) 일정/조회 ----
    public static final String LAB009 = "LAB009"; // 검사 일정이 등록되었습니다.
    public static final String LAB010 = "LAB010"; // 검사 일정이 재등록되었습니다.
    public static final String LAB013 = "LAB013"; // 검사 접수 정보를 찾을 수 없습니다.
    public static final String LAB014 = "LAB014"; // 재등록할 기존 검사 일정이 없습니다.

    // ---- 영상(IMAGE) 오더/접수/조회 ----
    public static final String LAB005 = "LAB005"; // 영상 접수가 생성되었습니다.
    public static final String LAB006 = "LAB006"; // 조회된 영상 오더가 없습니다.
    public static final String LAB007 = "LAB007"; // 이미 접수된 오더입니다. (중복)
    public static final String LAB008 = "LAB008"; // 영상촬영 접수 조회가 성공했습니다.

    // ---- 영상(IMAGE) 일정/조회 ----
    public static final String LAB011 = "LAB011"; // 영상 일정이 등록되었습니다.
    public static final String LAB012 = "LAB012"; // 영상 일정이 재등록되었습니다.
    public static final String LAB015 = "LAB015"; // 영상 촬영 접수 정보를 찾을 수 없습니다.
    public static final String LAB016 = "LAB016"; // 재등록할 기존 영상 일정이 없습니다.

    // ---- 공통 (도메인과 무관한 기술적 상황만) ----
    public static final String LAB999 = "LAB999"; // 처리 중 오류가 발생했습니다.
    public static final String LAB998 = "LAB998"; // 유효성 검증 / 필수값 누락 / 잘못된 형식
}
