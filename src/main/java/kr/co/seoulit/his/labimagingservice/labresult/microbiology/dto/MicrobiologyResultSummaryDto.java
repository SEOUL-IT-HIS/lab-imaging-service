package kr.co.seoulit.his.labimagingservice.labresult.microbiology.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 미생물 결과 응답. UC-RST-02
 *
 * ⚠ labOrderItemId / labItemCode 는 테이블에 없는 값이다. 결과는 검체에 붙지만,
 *   "접수당 미생물 항목 1개" 제약으로 서비스가 해당 항목을 찾아 채운다.
 *   화면(어느 항목의 결과인지)·청구·결과전송이 항목 단위로 움직이기 때문에 내려준다.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "미생물 결과")
public class MicrobiologyResultSummaryDto {

    private String microbiologyResultId;
    private String specimenId;
    private String specimenBarcode;
    private String receptionNo;

    @Schema(description = "이 결과가 대응하는 검사항목ID (접수의 미생물 항목)")
    private String labOrderItemId;
    @Schema(description = "검사항목코드 (TEST_TYPE_CD)")
    private String labItemCode;

    private String cultureStatusCode;
    private String organismCode;
    private String causativeYn;
    private String observationNote;

    @Schema(description = "결과상태 (01 등록=중간보고 / 02 확정=최종보고)")
    private String resultStatusCode;
    private LocalDateTime recordedAt;
    private String recordedById;
    private LocalDateTime confirmedAt;
    private String confirmedById;
    /** 수정 시각 — 중간보고가 언제 갱신됐는지 화면에 보여준다(D4, 이력 테이블 없음) */
    private LocalDateTime updatedAt;

    private List<MicrobiologySusceptibilityDto> susceptibilities;
}
