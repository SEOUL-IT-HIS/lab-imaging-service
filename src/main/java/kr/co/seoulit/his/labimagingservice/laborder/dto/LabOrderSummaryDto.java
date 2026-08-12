package kr.co.seoulit.his.labimagingservice.laborder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 검사 오더 접수 응답
 * API: POST /lab-orders
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "검사 오더 접수 응답")
public class LabOrderSummaryDto {

    @Schema(description = "오더ID", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f")
    private String labOrderId;

    @Schema(description = "오더번호", example = "LO-2026-000123")
    private String labOrderNo;

    @Schema(description = "환자번호", example = "PA-2026-000456")
    private String patientNo;

    @Schema(description = "오더상태코드", example = "RECEIVED")
    private String orderStatusCode;

    @Schema(description = "접수ID (자동 생성된 LAB_RECEPTION)", example = "9c8b7a6f-1234-4e5f-9a0b-1c2d3e4f5a6b")
    private String labReceptionId;

    @Schema(description = "접수번호", example = "LR-2026-000123")
    private String receptionNo;

    @Schema(description = "접수상태코드", example = "ACCEPTED")
    private String receptionStatusCode;

    /**
     * 최종(latest_yn='Y') 일정의 검사 예정일시. 일정 미등록 접수면 null.
     * 목록에서 "언제로 잡혔는지"를 보여주고, 등록/재등록 버튼을 가르는 기준으로도 쓴다.
     */
    @Schema(description = "검사 예정일시 (일정 미등록이면 null)", example = "2026-08-20T09:30:00")
    private LocalDateTime scheduledAt;
}
