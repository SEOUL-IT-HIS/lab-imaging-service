package kr.co.seoulit.his.labimagingservice.client;

import java.util.List;

/**
 * Admin Service(공통코드) 연동 클라이언트.
 *
 * ⚠ 현재는 뼈대(인터페이스)만 존재하며 구현체가 없습니다. (PatientServiceClient와 동일한 상태)
 *
 * 참고 API(Admin_Staff_Provider_API.xlsx 기준):
 *   GET /api/admin/commonCodes/groups/{groupCode}
 */
public interface AdminCommonCodeClient {

    /**
     * 특정 공통코드 그룹의 유효 코드값 목록을 조회한다.
     *
     * @param groupCode 코드그룹ID (예: TRANSMIT_STATUS_CD)
     * @return 사용 중인 코드값 목록
     */
    List<String> getCodeValues(String groupCode);

    /**
     * 코드값이 해당 그룹 내 유효한 값인지 확인한다.
     *
     * @param groupCode 코드그룹ID
     * @param code      확인할 코드값
     * @return 유효하면 true
     */
    boolean isValidCode(String groupCode, String code);
}
