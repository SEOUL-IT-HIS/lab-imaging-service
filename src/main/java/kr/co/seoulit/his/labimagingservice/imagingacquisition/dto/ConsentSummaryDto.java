package kr.co.seoulit.his.labimagingservice.imagingacquisition.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 동의서 요약 (목록/단건 공용 응답)
 * 대응 유스케이스: UC-IMG-05 (Jira ZP2-80 검사 진행 전 동의 상태 확인 및 조회)
 *
 * TODO: 필드 추가 — consentId, imageOrderId, patientNo, consentTypeCode, consentYn,
 *       consentDt, signedByName, withdrawnYn, withdrawnAt, withdrawnReasonCode
 */
// TODO: 필드를 추가할 때 @Builder & @AllArgsConstructor 를 함께 되살릴 것.
//       (필드가 없는 상태에서는 @NoArgsConstructor 와 생성자 시그니처가 겹쳐 컴파일 실패)
@Getter
@NoArgsConstructor
@Schema(description = "동의서 요약")
public class ConsentSummaryDto {
}
