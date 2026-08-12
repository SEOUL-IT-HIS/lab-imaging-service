package kr.co.seoulit.his.labimagingservice.imagingorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 영상 접수 상세 응답
 * API: GET /image-orders/receptions/{receptionNo}
 *
 * (목록용 ImageOrderSummaryDto 와 분리한 이유는 LabReceptionDetailDto 주석 참고 — N+1 회피)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "영상 접수 상세 응답")
public class ImageReceptionDetailDto {

    @Schema(description = "접수ID (화면 표시용 아님 — 일정 화면 이동에 사용)", example = "9c8b7a6f-1234-4e5f-9a0b-1c2d3e4f5a6b")
    private String imageReceptionId;

    @Schema(description = "접수번호", example = "IR-A1B2C3D4")
    private String receptionNo;

    @Schema(description = "오더번호", example = "EXT-IO-20260715-001")
    private String imageOrderNo;

    @Schema(description = "진료구분코드 (공통코드 RCPT_TYPE_CD)", example = "01")
    private String treatTypeCode;

    @Schema(description = "응급여부 (Y/N)", example = "N")
    private String urgencyYn;

    @Schema(description = "환자번호", example = "P00012345")
    private String patientNo;

    @Schema(description = "처방의번호", example = "D0032")
    private String physicianNo;

    @Schema(description = "촬영항목코드 목록 (공통코드 IMG_ITEM_CD)", example = "[\"01\",\"02\"]")
    private List<String> imageItemCodes;

    @Schema(description = "오더 수신일시", example = "2026-08-10T14:20:00")
    private LocalDateTime receivedAt;

    @Schema(description = "촬영 예정일시 (일정 미등록이면 null)", example = "2026-08-20T09:30:00")
    private LocalDateTime scheduledAt;

    @Schema(description = "오더상태코드 (서비스 내부 Enum OrderStatus)", example = "RECEIVED")
    private String orderStatusCode;

    @Schema(description = "접수상태코드 (서비스 내부 Enum ReceptionStatus)", example = "ACCEPTED")
    private String receptionStatusCode;

    @Schema(description = "접수담당자ID", example = "STF00021")
    private String receivedById;
}
