package kr.co.seoulit.his.labimagingservice.imagingorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 영상 워크리스트 1행.
 * GET /api/lab-imaging/image-orders/worklist
 *
 * ⚠ 목록의 행 단위는 "접수" 하나로 고정한다.
 *   촬영항목·동의·영상파일은 접수 1건에 여러 건 달릴 수 있는데(1:N), 그걸 행으로 삼으면
 *   같은 접수가 여러 줄로 쪼개져 표가 통째로 바뀐다. 하위 작업은 오른쪽 작업 폼에서 처리한다.
 *   (검사 LabWorklistItemDto 와 같은 원칙)
 *
 * ⚠ 진행 상태를 검사와 다르게 담는다.
 *   검사는 검체가 1:N 이라 "3건 중 2건 판정"을 표현하려고 개수(specimenCount/judgedCount)를 쓴다.
 *   영상의 동의는 개수가 아니라 Y/N 이 맞다. 유효한 동의가 하나라도 있으면 촬영 가능이고,
 *   "3건 중 2건 동의" 같은 중간 상태가 업무상 존재하지 않는다.
 *
 * ⚠ ImageOrderSummaryDto 를 재사용하지 않고 목록 전용 DTO 를 따로 둔다.
 *   진행 상태 필드들은 워크리스트에서만 쓰는데, 기존 DTO 에 얹으면 접수 조회 API 를 쓰는
 *   다른 화면들도 쓰지 않는 값을 함께 받게 된다.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "영상 워크리스트 항목")
public class ImageWorklistItemDto {

    @Schema(description = "접수ID (화면 표시용 아님 — 일정 등록 등 하위 작업 호출에 사용)",
            example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f")
    private String imageReceptionId;

    @Schema(description = "접수번호", example = "IR-A1B2C3D4")
    private String receptionNo;

    @Schema(description = "오더번호", example = "EXT-IO-20260715-001")
    private String imageOrderNo;

    /**
     * ⚠ 화면에 표시하는 값이 아니라 동의 작업이 쓰는 열쇠다.
     *   CONSENT 는 접수가 아니라 오더에 붙어서(CONSENT.image_order_id),
     *   동의 등록·조회가 이 값을 요청에 담는다. 없으면 워크리스트에서 동의 탭이 동작하지 않는다.
     */
    @Schema(description = "오더ID (화면 표시용 아님 — 동의 등록/조회에 사용)",
            example = "9c8b7a6f-1234-4e5f-9a0b-1c2d3e4f5a6b")
    private String imageOrderId;

    /**
     * ⚠ 동의 등록 등 하위 작업 요청에 담고, 화면의 환자명 조회에도 쓴다.
     *   환자번호(patient_no)는 화면·DTO 에서 뺐다. (2026-08-25 결정 — 검사 쪽과 동일)
     */
    @Schema(description = "환자ID (patient-service 내부 식별자)",
            example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f")
    private String patientId;

    @Schema(description = "응급여부 (Y/N)", example = "N")
    private String urgencyYn;

    @Schema(description = "접수일시 — 목록 정렬 기준(오래된 건이 위)", example = "2026-09-02T08:40:00")
    private LocalDateTime receivedAt;

    // ---- 진행 상태 ----

    /**
     * ⚠ 촬영항목 중 "가장 이른" 예정일시다. (2026-09-03 — 일정이 항목 단위로 바뀜)
     *   목록 한 줄에 시각 하나만 보여줘야 하고, 담당자가 알고 싶은 건 "이 환자가 언제 오는가"라
     *   첫 촬영 시각이 답이다. 항목별 시각은 오른쪽 일정 탭에서 본다.
     */
    @Schema(description = "가장 이른 촬영 예정일시. 일정이 하나도 없으면 null",
            example = "2026-09-05T09:30:00")
    private LocalDateTime scheduledAt;

    /**
     * ⚠ 일정도 검체·결과처럼 개수로 내려준다.
     *   촬영항목이 여러 개면 "3건 중 1건 일정" 같은 중간 상태가 실제로 생긴다.
     *   Y/N 으로는 그 상태를 표현할 수 없다.
     */
    @Schema(description = "촬영항목 수", example = "3")
    private int imageItemCount;

    @Schema(description = "일정이 잡힌 촬영항목 수", example = "1")
    private int scheduledItemCount;

    /**
     * ⚠ "철회되지 않은 동의가 하나라도 있는가"다. 유형별 완비 여부가 아니다.
     *   어떤 오더에 어떤 유형(조영제/침습)의 동의가 필요한지는 촬영항목별 기준이 있어야 판단할 수 있는데
     *   그 기준 데이터가 아직 없다. 지금은 "동의를 받기 시작했는가"까지만 본다.
     */
    @Schema(description = "유효한 동의 존재 여부 (Y/N)", example = "N")
    private String consentYn;

    /**
     * ⚠ 항상 0 이다. 촬영(영상파일) 등록 기능이 아직 없다. (ZP2-21)
     *   그래도 필드를 미리 두는 이유는, 화면이 "촬영 단계가 존재한다"를 표시해야 하기 때문이다.
     *   IMAGE_FILE 테이블은 있으나 엔티티를 만들지 않았다 — 등록 기능이 없어 세어봐야 0 이라
     *   지금 만들면 쓰이지 않는 코드만 남는다.
     */
    @Schema(description = "등록된 영상파일 수 (촬영 기능 구현 전까지 항상 0)", example = "0")
    private int imageFileCount;

    @Schema(description = "다음에 해야 할 일 — 서버가 계산한다", example = "CONSENT")
    private ImageWorklistStep nextStep;

    // ---- 제외 상태 ----

    @Schema(description = "접수상태코드 (ACCEPTED=처리 대상, EXCLUDED=제외됨)", example = "ACCEPTED")
    private String receptionStatusCode;

    @Schema(description = "제외 사유. 제외된 건에만 값이 있다", example = "환자 미방문으로 촬영 취소")
    private String exclusionReason;

    @Schema(description = "제외 일시. 제외된 건에만 값이 있다", example = "2026-09-02T11:20:00")
    private LocalDateTime excludedAt;
}
