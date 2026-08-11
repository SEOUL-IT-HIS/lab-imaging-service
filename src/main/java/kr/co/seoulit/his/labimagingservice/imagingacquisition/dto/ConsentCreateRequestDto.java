package kr.co.seoulit.his.labimagingservice.imagingacquisition.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 조영제/침습검사 동의 등록 요청
 * API: POST /api/lab-imaging/consents
 * 대응 유스케이스: UC-IMG-05 (Jira ZP2-84 동의 여부 등록 및 변경, ZP2-83 필수값·유효성 검증)
 *
 * TODO: 필드 추가 — imageOrderId, patientNo, patientId, consentTypeCode,
 *       documentTemplateId, consentYn(@YnValue), consentDt, signedByName, witnessId
 *       (@NotBlank/@Size 는 CONSENT 테이블 제약과 맞출 것)
 */
// TODO: 필드를 추가할 때 @Builder & @AllArgsConstructor 를 함께 되살릴 것.
//       (필드가 없는 상태에서는 @NoArgsConstructor 와 생성자 시그니처가 겹쳐 컴파일 실패)
@Getter
@NoArgsConstructor
@Schema(description = "조영제/침습검사 동의 등록 요청")
public class ConsentCreateRequestDto {
}
