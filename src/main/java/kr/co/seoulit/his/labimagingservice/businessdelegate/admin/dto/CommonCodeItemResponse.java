package kr.co.seoulit.his.labimagingservice.businessdelegate.admin.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * admin-service의 공통코드 항목 1건.
 *
 * 필드명은 admin 서비스의 프론트
 * features/commonCode/types/commonCodeItemTypes.ts 와 맞춘 것이다.
 *   { codeId, groupId, codeValue, codeName, useYn }
 *
 * 검증에 실제로 쓰는 건 codeValue와 useYn 두 개뿐이라 나머지는 매핑하지 않는다.
 * (codeName은 표시용이고, 이 서비스는 타 서비스 소유 표시명을 저장하지 않는다 — 개발표준가이드 14.1)
 *
 * ⚠ 매핑하지 않은 codeId/codeName/groupId/parentCodeId/sortOrder 는 Spring Boot의 Jackson
 *   기본 설정(FAIL_ON_UNKNOWN_PROPERTIES 비활성)이 조용히 무시한다.
 *   상세는 ExternalApiResponse 주석 참고.
 *    @JsonIgnoreProperties 전역설정이 바뀌면 어노테이션 선언을 별도로 하여 방어한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class CommonCodeItemResponse {

    /** 코드값 (예: DEPT_CD 그룹의 "D001") */
    private String codeValue;

    /** 사용여부 "Y" / "N" */
    private String useYn;
}
