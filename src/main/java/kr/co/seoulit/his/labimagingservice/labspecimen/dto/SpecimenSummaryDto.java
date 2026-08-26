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
 * 검체 요약 (목록/단건 공용 응답)
 * 대응 유스케이스: UC-SPC-03 검체식별관리 / UC-SPC-04 검체적합성판정(ZP2-79 검체 목록 조회)
 *
 * ⚠ 접수는 UUID(labReceptionId)가 아니라 사람이 읽는 접수번호(receptionNo)로 담는다.
 *   화면에서 "이 검체가 어느 접수 건인지"를 UUID로는 알 수 없다.
 *
 * ⚠ specimenId 는 화면에 표시하지 않지만 담는다. 판정 화면으로 이동할 때 경로변수로 쓴다.
 *   (LabReceptionDetailDto.labReceptionId 와 같은 이유)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "검체 응답 요약")
public class SpecimenSummaryDto {

    @Schema(description = "검체ID (화면 표시용 아님 — 판정 화면 이동에 사용)", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f")
    private String specimenId;

    @Schema(description = "접수번호", example = "LR-A1B2C3D4")
    private String receptionNo;

    @Schema(description = "검체바코드", example = "SP-A1B2C3D4")
    private String specimenBarcode;

    @Schema(description = "검체종류", example = "BLOOD")
    private SpecimenType specimenType;

    @Schema(description = "검체용기코드 (공통코드 SPECIMEN_CONTAINER_CD)", example = "01")
    private String specimenContainerCode;

    @Schema(description = "검체채취일시", example = "2026-07-25T09:30:00")
    private LocalDateTime collectedAt;

    @Schema(description = "검체채취자ID", example = "STF00021")
    private String collectedById;

    /**
     * 적합성 판정 결과. 아직 판정하지 않은 검체는 null.
     * 목록에서 "판정됐는지 / 어떤 결과인지"를 보여주고, 판정/재판정 버튼을 가르는 기준으로도 쓴다.
     * (접수 목록의 scheduledAt 과 같은 역할)
     */
    @Schema(description = "적합상태 (미판정이면 null)", example = "FIT")
    private FitnessStatus fitnessStatus;
}
