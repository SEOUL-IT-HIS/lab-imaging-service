package kr.co.seoulit.his.labimagingservice.businessdelegate.admin;

import kr.co.seoulit.his.labimagingservice.businessdelegate.admin.dto.RoleInfo;
import kr.co.seoulit.his.labimagingservice.businessdelegate.admin.dto.StaffInfo;
import kr.co.seoulit.his.labimagingservice.businessdelegate.dto.ExternalApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * AdminStaffBusinessDelegate의 RestTemplate 구현체. (직원 검증, 2026-10-05, 04번 지시서 Phase 2-A)
 *
 * AdminCommonCodeHttpBusinessDelegate 구조를 그대로 본떴다(지시서 §2-A) — 신경로만,
 * 공유 RestTemplate(3s 타임아웃) 그대로, 실패를 삼키지 않고 호출부(StaffDirectoryCache)로
 * 예외를 올려보내 "조회 실패 시 기존 캐시 유지" 판단을 그쪽이 하게 한다.
 *
 * ⚠ 인증 — emp/role list 는 비로그인 호출 시 401 이다(Phase 0-4 확인). 우선순위(지시서 §2-A-2):
 *   1) app.admin-service.internal-api-key 가 설정돼 있으면 X-Internal-Api-Key 헤더로 보낸다.
 *   2) 없고 app.staff-directory.forward-session-cookie(기본 true) 이며 지금 스레드에 요청
 *      컨텍스트가 있으면, 그 요청의 Cookie 헤더를 그대로 실어 보낸다(로그인한 사용자의 세션으로
 *      admin 을 대신 불러주는 셈). 배치·캐시 선제갱신처럼 요청 스레드가 아닌 곳에서 부르면
 *      컨텍스트가 없어 이 분기를 타지 않는다.
 *   3) 둘 다 아니면 헤더 없이 부른다 — admin 이 401 을 돌려주면 HttpClientErrorException 이
 *      그대로 올라가고, StaffDirectoryCache 가 "조회 불가"로 처리한다.
 *
 * ⚠ internal-api-key 는 운영 값이라 application.properties 에 적지 않는다(개인 PC IP 포함 파일,
 *   지시서 §0-4) — 기본값 빈 문자열이라 설정하지 않으면 2)번 분기로 자연히 넘어간다.
 */
@Slf4j
@Component
public class AdminStaffHttpBusinessDelegate implements AdminStaffBusinessDelegate {

    private static final String EMP_LIST_PATH = "/api/admin/emp/list";
    private static final String ROLE_LIST_PATH = "/api/admin/role/list";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String internalApiKey;
    private final boolean forwardSessionCookie;

    public AdminStaffHttpBusinessDelegate(
            RestTemplate restTemplate,
            @Value("${app.admin-service.base-url}") String baseUrl,
            @Value("${app.admin-service.internal-api-key:}") String internalApiKey,
            @Value("${app.staff-directory.forward-session-cookie:true}") boolean forwardSessionCookie) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.internalApiKey = internalApiKey;
        this.forwardSessionCookie = forwardSessionCookie;
    }

    @Override
    public List<StaffInfo> getStaffList() {
        ResponseEntity<ExternalApiResponse<List<StaffInfo>>> response = restTemplate.exchange(
                baseUrl + EMP_LIST_PATH,
                HttpMethod.GET,
                authEntity(),
                new ParameterizedTypeReference<>() {
                });

        ExternalApiResponse<List<StaffInfo>> body = response.getBody();
        List<StaffInfo> staff = (body == null) ? null : body.getData();
        if (staff == null) {
            log.warn("직원 목록 응답 본문이 비어 있습니다.");
            return List.of();
        }
        return staff;
    }

    @Override
    public List<RoleInfo> getRoleList() {
        ResponseEntity<ExternalApiResponse<List<RoleInfo>>> response = restTemplate.exchange(
                baseUrl + ROLE_LIST_PATH,
                HttpMethod.GET,
                authEntity(),
                new ParameterizedTypeReference<>() {
                });

        ExternalApiResponse<List<RoleInfo>> body = response.getBody();
        List<RoleInfo> roles = (body == null) ? null : body.getData();
        if (roles == null) {
            log.warn("역할 목록 응답 본문이 비어 있습니다.");
            return List.of();
        }
        return roles;
    }

    private HttpEntity<Void> authEntity() {
        HttpHeaders headers = new HttpHeaders();
        if (internalApiKey != null && !internalApiKey.isBlank()) {
            headers.set("X-Internal-Api-Key", internalApiKey);
        } else if (forwardSessionCookie) {
            String cookie = currentRequestCookieHeader();
            if (cookie != null) {
                headers.set(HttpHeaders.COOKIE, cookie);
            }
        }
        return new HttpEntity<>(headers);
    }

    /** 현재 요청의 Cookie 헤더를 그대로 꺼낸다. 요청 스레드가 아니면(배치 등) null. */
    private String currentRequestCookieHeader() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            return null;
        }
        return servletAttrs.getRequest().getHeader(HttpHeaders.COOKIE);
    }
}
