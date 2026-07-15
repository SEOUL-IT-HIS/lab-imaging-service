package kr.co.seoulit.his.labimagingservice.common;

/**
 * 메시지 코드 체계 (개발표준가이드 15.2)
 * {서비스코드 3자리}{일련번호 3자리} — 검사영상관리서비스는 "LAB" 코드를 사용한다.
 * (프론트 features/labImaging/messages.ts 와 코드-문구를 반드시 맞춰야 함)
 */
public final class LabMessageCode {

    private LabMessageCode() {
    }

    // ---- 검사(LAB) 오더/접수 ----
    public static final String LAB001 = "LAB001"; // 검사 접수가 생성되었습니다.
    public static final String LAB002 = "LAB002"; // 조회된 검사 오더가 없습니다.
    public static final String LAB003 = "LAB003"; // 필수 항목이 누락되어 접수를 처리할 수 없습니다.
    public static final String LAB004 = "LAB004"; // 이미 접수된 오더입니다. (중복)

    // ---- 영상(IMAGE) 오더/접수 ----
    public static final String LAB005 = "LAB005"; // 영상 접수가 생성되었습니다.
    public static final String LAB006 = "LAB006"; // 조회된 영상 오더가 없습니다.
    public static final String LAB007 = "LAB007"; // 필수 항목이 누락되어 접수를 처리할 수 없습니다.
    public static final String LAB008 = "LAB008"; // 이미 접수된 오더입니다. (중복)

    // ---- 공통 ----
    public static final String LAB999 = "LAB999"; // 처리 중 오류가 발생했습니다.
}
