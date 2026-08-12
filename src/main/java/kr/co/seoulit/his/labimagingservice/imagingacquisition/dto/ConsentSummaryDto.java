package kr.co.seoulit.his.labimagingservice.imagingacquisition.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 동의서 요약 (목록/단건 공용 응답)
 * 대응 유스케이스: UC-IMG-05 (Jira ZP2-80 검사 진행 전 동의 상태 확인 및 조회)
 *
 * ⚠ patientId 는 참조/검증용이라 화면 응답에 넣지 않는다. (LabOrderSummaryDto 와 동일 기준)
 * ⚠ documentTemplateId 도 제외했다. 화면에서 양식을 다시 열어야 하면 그때 추가할 것.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "동의서 응답 요약")
public class ConsentSummaryDto {

    @Schema(description = "동의서ID", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f")
    private String consentId;

    @Schema(description = "영상오더ID", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f")
    private String imageOrderId;

    @Schema(description = "환자번호 (화면 표시용 업무번호)", example = "P00012345")
    private String patientNo;

    @Schema(description = "동의서유형코드", example = "조영제사용")
    private String consentTypeCode;

    @Schema(description = "동의여부 (Y/N)", example = "Y")
    private String consentYn;

    @Schema(description = "동의일자", example = "2026-07-25")
    private LocalDate consentDt;

    @Schema(description = "서명자명", example = "홍길동")
    private String signedByName;

    @Schema(description = "확인자ID", example = "STF00021")
    private String witnessId;

    @Schema(description = "철회여부 (Y/N)", example = "N")
    private String withdrawnYn;

    @Schema(description = "철회일시 (철회 전이면 null)", example = "2026-07-26T14:00:00")
    private LocalDateTime withdrawnAt;

    @Schema(description = "철회사유코드 (철회 전이면 null)", example = "환자거부")
    private String withdrawnReasonCode;
}
