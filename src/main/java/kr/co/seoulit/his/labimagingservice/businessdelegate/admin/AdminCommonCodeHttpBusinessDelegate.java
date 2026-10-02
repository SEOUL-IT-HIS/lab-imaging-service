package kr.co.seoulit.his.labimagingservice.businessdelegate.admin;

import kr.co.seoulit.his.labimagingservice.businessdelegate.admin.dto.CommonCodeGroupResponse;
import kr.co.seoulit.his.labimagingservice.businessdelegate.admin.dto.CommonCodeItemResponse;
import kr.co.seoulit.his.labimagingservice.businessdelegate.dto.ExternalApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AdminCommonCodeBusinessDelegate의 RestTemplate 구현체.
 *
 * ⚠ 이 클라이언트를 Service 계층에서 직접 호출하지 않는다.
 *   코드값 검증은 CommonCodeCache(로컬 메모리)가 담당하고, 이 클라이언트는 캐시를 채울 때만 쓰인다.
 *   (2026-08 팀 결정 — 검증 때마다 admin 서비스에 실시간 조회하지 않는다)
 *
 * ⚠ 경로는 API 명세서가 아니라 "admin 서비스에 실제로 구현되어 있는" API에 맞췄다.
 *   (2026-08-04 팀 결정 — admin이 명세서 경로로 바뀌면 그때 이 클래스만 고친다)
 *     2026-10-02: admin 제출 카탈로그 기준으로 신경로(/api/admin/...)로 전환했다 — 외래(OPD)·환자
 *     서비스는 이미 신경로를 쓰고 있고, admin이 구경로(/api/commonCodeGroup/list 등)를 이중 매핑 중이며
 *     "팀 전환 후 제거 예정"이라 미리 옮겼다. 전환 전 구경로·신경로 응답을 직접 조회해 구조가
 *     동일함을 확인했다(둘 다 {code,message,data:[{groupId,groupCode,groupName,useYn}]}).
 *     실제  : GET /api/admin/commonCodeGroup/list,  GET /api/admin/commonCodeItem/list?groupId={groupId}
 *     명세서: GET /api/admin/commonCodes/groups/{groupCode} — 이것도 여전히 admin에 구현되어 있지 않다.
 *   프론트 features/commonCode/api/*.ts 와 같은 2단계 흐름이다(그쪽 경로 전환 여부는 별개).
 *
 * ⚠ 항목 조회 API가 groupCode가 아니라 groupId를 받는다. 그래서 어떤 조회든
 *   "그룹 목록으로 groupCode → groupId 변환" 단계가 먼저 필요하다.
 */
@Slf4j
@Component
public class AdminCommonCodeHttpBusinessDelegate implements AdminCommonCodeBusinessDelegate {

    private static final String CODE_GROUP_LIST_PATH = "/api/admin/commonCodeGroup/list";
    private static final String CODE_ITEM_LIST_PATH = "/api/admin/commonCodeItem/list?groupId={groupId}";

    private static final String USE_YN_Y = "Y";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public AdminCommonCodeHttpBusinessDelegate(RestTemplate restTemplate,
                                               @Value("${app.admin-service.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * 전체 공통코드를 그룹별로 적재한다.
     *
     * ⚠ 그룹 목록 1회 + 그룹당 1회 = N+1 호출이다. admin 서비스에 "모든 그룹 일괄 조회" API가
     *   없어서 프론트와 같은 방식으로 도는 것이고, 요청 처리 경로가 아니라 10분 주기 백그라운드
     *   갱신에서만 실행되므로 감수한다. 벌크 API가 신설되면 이 메서드만 한 번의 호출로 바꾸면 된다.
     */
    @Override
    public Map<String, List<String>> getAllCodeValues() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (CommonCodeGroupResponse group : findUsableGroups()) {
            result.put(group.getGroupCode(), findUsableCodeValues(group.getGroupId()));
        }
        return result;
    }

    /**
     * ⚠ groupCode → groupId 변환이 먼저 필요하다(항목 조회 API가 groupId 를 받는다 — 클래스 주석 참고).
     *   요청마다 그룹 목록을 다시 읽는다 — 이 메서드는 10분 캐시 적재가 아니라 요청 경로에서 쓰이므로
     *   groupId 를 따로 캐시해 두지 않는다(지금 쓰는 곳이 하나뿐이라 과한 최적화를 피했다).
     */
    @Override
    public List<CommonCodeItemResponse> getUsableCodeItems(String groupCode) {
        String groupId = findUsableGroups().stream()
                .filter(group -> groupCode.equals(group.getGroupCode()))
                .map(CommonCodeGroupResponse::getGroupId)
                .findFirst()
                .orElse(null);

        if (groupId == null) {
            log.warn("존재하지 않거나 사용중이 아닌 공통코드 그룹입니다. groupCode={}", groupCode);
            return List.of();
        }
        return findUsableCodeItems(groupId);
    }

    /** 사용중(useYn='Y')이고 groupCode/groupId가 온전한 그룹만 반환. */
    private List<CommonCodeGroupResponse> findUsableGroups() {
        ResponseEntity<ExternalApiResponse<List<CommonCodeGroupResponse>>> response = restTemplate.exchange(
                baseUrl + CODE_GROUP_LIST_PATH,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {
                });

        ExternalApiResponse<List<CommonCodeGroupResponse>> body = response.getBody();
        List<CommonCodeGroupResponse> groups = (body == null) ? null : body.getData();
        if (groups == null) {
            log.warn("공통코드 그룹 목록 응답 본문이 비어 있습니다.");
            return List.of();
        }

        return groups.stream()
                .filter(group -> USE_YN_Y.equals(group.getUseYn()))
                .filter(group -> group.getGroupCode() != null && group.getGroupId() != null)
                .toList();
    }

    /** 그룹의 사용중(useYn='Y') 코드값 목록. */
    private List<String> findUsableCodeValues(String groupId) {
        return findUsableCodeItems(groupId).stream()
                .map(CommonCodeItemResponse::getCodeValue)
                .filter(Objects::nonNull)
                .toList();
    }

    /** 그룹의 사용중(useYn='Y') 코드 항목 전체(코드값+이름). */
    private List<CommonCodeItemResponse> findUsableCodeItems(String groupId) {
        try {
            ResponseEntity<ExternalApiResponse<List<CommonCodeItemResponse>>> response = restTemplate.exchange(
                    baseUrl + CODE_ITEM_LIST_PATH,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {
                    },
                    groupId);

            ExternalApiResponse<List<CommonCodeItemResponse>> body = response.getBody();
            List<CommonCodeItemResponse> items = (body == null) ? null : body.getData();
            if (items == null) {
                log.warn("공통코드 항목 응답 본문이 비어 있습니다. groupId={}", groupId);
                return List.of();
            }

            return items.stream()
                    .filter(item -> USE_YN_Y.equals(item.getUseYn()))
                    .toList();

        } catch (HttpClientErrorException.NotFound e) {
            log.warn("존재하지 않는 공통코드 그룹입니다. groupId={}", groupId);
            return List.of();
        }
    }
}
