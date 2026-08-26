package kr.co.seoulit.his.labimagingservice.laborder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 검사 접수 상세 응답
 * API: GET /lab-orders/receptions/{receptionNo}
 *
 * ⚠ 목록용 LabOrderSummaryDto 와 일부러 분리했다.
 *   상세에는 검사항목(labItemCodes)이 필요한데, LAB_ORDER_ITEM 은 @OneToMany(LAZY) 라
 *   목록 응답에 넣으면 행마다 지연로딩이 일어난다(N+1). 단건 조회에서만 담는다.
 *
 * ⚠ 화면에 안 보이는 labReceptionId 를 담는 이유: 일정 등록/재등록 화면으로 이동할 때
 *   경로변수로 쓴다. 사용자에게 노출하지 않을 뿐 데이터로는 필요하다.
 *
 * ⚠ systemCode 는 담지 않는다. 접수 담당자에게 의미 없는 연계 식별자라 화면에서 뺐다.
 *   (연계 문제 추적이 필요해지면 그때 추가)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "검사 접수 상세 응답")
public class LabReceptionDetailDto {

    @Schema(description = "접수ID (화면 표시용 아님 — 일정 화면 이동에 사용)", example = "9c8b7a6f-1234-4e5f-9a0b-1c2d3e4f5a6b")
    private String labReceptionId;

    @Schema(description = "접수번호", example = "LR-A1B2C3D4")
    private String receptionNo;

    @Schema(description = "오더번호", example = "EXT-LO-20260715-001")
    private String labOrderNo;

    @Schema(description = "진료구분코드 (공통코드 RCPT_TYPE_CD)", example = "01")
    private String treatTypeCode;

    @Schema(description = "응급여부 (Y/N)", example = "N")
    private String urgencyYn;

    /**
     * 환자ID (patient-service 내부 식별자).
     * ⚠ 화면에 그대로 찍는 값이 아니라, 환자명을 조회하는 열쇠다.
     *   환자번호(patient_no)는 화면·DTO 에서 모두 뺐다. (2026-08-25 결정)
     *   전체 MSA 에서 환자번호를 어떻게 쓸지 정해지기 전까지 쓰지 않기로 했고,
     *   발급 주체도 아직 없다. 컬럼은 남아 있으니 결정되면 다시 열면 된다.
     *   그동안 화면에서 환자를 알아보는 값은 환자명이며, patientId 로 조회한다.
     */
    @Schema(description = "환자ID (환자명 조회용)", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f")
    private String patientId;

    @Schema(description = "처방의번호", example = "D0032")
    private String physicianNo;

    @Schema(description = "검사항목코드 목록 (공통코드 TEST_TYPE_CD)", example = "[\"01\",\"02\"]")
    private List<String> labItemCodes;

    @Schema(description = "오더 수신일시", example = "2026-08-10T14:20:00")
    private LocalDateTime receivedAt;

    @Schema(description = "검사 예정일시 (일정 미등록이면 null)", example = "2026-08-20T09:30:00")
    private LocalDateTime scheduledAt;

    @Schema(description = "오더상태코드 (서비스 내부 Enum OrderStatus)", example = "RECEIVED")
    private String orderStatusCode;

    @Schema(description = "접수상태코드 (서비스 내부 Enum ReceptionStatus)", example = "ACCEPTED")
    private String receptionStatusCode;

    @Schema(description = "접수담당자ID", example = "STF00021")
    private String receivedById;
}
