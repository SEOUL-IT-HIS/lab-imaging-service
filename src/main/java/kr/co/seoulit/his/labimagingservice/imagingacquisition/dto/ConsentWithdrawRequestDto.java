package kr.co.seoulit.his.labimagingservice.imagingacquisition.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 동의 철회 요청
 * 대응 유스케이스: UC-IMG-05 (Jira ZP2-84 동의 여부 등록 및 "변경")
 *
 * TODO: 필드 추가 — withdrawnReasonCode, withdrawnAt
 *       (withdrawnYn 은 요청으로 받지 않고 서버가 'Y'로 설정하는 것을 권장)
 *
 * ⚠ 철회는 기존 행을 UPDATE 할지, 이력 보존을 위해 신규 행을 INSERT 할지 결정 필요.
 *   ERD 상 CONSENT 는 "오더 건에 대해 동의 등록(및 재동의) 이력 발생 가능"으로 1:N 이고,
 *   withdrawn_* 컬럼이 같은 행에 있는 걸 보면 UPDATE 방식으로 설계된 것으로 보인다.
 *   일정(LAB_SCHEDULE)의 latest_yn 방식과 다르므로 구현 전에 확인할 것.
 */
// TODO: 필드를 추가할 때 @Builder & @AllArgsConstructor 를 함께 되살릴 것.
//       (필드가 없는 상태에서는 @NoArgsConstructor 와 생성자 시그니처가 겹쳐 컴파일 실패)
@Getter
@NoArgsConstructor
@Schema(description = "동의 철회 요청")
public class ConsentWithdrawRequestDto {
}
