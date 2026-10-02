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
 * 검증(CommonCodeCache)에는 codeValue와 useYn 두 개만 쓴다. codeName은 원래 표시용이라
 * 매핑하지 않았었는데(이 서비스는 타 서비스 소유 표시명을 저장하지 않는다 — 개발표준가이드 14.1),
 * 2026-10-02(검사항목 카탈로그 검색, LabItemCatalogService)부터 codeName도 매핑한다.
 *
 * ⚠ 이건 "저장"이 아니다 — 그 즉시 응답으로 돌려주기만 하고 우리 DB 어디에도 남기지 않는다.
 *   요청마다 admin에 새로 물어보기 때문에 admin이 이름을 바꾸면 다음 요청부터 바로 반영된다.
 *   스냅샷 금지 원칙이 막는 건 "복사해서 오래 들고 있는 것"이지, 매 응답을 그대로 돌려주는 건 아니다.
 *
 * ⚠ 매핑하지 않은 codeId/groupId/parentCodeId/sortOrder 는 Spring Boot의 Jackson
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

    /** 코드명 (표시용) — LabItemCatalogService 처럼 명시적으로 필요한 곳에서만 쓴다 */
    private String codeName;

    /** 사용여부 "Y" / "N" */
    private String useYn;
}
