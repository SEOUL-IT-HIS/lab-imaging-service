package kr.co.seoulit.his.labimagingservice.businessdelegate.admin;

import java.util.List;
import java.util.Map;

/**
 * Admin Service(공통코드) 연동 클라이언트.
 *
 * 구현체: AdminCommonCodeHttpBusinessDelegate (RestTemplate)
 *
 * ⚠ 2026-08 팀 결정 — 코드값 검증은 admin 서비스에 매번 실시간 조회하지 않고
 *   CommonCodeCache(로컬 메모리 캐시)를 통해 수행한다.
 *   따라서 Service 계층은 이 인터페이스를 직접 주입받지 말고 CommonCodeCache를 사용한다.
 *   이 인터페이스는 캐시를 채우는 통로 역할이다.
 *
 * 연동 API — admin 서비스에 실제로 구현되어 프론트가 쓰고 있는 경로를 따른다.
 *   GET /api/commonCodeGroup/list                   — 코드그룹 목록 (groupCode → groupId)
 *   GET /api/commonCodeItem/list?groupId={groupId}  — 그룹별 코드항목
 *   (명세서상 경로는 /api/admin/commonCodes/groups/{groupCode} 이지만, admin이 그쪽으로
 *    전환하기 전까지는 실제 구현을 따른다 — 2026-08-04 팀 결정)
 */
public interface AdminCommonCodeBusinessDelegate {

    /**
     * 특정 공통코드 그룹의 유효 코드값 목록을 조회한다.
     *
     * @param groupCode 코드그룹ID (예: TRANSMIT_STATUS_CD)
     * @return 사용 중인 코드값 목록
     */
    List<String> getCodeValues(String groupCode);

    /**
     * 전체 공통코드를 그룹별로 조회한다. (CommonCodeCache 적재용 벌크 조회)
     *
     * ⚠ admin 서비스에 "모든 그룹 일괄 조회" 단일 엔드포인트가 없어, 구현체는
     *   그룹 목록을 읽고 그룹마다 항목을 조회하는 방식(N+1)으로 채운다.
     *   벌크 API가 신설되면 구현체만 한 번의 호출로 바꾸면 된다.
     *
     * @return 코드그룹ID → 사용 중인 코드값 목록. 조회 불가 시 빈 Map(null 아님)
     */
    Map<String, List<String>> getAllCodeValues();

    /**
     * 코드값이 해당 그룹 내 유효한 값인지 확인한다.
     *
     * @param groupCode 코드그룹ID
     * @param code      확인할 코드값
     * @return 유효하면 true
     */
    boolean isValidCode(String groupCode, String code);
}
