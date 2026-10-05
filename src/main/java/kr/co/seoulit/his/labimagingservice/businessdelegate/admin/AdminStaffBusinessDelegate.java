package kr.co.seoulit.his.labimagingservice.businessdelegate.admin;

import kr.co.seoulit.his.labimagingservice.businessdelegate.admin.dto.RoleInfo;
import kr.co.seoulit.his.labimagingservice.businessdelegate.admin.dto.StaffInfo;

import java.util.List;

/**
 * Admin Service(직원·역할) 연동 클라이언트. (직원 검증, 2026-10-05, 04번 지시서 Phase 2-A)
 *
 * 구현체: AdminStaffHttpBusinessDelegate (RestTemplate)
 *
 * ⚠ AdminCommonCodeBusinessDelegate 와 같은 자리다 — Service 계층은 이 인터페이스를
 *   직접 주입받지 말고 StaffDirectoryCache 를 통해 쓴다. 이 인터페이스는 캐시를 채우는
 *   통로일 뿐이다.
 *
 * 연동 API — 신경로만 쓴다(레거시 경로 사용 금지, 지시서 §2-A-2).
 *   GET /api/admin/emp/list
 *   GET /api/admin/role/list
 *
 * ⚠ 두 API 모두 인증이 필요하다(비로그인 호출 시 401 확인됨 — Phase 0-4).
 *   구현체의 인증 처리는 AdminStaffHttpBusinessDelegate 클래스 주석 참고.
 */
public interface AdminStaffBusinessDelegate {

    /**
     * 직원 전체 목록을 조회한다. (StaffDirectoryCache 적재용)
     *
     * @return 직원 목록. 조회 불가 시 빈 List(null 아님) — 호출부가 "조회 실패"와
     *         "직원 0명"을 구분해야 하면 예외를 직접 잡아야 한다(이 메서드는 구분하지 않는다).
     */
    List<StaffInfo> getStaffList();

    /**
     * 역할 전체 목록을 조회한다. (의사 역할 판별용)
     *
     * @return 역할 목록. 조회 불가 시 빈 List(null 아님)
     */
    List<RoleInfo> getRoleList();
}
