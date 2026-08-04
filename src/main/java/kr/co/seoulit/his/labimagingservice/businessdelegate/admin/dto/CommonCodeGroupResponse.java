package kr.co.seoulit.his.labimagingservice.businessdelegate.admin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * admin-service의 공통코드 그룹 1건.
 *
 * 필드명은 admin 서비스(HisBack)의 CommonCodeGroup DTO 및 프론트
 * features/commonCode/types/commonCodeGroupTypes.ts 와 맞춘 것이다.
 *   { groupId, groupCode, groupName, useYn }
 *
 * 항목 조회 API가 groupCode가 아니라 groupId를 받기 때문에,
 * "groupCode → groupId" 변환을 위해 그룹 목록을 먼저 읽어야 한다.
 * groupName은 표시용이라 매핑하지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CommonCodeGroupResponse {

    /** 항목 조회 API의 쿼리 파라미터로 쓰이는 내부 식별자 */
    private Long groupId;

    /** 코드그룹ID (예: DEPT_CD) — 이 서비스가 검증에 쓰는 키 */
    private String groupCode;

    /** 사용여부 "Y" / "N" */
    private String useYn;
}
