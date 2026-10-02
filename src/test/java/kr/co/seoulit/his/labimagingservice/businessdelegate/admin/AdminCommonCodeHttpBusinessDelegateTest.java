package kr.co.seoulit.his.labimagingservice.businessdelegate.admin;

import kr.co.seoulit.his.labimagingservice.businessdelegate.admin.dto.CommonCodeItemResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * admin 공통코드 연동 — 신경로(/api/admin/...) 전환 확인. (I-02, 2026-10-02)
 *
 * ⚠ admin이 구경로(/api/commonCodeGroup/list 등)를 이중 매핑 중이고 "팀 전환 후 제거 예정"이라
 *   제거되기 전에 신경로로 미리 옮겼다. 이 테스트가 구경로를 기대하도록 되돌아가면
 *   MockRestServiceServer가 "요청을 기대하지 않았다"로 실패한다 — 그게 이 테스트의 목적이다.
 */
class AdminCommonCodeHttpBusinessDelegateTest {

    private static final String BASE_URL = "http://admin.test";

    private final RestTemplate restTemplate = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    private final AdminCommonCodeHttpBusinessDelegate delegate =
            new AdminCommonCodeHttpBusinessDelegate(restTemplate, BASE_URL);

    private static final String GROUP_LIST_JSON =
            "{\"code\":200,\"message\":\"SUCCESS\",\"data\":["
                    + "{\"groupCode\":\"TEST_TYPE_CD\",\"groupId\":\"group-1\",\"groupName\":\"Test Item\",\"useYn\":\"Y\"}]}";

    private static final String ITEM_LIST_JSON =
            "{\"code\":200,\"message\":\"SUCCESS\",\"data\":["
                    + "{\"codeId\":\"c1\",\"codeValue\":\"01\",\"codeName\":\"Blood Glucose Test\",\"useYn\":\"Y\",\"groupId\":\"group-1\"},"
                    + "{\"codeId\":\"c2\",\"codeValue\":\"02\",\"codeName\":\"Disabled Item\",\"useYn\":\"N\",\"groupId\":\"group-1\"}]}";

    @Test
    @DisplayName("getAllCodeValues: 신경로(/api/admin/...)를 호출한다 — 구경로를 호출하면 테스트가 실패한다")
    void getAllCodeValuesCallsNewPath() {
        server.expect(requestTo(BASE_URL + "/api/admin/commonCodeGroup/list"))
                .andRespond(withSuccess(GROUP_LIST_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/api/admin/commonCodeItem/list?groupId=group-1"))
                .andRespond(withSuccess(ITEM_LIST_JSON, MediaType.APPLICATION_JSON));

        Map<String, List<String>> result = delegate.getAllCodeValues();

        assertThat(result).containsKey("TEST_TYPE_CD");
        assertThat(result.get("TEST_TYPE_CD")).containsExactly("01"); // useYn=N 인 02 는 빠진다
        server.verify();
    }

    @Test
    @DisplayName("getUsableCodeItems: useYn=N 항목은 빼고 codeValue/codeName 을 돌려준다")
    void getUsableCodeItemsReturnsValueAndName() {
        server.expect(requestTo(BASE_URL + "/api/admin/commonCodeGroup/list"))
                .andRespond(withSuccess(GROUP_LIST_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/api/admin/commonCodeItem/list?groupId=group-1"))
                .andRespond(withSuccess(ITEM_LIST_JSON, MediaType.APPLICATION_JSON));

        List<CommonCodeItemResponse> items = delegate.getUsableCodeItems("TEST_TYPE_CD");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getCodeValue()).isEqualTo("01");
        assertThat(items.get(0).getCodeName()).isEqualTo("Blood Glucose Test");
        server.verify();
    }

    @Test
    @DisplayName("없는 그룹코드는 빈 List를 돌려준다(null 아님) — 항목 조회 API는 부르지 않는다")
    void getUsableCodeItemsUnknownGroupReturnsEmpty() {
        server.expect(requestTo(BASE_URL + "/api/admin/commonCodeGroup/list"))
                .andRespond(withSuccess(GROUP_LIST_JSON, MediaType.APPLICATION_JSON));

        List<CommonCodeItemResponse> items = delegate.getUsableCodeItems("NO_SUCH_GROUP");

        assertThat(items).isEmpty();
        server.verify();
    }
}
