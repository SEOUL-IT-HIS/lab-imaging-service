package kr.co.seoulit.his.labimagingservice.laborder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 검사 워크리스트 1행.
 * GET /api/lab-imaging/lab-orders/worklist
 *
 * ⚠ 목록의 행 단위는 "접수" 하나로 고정한다.
 *   검체는 접수 1건에 여러 건 달릴 수 있는데(1:N), 검체를 행으로 삼으면 단계에 따라
 *   같은 접수가 여러 줄로 쪼개져 표가 통째로 바뀐다. 검체 단위 작업은 목록이 아니라
 *   오른쪽 작업 폼 안에서 처리한다.
 *
 * ⚠ 진행 상태를 Y/N 이 아니라 개수(specimenCount / judgedCount)로 내려준다.
 *   검체 3건 중 2건만 판정된 상태를 Y/N 으로는 표현할 수 없다. "판정 완료"라고 하면
 *   남은 1건이 묻히고, "미판정"이라고 하면 끝낸 2건이 묻힌다. 화면에 "판정 2/3" 으로 보이려면
 *   개수가 있어야 한다.
 *
 * ⚠ LabOrderSummaryDto 를 재사용하지 않고 목록 전용 DTO 를 따로 둔다.
 *   진행 상태 필드들은 워크리스트에서만 쓰는데, 기존 DTO 에 얹으면 접수 조회 API 를 쓰는
 *   다른 화면들도 쓰지 않는 값을 함께 받게 된다.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "검사 워크리스트 항목")
public class LabWorklistItemDto {

    @Schema(description = "접수ID (화면 표시용 아님 — 일정 등록 등 하위 작업 호출에 사용)",
            example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f")
    private String labReceptionId;

    @Schema(description = "접수번호", example = "LR-A1B2C3D4")
    private String receptionNo;

    @Schema(description = "오더번호", example = "GR2-20260814-001")
    private String labOrderNo;

    /**
     * 하위 작업(검체 등록 등)의 요청 본문에 담고, 화면의 환자명 조회에도 쓴다.
     *
     * ⚠ 환자번호(patient_no)는 화면·DTO 에서 모두 뺐다. (2026-08-25 결정)
     *   전체 MSA 에서 환자번호를 어떻게 쓸지 정해지기 전까지 쓰지 않기로 했고,
     *   발급 주체도 아직 없다. 컬럼은 남아 있으니 결정되면 다시 열면 된다.
     *   그동안 화면에서 환자를 알아보는 값은 환자명이며, patientId 로 조회한다.
     */
    @Schema(description = "환자ID (patient-service 내부 식별자)",
            example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f")
    private String patientId;

    @Schema(description = "응급여부 (Y/N)", example = "N")
    private String urgencyYn;

    @Schema(description = "접수일시 — 목록 정렬 기준(오래된 건이 위)", example = "2026-08-14T08:40:00")
    private LocalDateTime receivedAt;

    // ---- 진행 상태 ----

    @Schema(description = "검사 예정일시. 일정 미등록이면 null", example = "2026-08-20T09:30:00")
    private LocalDateTime scheduledAt;

    @Schema(description = "등록된 검체 수", example = "3")
    private int specimenCount;

    @Schema(description = "적합성 판정이 끝난 검체 수", example = "2")
    private int judgedCount;

    @Schema(description = "재채취 요청이 있는지 (Y/N)", example = "N")
    private String recollectionRequestedYn;

    @Schema(description = "다음에 해야 할 일 — 서버가 계산한다", example = "ACCEPTANCE")
    private WorklistStep nextStep;

    // ---- 제외 상태 ----

    @Schema(description = "접수상태코드 (ACCEPTED=처리 대상, EXCLUDED=제외됨)", example = "ACCEPTED")
    private String receptionStatusCode;

    @Schema(description = "제외 사유. 제외된 건에만 값이 있다", example = "환자 미방문으로 검사 취소")
    private String exclusionReason;

    @Schema(description = "제외 일시. 제외된 건에만 값이 있다", example = "2026-08-14T11:20:00")
    private LocalDateTime excludedAt;
}
