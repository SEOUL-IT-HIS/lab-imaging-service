package kr.co.seoulit.his.labimagingservice.imagingconsent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.co.seoulit.his.labimagingservice.common.YnValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 조영제/침습검사 동의 등록 요청
 * API: POST /api/lab-imaging/consents
 * 대응 유스케이스: UC-IMG-05 (Jira ZP2-84 동의 여부 등록 및 변경, ZP2-83 필수값·유효성 검증)
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "조영제/침습검사 동의 등록 요청")
public class ConsentCreateRequestDto {

    @NotBlank
    @Size(max = 36)
    @Schema(description = "영상오더ID (IMAGE_ORDER 참조, UUID)", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f", requiredMode = Schema.RequiredMode.REQUIRED)
    private String imageOrderId;

    @NotBlank
    @Size(max = 36)
    @Schema(description = "환자ID (patient-service 내부 식별자, 참조/검증용)", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f", requiredMode = Schema.RequiredMode.REQUIRED)
    private String patientId;

    @NotBlank
    @Size(max = 10)
    @Schema(description = "동의서유형코드 (공통코드 CONSENT_TYPE_CD)", example = "조영제사용", requiredMode = Schema.RequiredMode.REQUIRED)
    private String consentTypeCode;

    /**
     * ⚠ 더 이상 필수가 아니다. (2026-10-06 결정 — 동의서를 종이문서로 보관하기로 확정되면서
     *   admin-service 문서양식(DOCUMENT_TEMPLATE)을 참조할 일이 없어졌다. 화면도 이 입력칸을
     *   뺐다. 보내더라도 형식 검증은 하지 않는다 — 검증할 "전자양식" 개념 자체가 없어졌다.)
     */
    @Size(max = 36)
    @Schema(description = "(더 이상 쓰지 않음 — 동의서는 종이문서로 보관한다) 동의서양식ID",
            example = "null")
    private String documentTemplateId;

    @NotBlank
    @YnValue
    @Schema(description = "동의여부 (Y/N)", example = "Y", requiredMode = Schema.RequiredMode.REQUIRED)
    private String consentYn;

    @NotNull
    @Schema(description = "동의일자", example = "2026-07-25", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate consentDt;

    @NotBlank
    @Size(max = 50)
    @Schema(description = "서명자명 (환자 또는 법정대리인, 이 화면에서 직접 입력)", example = "홍길동", requiredMode = Schema.RequiredMode.REQUIRED)
    private String signedByName;

    @Size(max = 36)
    @Schema(description = "(로그인 세션이 있으면 무시 — 서버가 로그인 사용자 empId 로 기록) 확인자ID", example = "STF00021")
    private String witnessId;

    /** 5차 Phase 9-2. 거부(consentYn=N)일 때만 저장한다 — 동의(Y)에 사유를 보내면 서버가 버린다 */
    @Size(max = 500)
    @Schema(description = "동의 거부 사유 (선택, consentYn=N 일 때만 저장)", example = "조영제 부작용 경험으로 거부")
    private String refusalNote;
}
