package kr.co.seoulit.his.labimagingservice.businessdelegate.admin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * admin-service의 공통코드 항목 1건.
 *
 * 필드명은 admin 서비스(HisBack)의 CommonCodeItem DTO 및 프론트
 * features/commonCode/types/commonCodeItemTypes.ts 와 맞춘 것이다.
 *   { codeId, groupId, codeValue, codeName, useYn }
 *
 * 검증에 실제로 쓰는 건 codeValue와 useYn 두 개뿐이라 나머지는 매핑하지 않는다.
 * (codeName은 표시용이고, 이 서비스는 타 서비스 소유 표시명을 저장하지 않는다 — 개발표준가이드 14.1)
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CommonCodeItemResponse {

    /** 코드값 (예: DEPT_CD 그룹의 "D001") */
    private String codeValue;

    /** 사용여부 "Y" / "N" */
    private String useYn;
}
