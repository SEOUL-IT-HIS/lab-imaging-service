package kr.co.seoulit.his.labimagingservice.labspecimen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.FitnessStatus;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 검체 인수/적합성 판정 응답
 * 대응 유스케이스: UC-SPC-04 검체적합성판정
 *
 * ⚠ 판정 결과만 담으면 "무슨 검체를, 누구 것을" 판정했는지 알 수 없어서
 *   검체 식별 정보(바코드·종류·환자번호·접수번호)를 함께 담는다.
 *   사람이 검체를 식별하는 값은 UUID 가 아니라 바코드다.
 *
 * ⚠ specimenAcceptanceId(판정 PK)는 담지 않는다. 화면에 쓸 일도, 이동에 쓸 일도 없다.
 *   판정 건은 검체 1건에 1건뿐이라 specimenId 로 충분히 지목된다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "검체 인수/적합성 판정 응답")
public class SpecimenAcceptanceSummaryDto {

    @Schema(description = "검체ID (화면 표시용 아님 — 검체 화면 이동에 사용)", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f")
    private String specimenId;

    @Schema(description = "접수번호", example = "LR-A1B2C3D4")
    private String receptionNo;

    @Schema(description = "검체바코드", example = "SP-A1B2C3D4")
    private String specimenBarcode;

    @Schema(description = "검체종류", example = "BLOOD")
    private SpecimenType specimenType;

    @Schema(description = "환자번호", example = "P00012345")
    private String patientNo;

    @Schema(description = "검체 인수일시", example = "2026-07-25T09:30:00")
    private LocalDateTime acceptedAt;

    @Schema(description = "검체인수자ID", example = "STF00021")
    private String acceptedById;

    @Schema(description = "적합상태", example = "FIT")
    private FitnessStatus fitnessStatus;

    @Schema(description = "부적합사유코드 (공통코드 SPECIMEN_REJECT_CD, 적합이면 null)", example = "01")
    private String unfitReasonCode;

    @Schema(description = "재채취요청여부 (Y/N)", example = "N")
    private String recollectionRequestedYn;
}
