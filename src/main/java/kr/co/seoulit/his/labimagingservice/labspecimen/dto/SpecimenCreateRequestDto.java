package kr.co.seoulit.his.labimagingservice.labspecimen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 검체 채취정보 등록 요청
 * API: POST /api/lab-imaging/specimens
 * 대응 유스케이스: UC-SPC-03 검체식별관리 (Jira ZP2-68)
 *
 * ⚠ specimenBarcode 는 요청으로 받지 않는다. 서버가 채번해서 응답으로 내려준다. (ZP2-65)
 *   채취 담당자가 바코드를 직접 입력할 일이 없고, 값이 겹치면 안 되기 때문이다.
 *   채번 규칙은 SpecimenService.generateSpecimenBarcode 참고.
 */

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "검체 채취정보 등록 요청")
public class SpecimenCreateRequestDto {

    @NotBlank
    @Size(max = 36)
    @Schema(description = "접수ID", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f", requiredMode = Schema.RequiredMode.REQUIRED)
    private String labReceptionId;

    @NotBlank
    @Size(max = 10)
    @Schema(description = "검체용기코드", example = "튜브")
    private String specimenContainerCode;

    // enum 필드에는 @Size 가 동작하지 않아 붙이지 않는다. 값 검증은 Jackson 역직렬화가 담당한다.
    @NotNull
    @Schema(description = "검체종류", example = "BLOOD", requiredMode = Schema.RequiredMode.REQUIRED)
    private SpecimenType specimenType;

    /**
     * ⚠ @NotBlank 를 뗐다. 연계 수신으로 만들어진 접수는 환자번호가 없어서,
     *   그 접수의 검체를 등록할 때 넘길 값이 없다. (LabOrderCreateRequestDto 와 같은 사유)
     */
    @Size(max = 20)
    @Schema(description = "환자번호 (화면 표시용 업무번호, 없을 수 있음)", example = "P00012345")
    private String patientNo;

    @NotBlank
    @Size(max = 36)
    @Schema(description = "환자ID (patient-service 내부 식별자, 참조/검증용)", example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f", requiredMode = Schema.RequiredMode.REQUIRED)
    private String patientId;

    @NotNull
    @Schema(description = "검체채취일시", example = "2026-07-25T09:30:00")
    private LocalDateTime collectedAt;

    @NotBlank
    @Size(max = 20)
    @Schema(description = "검체채취자ID", example = "STF00021")
    private String collectedById;
}
