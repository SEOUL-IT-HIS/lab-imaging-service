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
 * ⚠ 경로는 API 명세서가 아니라 "admin 서비스에 실제로 구현되어 프론트가 쓰고 있는" API에 맞췄다.
 *   (2026-08-04 팀 결정 — admin이 명세서 경로로 바뀌면 그때 이 클래스만 고친다)
 *     실제  : GET /api/commonCodeGroup/list,  GET /api/commonCodeItem/list?groupId={groupId}
 *     명세서: GET /api/admin/commonCodes/groups/{groupCode}
 *   프론트 features/commonCode/api/*.ts 와 같은 경로·같은 2단계 흐름이다.
 *
 * ⚠ 항목 조회 API가 groupCode가 아니라 groupId를 받는다. 그래서 어떤 조회든
 *   "그룹 목록으로 groupCode → groupId 변환" 단계가 먼저 필요하다.
 */
@Slf4j
@Component
public class AdminCommonCodeHttpBusinessDelegate implements AdminCommonCodeBusinessDelegate {

    private static final String CODE_GROUP_LIST_PATH = "/api/commonCodeGroup/list";
    private static final String CODE_ITEM_LIST_PATH = "/api/commonCodeItem/list?groupId={groupId}";

    private static final String USE_YN_Y = "Y";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public AdminCommonCodeHttpBusinessDelegate(RestTemplate restTemplate,
                                               @Value("${app.admin-service.host}") String host,
                                               @Value("${app.admin-service.port}") int port) {
        this.restTemplate = restTemplate;
        this.baseUrl = "http://" + host + ":" + port;
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
                    .map(CommonCodeItemResponse::getCodeValue)
                    .filter(Objects::nonNull)
                    .toList();

        } catch (HttpClientErrorException.NotFound e) {
            log.warn("존재하지 않는 공통코드 그룹입니다. groupId={}", groupId);
            return List.of();
        }
    }
}
