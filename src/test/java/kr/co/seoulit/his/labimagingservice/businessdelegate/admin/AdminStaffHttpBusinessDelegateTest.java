package kr.co.seoulit.his.labimagingservice.businessdelegate.admin;

import kr.co.seoulit.his.labimagingservice.businessdelegate.admin.dto.RoleInfo;
import kr.co.seoulit.his.labimagingservice.businessdelegate.admin.dto.StaffInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * admin 직원·역할 연동 — 신경로 호출 + 인증 헤더 우선순위 확인. (04번 지시서 Phase 2-A/2-E)
 */
class AdminStaffHttpBusinessDelegateTest {

    private static final String BASE_URL = "http://admin.test";

    private static final String EMP_LIST_JSON =
            "{\"message\":\"SUCCESS\",\"data\":["
                    + "{\"empId\":\"emp-1\",\"empNo\":\"10001\",\"empName\":\"Dr. Kim\",\"retireDate\":null,\"roleIds\":[\"role-doctor\"]}]}";

    private static final String ROLE_LIST_JSON =
            "{\"message\":\"SUCCESS\",\"data\":["
                    + "{\"roleId\":\"role-doctor\",\"roleCode\":\"DOCTOR\",\"roleName\":\"의사\",\"useYn\":\"Y\"}]}";

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("getStaffList: 신경로를 호출하고 응답 data를 그대로 돌려준다")
    void getStaffListCallsNewPathAndParsesBody() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        AdminStaffHttpBusinessDelegate delegate =
                new AdminStaffHttpBusinessDelegate(restTemplate, BASE_URL, "", true);

        server.expect(requestTo(BASE_URL + "/api/admin/emp/list"))
                .andExpect(headerDoesNotExist("X-Internal-Api-Key"))
                .andRespond(withSuccess(EMP_LIST_JSON, MediaType.APPLICATION_JSON));

        List<StaffInfo> staff = delegate.getStaffList();

        assertThat(staff).hasSize(1);
        assertThat(staff.get(0).getEmpId()).isEqualTo("emp-1");
        assertThat(staff.get(0).getRoleIds()).containsExactly("role-doctor");
        server.verify();
    }

    @Test
    @DisplayName("getRoleList: 신경로를 호출하고 응답 data를 그대로 돌려준다")
    void getRoleListCallsNewPathAndParsesBody() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        AdminStaffHttpBusinessDelegate delegate =
                new AdminStaffHttpBusinessDelegate(restTemplate, BASE_URL, "", true);

        server.expect(requestTo(BASE_URL + "/api/admin/role/list"))
                .andRespond(withSuccess(ROLE_LIST_JSON, MediaType.APPLICATION_JSON));

        List<RoleInfo> roles = delegate.getRoleList();

        assertThat(roles).hasSize(1);
        assertThat(roles.get(0).getRoleCode()).isEqualTo("DOCTOR");
        server.verify();
    }

    @Test
    @DisplayName("internal-api-key가 설정돼 있으면 X-Internal-Api-Key 헤더로 보낸다(Cookie는 안 본다)")
    void usesInternalApiKeyWhenConfigured() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        AdminStaffHttpBusinessDelegate delegate =
                new AdminStaffHttpBusinessDelegate(restTemplate, BASE_URL, "secret-key", true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Cookie", "SESSION=abc");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        server.expect(requestTo(BASE_URL + "/api/admin/emp/list"))
                .andExpect(header("X-Internal-Api-Key", "secret-key"))
                .andExpect(headerDoesNotExist("Cookie"))
                .andRespond(withSuccess(EMP_LIST_JSON, MediaType.APPLICATION_JSON));

        delegate.getStaffList();

        server.verify();
    }

    @Test
    @DisplayName("internal-api-key가 없고 forward-session-cookie면 현재 요청의 Cookie를 그대로 전달한다")
    void forwardsSessionCookieWhenNoApiKey() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        AdminStaffHttpBusinessDelegate delegate =
                new AdminStaffHttpBusinessDelegate(restTemplate, BASE_URL, "", true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Cookie", "SESSION=abc");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        server.expect(requestTo(BASE_URL + "/api/admin/emp/list"))
                .andExpect(header("Cookie", "SESSION=abc"))
                .andRespond(withSuccess(EMP_LIST_JSON, MediaType.APPLICATION_JSON));

        delegate.getStaffList();

        server.verify();
    }

    @Test
    @DisplayName("forward-session-cookie가 false면 요청 컨텍스트가 있어도 Cookie를 보내지 않는다")
    void doesNotForwardCookieWhenDisabled() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        AdminStaffHttpBusinessDelegate delegate =
                new AdminStaffHttpBusinessDelegate(restTemplate, BASE_URL, "", false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Cookie", "SESSION=abc");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        server.expect(requestTo(BASE_URL + "/api/admin/emp/list"))
                .andExpect(headerDoesNotExist("Cookie"))
                .andRespond(withSuccess(EMP_LIST_JSON, MediaType.APPLICATION_JSON));

        delegate.getStaffList();

        server.verify();
    }

    @Test
    @DisplayName("요청 스레드가 아니면(배치 등) Cookie 분기를 타지 않고 헤더 없이 부른다")
    void noRequestContextMeansNoCookieHeader() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        AdminStaffHttpBusinessDelegate delegate =
                new AdminStaffHttpBusinessDelegate(restTemplate, BASE_URL, "", true);

        // RequestContextHolder 에 아무 것도 설정하지 않은 상태 — 배치/캐시 선제갱신 스레드를 흉내낸다.
        server.expect(requestTo(BASE_URL + "/api/admin/emp/list"))
                .andExpect(headerDoesNotExist("Cookie"))
                .andExpect(headerDoesNotExist("X-Internal-Api-Key"))
                .andRespond(withSuccess(EMP_LIST_JSON, MediaType.APPLICATION_JSON));

        delegate.getStaffList();

        server.verify();
    }
}
