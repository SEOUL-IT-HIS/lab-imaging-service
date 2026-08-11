package kr.co.seoulit.his.labimagingservice.businessdelegate.admin.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * admin-service의 공통코드 그룹 1건.
 *
 * 필드명은 admin 서비스의 프론트
 * features/commonCode/types/commonCodeGroupTypes.ts 와 맞춘 것이다.
 *   { groupId, groupCode, groupName, useYn }
 *
 * 항목 조회 API가 groupCode가 아니라 groupId를 받기 때문에,
 * "groupCode → groupId" 변환을 위해 그룹 목록을 먼저 읽어야 한다.
 * groupName은 표시용이라 매핑하지 않는다.
 *
 * ⚠ 매핑하지 않은 groupName 은 Spring Boot의 Jackson 기본 설정
 *   (FAIL_ON_UNKNOWN_PROPERTIES 비활성)이 조용히 무시한다.
 *   상세는 ExternalApiResponse 주석 참고.
 *    @JsonIgnoreProperties 전역설정이 바뀌면 어노테이션 선언을 별도로 하여 방어한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class CommonCodeGroupResponse {

    /** 항목 조회 API의 쿼리 파라미터로 쓰이는 내부 식별자 */
    private String groupId;

    /** 코드그룹ID (예: DEPT_CD) — 이 서비스가 검증에 쓰는 키 */
    private String groupCode;

    /** 사용여부 "Y" / "N" */
    private String useYn;
}
