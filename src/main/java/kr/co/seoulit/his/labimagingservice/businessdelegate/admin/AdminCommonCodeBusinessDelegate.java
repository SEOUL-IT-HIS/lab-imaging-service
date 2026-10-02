package kr.co.seoulit.his.labimagingservice.businessdelegate.admin;

import kr.co.seoulit.his.labimagingservice.businessdelegate.admin.dto.CommonCodeItemResponse;

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
 * 연동 API — admin 서비스에 실제로 구현되어 있는 경로를 따른다.
 *   GET /api/admin/commonCodeGroup/list                   — 코드그룹 목록 (groupCode → groupId)
 *   GET /api/admin/commonCodeItem/list?groupId={groupId}  — 그룹별 코드항목
 *   (명세서상 경로는 /api/admin/commonCodes/groups/{groupCode} 이지만, admin에 아직 구현되어
 *    있지 않다 — 2026-08-04 팀 결정, 2026-10-02 재확인)
 *
 * ⚠ 2026-10-02: 위 경로는 원래 /api/commonCodeGroup/list, /api/commonCodeItem/list(버전·
 *   /admin 접두어 없음)였다. 외래(OPD)·환자 서비스가 이미 쓰고 있던 /api/admin/... 신경로로
 *   admin이 구경로를 이중 매핑해 두고 "팀 전환 후 제거 예정"이라고 해, 제거되기 전에 먼저
 *   옮겨 왔다. 구경로·신경로 응답 구조가 동일함을 조회로 확인했다.
 */
public interface AdminCommonCodeBusinessDelegate {

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
     * 한 그룹의 사용중(useYn='Y') 코드 항목 전체(코드값+이름)를 조회한다.
     * (검사항목 카탈로그 검색 — LabItemCatalogService, 2026-10-02)
     *
     * ⚠ getAllCodeValues()와 다르다. 그쪽은 CommonCodeCache 적재용이라 코드값만 뽑아 쓰고
     *   주기적으로만(10분) 호출된다. 이건 요청이 올 때마다 admin에 직접 물어 "지금" 등록된
     *   이름까지 그대로 돌려줘야 하는 곳(화면 검색·자동완성)에서 쓴다 — 결과를 캐시하지 않는다.
     *
     * @return 사용중 코드 항목 목록. 그룹이 없거나 조회 불가 시 빈 List(null 아님)
     */
    List<CommonCodeItemResponse> getUsableCodeItems(String groupCode);
}
