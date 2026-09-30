package kr.co.seoulit.his.labimagingservice.interfacelog.send.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 발신 이력 1건 (목록·상세 공용). ZP2-120 전송이력 조회 / ZP2-124 청구 발행 이력
 *
 * ⚠ payload 는 상세 조회(GET /{id})에서만 채운다. 목록에 원문까지 싣지 않는다.
 * ⚠ receptionRef / itemCode 는 테이블에 없는 "표시용" 값이다. payload 에서 꺼낸다 —
 *   결과전송은 receptionNo·testTypeCode, 청구는 receptionId·itemName(검사항목코드). 원문이 없으면 null.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "발신 이력")
public class InterfaceSendLogDto {

    private String interfaceSendLogId;
    @Schema(description = "이벤트유형 (SEND_EVENT_TYPE_CD: 01 검사결과 / 02 청구)")
    private String eventTypeCode;
    @Schema(description = "발행 이벤트ID — 재발행해도 같다(수신측 멱등키)")
    private String eventId;
    @Schema(description = "원본ID (결과ID / 검사항목ID / 영상촬영항목ID)")
    private String referenceId;
    @Schema(description = "수신처 (SYSTEM_SOURCE_CD)")
    private String systemCode;
    @Schema(description = "상태 (TRANSMIT_STATUS_CD: 01 대기 / 02 완료 / 03 실패)")
    private String sendStatusCode;
    private Integer retryCount;
    private String errorMessage;
    @Schema(description = "마지막 발행(시도) 일시")
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;

    @Schema(description = "표시용 — 접수번호(결과) 또는 접수ID(청구)")
    private String receptionRef;
    @Schema(description = "표시용 — 검사항목코드/영상항목코드")
    private String itemCode;

    @Schema(description = "원문 JSON (상세 조회에서만)")
    private String payload;
}
