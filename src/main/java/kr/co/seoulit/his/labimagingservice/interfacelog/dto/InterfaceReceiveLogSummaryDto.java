package kr.co.seoulit.his.labimagingservice.interfacelog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.co.seoulit.his.labimagingservice.interfacelog.entity.InterfaceOrderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 연계 수신 이력 응답
 * API: GET /api/lab-imaging/interface-logs
 *
 * ⚠ Kafka 는 백엔드끼리의 통신이라 화면에는 아무것도 보이지 않는다.
 *   워크리스트에 오더가 나타나도 그게 REST 로 온 건지 Kafka 로 온 건지 구분할 수 없다.
 *   이 조회가 "방금 Kafka 로 들어왔다"를 눈으로 확인하는 유일한 창구다.
 *
 * ⚠ eventId 가 채워져 있으면 Kafka 수신 건, 비어 있으면 REST 수신 건이다.
 *   두 경로를 가르는 기준이 이 필드 하나뿐이라 반드시 응답에 넣는다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "연계 수신 이력")
public class InterfaceReceiveLogSummaryDto {

    @Schema(description = "수신로그ID", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f")
    private String interfaceReceiveLogId;

    @Schema(description = "Kafka 이벤트ID. 값이 있으면 Kafka 수신, 없으면 REST 수신",
            example = "b3f1c2a0-1111-2222-3333-444455556666")
    private String eventId;

    @Schema(description = "수신 대상 구분 (LAB/IMG)", example = "LAB")
    private InterfaceOrderType orderTypeCode;

    @Schema(description = "수신 출처 (공통코드 SYSTEM_SOURCE_CD)", example = "OP")
    private String systemCode;

    @Schema(description = "처리 결과 코드. RECEIVED 는 아직 처리 전", example = "LAB001")
    private String resultCode;

    @Schema(description = "실패 사유 (성공이면 null)")
    private String errorMessage;

    @Schema(description = "수신 일시", example = "2026-08-27T10:15:00")
    private LocalDateTime receivedAt;

    /**
     * 수신 원문(JSON).
     * ⚠ 시연에서 "이 payload 가 그대로 들어왔다"를 보여주는 값이라 반드시 포함한다.
     */
    @Schema(description = "수신 원문 JSON")
    private String rawMessage;
}
