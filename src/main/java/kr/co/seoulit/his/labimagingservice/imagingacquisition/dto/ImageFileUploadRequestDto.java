package kr.co.seoulit.his.labimagingservice.imagingacquisition.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

/**
 * 영상파일 업로드 요청
 * API: POST /api/lab-imaging/image-files (multipart/form-data)
 * 대응 유스케이스: UC-IMG-03 (Jira ZP2-105/106/108)
 *
 * ⚠ 이 프로젝트 최초의 멀티파트 업로드 API 다. JSON 본문(@RequestBody)이 아니라
 *   @ModelAttribute 로 바인딩한다 — Spring MVC 는 multipart/form-data 요청의 각 파트를
 *   DTO 필드에 자동으로 채워주는데, @RequestBody 는 JSON 본문 전용이라 파일 파트를 못 읽는다.
 *   (ImageFileController 참고)
 *
 * ⚠ @Setter 가 반드시 있어야 한다 — 이 프로젝트 다른 요청 DTO 는 @RequestBody(JSON)라
 *   Jackson 이 생성자로 바로 만들어서 세터가 필요 없었다. 하지만 @ModelAttribute 바인딩은
 *   Spring 의 WebDataBinder(BeanWrapper)가 처리하는데, 이건 기본 생성자로 객체를 만든 뒤
 *   "필드명 세터"로 값을 채워 넣는 방식이다. @Getter 만 있고 세터가 없으면 바인더가 값을
 *   넣을 방법이 없어 모든 필드가 조용히 null 로 남는다 — 예외도 안 나고 로그도 없다.
 *   (실제로 겪음: curl 로 값을 다 채워 보내도 서버는 5개 필드 전부 null 로 봤다.
 *    Bean Validation 이 그 null 들을 잡아 400 으로 응답해서 알아챌 수 있었다)
 *
 * ⚠ @Valid + @ModelAttribute 검증 실패는 BindException 계열이다.
 *   Spring 6 부터 MethodArgumentNotValidException 이 BindException 을 상속하도록 바뀌어서,
 *   실제로 관찰되는 예외는 (RequestBody 와 똑같이) MethodArgumentNotValidException 이다.
 *   그래서 GlobalExceptionHandler 의 기존 handleValidation 이 그대로 잡는다.
 *   더 상위 타입인 BindException 핸들러를 별도로 추가했지만 이 경로에서는 항상 더 구체적인
 *   handleValidation 이 먼저 매칭돼 실행되지 않는다 — 만에 하나 다른 원인으로 순수
 *   BindException 이 나는 경우를 위한 방어용으로만 남겨 둔다. (GlobalExceptionHandler 참고)
 *
 * ⚠ imageReceptionId 를 받는 이유 — 사전요건 검증(ZP2-105) 중 "촬영 일정이 등록되어 있는지"는
 *   ImageScheduleRepository 가 접수+항목 조합으로만 조회할 수 있다(UNIQUE 도 그 조합이다).
 *   imageOrderItemId 만으로는 그 일정을 찾을 수 없어, 화면이 이미 들고 있는 접수ID 를 함께 받는다.
 *   (ImageAcquisitionWorkPanel 의 prop 이 ImageWorklistItem 이라 imageReceptionId 는 이미 있다)
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "영상파일 업로드 요청 (multipart/form-data)")
public class ImageFileUploadRequestDto {

    @NotNull
    @Schema(description = "업로드할 영상 파일. 허용 형식은 화이트리스트 참고(ImageFileService.ALLOWED_CONTENT_TYPES)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private MultipartFile file;

    @NotBlank
    @Size(max = 36)
    @Schema(description = "영상접수ID (IMAGE_RECEPTION, UUID) — 촬영 일정 조회에 사용",
            example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f", requiredMode = Schema.RequiredMode.REQUIRED)
    private String imageReceptionId;

    @NotBlank
    @Size(max = 36)
    @Schema(description = "촬영항목ID (IMAGE_ORDER_ITEM, UUID)",
            example = "9c8b7a6f-1234-4e5f-9a0b-1c2d3e4f5a6b", requiredMode = Schema.RequiredMode.REQUIRED)
    private String imageOrderItemId;

    @NotBlank
    @Size(max = 36)
    @Schema(description = "환자ID (patient-service 내부 식별자) — 요청자가 보고 있는 접수의 환자와 "
            + "실제 오더의 환자가 같은지 확인하는 데 쓴다(환자 본인 확인, ZP2-105)",
            example = "3f7b1a20-6c2e-4e7a-9e2a-8b1f2c3d4e5f", requiredMode = Schema.RequiredMode.REQUIRED)
    private String patientId;

    @NotBlank
    @Size(max = 20)
    @Schema(description = "업로드자ID (참조 식별자)", example = "STF00021",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String uploadedById;
}
