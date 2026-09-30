package kr.co.seoulit.his.labimagingservice.labspecimen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 접수 1건에 허용되는 검체종류·검체용기 조합 1건. 6차 (2026-09-30)
 * 대응: LAB_TEST_SPECIMEN_RULE — 접수에 속한 오더의 검사항목들이 허용하는 규칙의 합집합.
 *
 * ⚠ 화면이 검체종류 → 검체용기로 연쇄 선택하는 데 쓴다. 같은 검체종류에 용기가 여럿이면
 *   그 개수만큼 행이 내려간다(예: BLOOD-05, BLOOD-03 처럼 검체종류가 중복될 수 있다).
 * ⚠ equals/hashCode 를 값 기준으로 둔다. 서로 다른 검사항목이 같은 조합을 허용하면 규칙 행이
 *   중복될 수 있어, 서비스가 stream().distinct() 로 조합 단위 중복을 제거한다(SpecimenService 참고).
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@Schema(description = "허용 검체종류·검체용기 조합")
public class SpecimenRuleDto {

    @Schema(description = "검체종류 (SpecimenType enum)", example = "BLOOD")
    private String specimenType;

    @Schema(description = "검체용기코드 (공통코드 SPECIMEN_CONTAINER_CD)", example = "05")
    private String specimenContainerCode;

    @Schema(description = "화면 기본 선택 여부 (Y/N)", example = "Y")
    private String defaultYn;
}
