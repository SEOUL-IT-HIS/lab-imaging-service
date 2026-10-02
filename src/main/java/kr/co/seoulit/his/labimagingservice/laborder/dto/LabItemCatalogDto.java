package kr.co.seoulit.his.labimagingservice.laborder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 검사항목 카탈로그 1건 — 처방 화면의 "검사 항목 고르기" 용도. (처방코어 요청, 2026-10-02)
 *
 * ⚠ 별도 마스터 테이블이 없다. 검사 가능한 항목의 전체 목록 자체가 admin 공통코드 TEST_TYPE_CD다
 *   (LabOrderItemRequestDto.labItemCode 검증에 쓰는 그 그룹과 같다). 이 DTO는 그 코드 목록에
 *   itemName(admin에서 즉시 조회, 저장 안 함)과 이 서비스가 이미 알고 있는 참고 정보
 *   (검사 분류·허용 검체종류)를 덧붙여 돌려준다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "검사항목 카탈로그 1건 (처방 화면 검색용)")
public class LabItemCatalogDto {

    @Schema(description = "검사항목코드 (공통코드 TEST_TYPE_CD) — 처방 itemCode 로 그대로 매핑해 보내면 된다",
            example = "02")
    private String itemCode;

    @Schema(description = "검사명 — admin 공통코드 이름을 그대로 돌려준다(저장하지 않고 매 요청마다 새로 조회)",
            example = "CBC")
    private String itemName;

    @Schema(description = "검사 분류 — GENERAL(일반) / MICROBIOLOGY(미생물) / PATHOLOGY(병리)",
            example = "GENERAL")
    private String testClassification;

    /**
     * 허용 검체종류(SpecimenType enum 이름, BLOOD/URINE/TISSUE/FLUID) 목록. 참고용이다.
     * ⚠ 빈 배열이면 "이 검사에 검체 규칙이 없다"는 뜻이다(검체 제한 없음) — "검체가 필요 없다"는 뜻이 아니다.
     */
    @Schema(description = "허용 검체종류 목록. 규칙이 없으면 빈 배열(검체 제한이 없다는 뜻)")
    private List<String> specimenTypes;
}
