package kr.co.seoulit.his.labimagingservice.laborder.dto;

import kr.co.seoulit.his.labimagingservice.common.YnValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 검사 오더 접수 요청
 * API: POST /lab-orders
 * 대응 유스케이스: UC-SPC-01 검사오더접수 (Jira ZP2-12)
 * 처리 시 LAB_ORDER + LAB_ORDER_ITEM + LAB_RECEPTION 를 한 트랜잭션에서 생성한다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "검사 오더 접수 요청")
public class LabOrderCreateRequestDto {

    // MSA 간 참조 식별자를 VARCHAR2(36)으로 통일 (2026-08-25). 처방코어의 prescriptionId 가 최대 36자.
    @NotBlank
    @Size(max = 36)
    @Schema(description = "외부시스템 오더 원본 번호 (LAB_ORDER.lab_order_no, UNIQUE)", example = "EXT-LO-20260715-001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String labOrderNo;

    /**
     * 연계시스템코드 (공통코드 SYSTEM_SOURCE_CD — WARD / ER / OP)
     *
     * ⚠ 2026-07-16 의 Open Question 은 해소됐다. (2026-08-26)
     *   처방코어 payload 가 확정됐고, 코어는 채널 값을 보내지 않는다.
     *   그래서 연계 수신(LabOrderIntakeService)이 "OP"(외래) 로 고정해서 채운다.
     *   코어가 outpatient-service 안에 있어 지금은 채널이 외래 하나이기 때문이다.
     *
     * ⚠ 코어가 encounterType(OPD|ER|IP) 을 보내기 시작하면 그때 매핑으로 바꾼다.
     *   이 필드를 없앨 필요는 없다. 프론트 수동 등록 폼은 계속 채널을 직접 고른다.
     */
    @NotBlank
    @Size(max = 10)
    @Schema(description = "연계시스템코드 (공통코드 SYSTEM_SOURCE_CD)", example = "OP", requiredMode = Schema.RequiredMode.REQUIRED)
    private String systemCode;

    @NotBlank
    @Size(max = 36)
    @Schema(description = "환자ID (patient-service 내부 식별자, 참조/검증용)", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f", requiredMode = Schema.RequiredMode.REQUIRED)
    private String patientId;

    @Size(max = 20)
    @Schema(description = "처방의번호 (화면 표시용 업무번호, NULL 허용)", example = "D0032")
    private String physicianNo;

    @Size(max = 36)
    @Schema(description = "처방의ID (참조용, NULL 허용)", example = "d0a1b2c3-4d5e-6f70-8192-a3b4c5d6e7f8")
    private String physicianId;

    @NotBlank
    @Size(max = 10)
    @Schema(description = "진료구분코드", example = "OUTPATIENT", requiredMode = Schema.RequiredMode.REQUIRED)
    private String treatTypeCode;

    @NotNull
    @YnValue
    @Schema(description = "응급여부 (Y/N)", example = "N", requiredMode = Schema.RequiredMode.REQUIRED)
    private String urgencyYn;

    @NotBlank
    @Size(max = 20)
    @Schema(description = "접수담당자ID", example = "staff-uuid-001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String receivedById;

    @NotEmpty
    @Valid
    @Schema(description = "검사항목 목록 (최소 1건)", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<LabOrderItemRequestDto> orderItems;
}
