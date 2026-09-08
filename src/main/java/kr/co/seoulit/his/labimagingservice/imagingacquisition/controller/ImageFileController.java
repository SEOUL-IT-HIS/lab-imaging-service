package kr.co.seoulit.his.labimagingservice.imagingacquisition.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.dto.ApiResponse;
import kr.co.seoulit.his.labimagingservice.imagingacquisition.dto.ImageFileSummaryDto;
import kr.co.seoulit.his.labimagingservice.imagingacquisition.dto.ImageFileUploadRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingacquisition.service.ImageFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 촬영/영상파일 API
 * 대응 유스케이스: UC-IMG-03 (Jira ZP2-21)
 *
 * 엔드포인트
 *   POST /api/lab-imaging/image-files                        영상파일 업로드 (ZP2-105/106/108)
 *   GET  /api/lab-imaging/image-files?imageOrderItemId=      촬영항목별 목록 조회 (ZP2-110)
 *   GET  /api/lab-imaging/image-files/{imageFileId}/download  다운로드
 *
 * ⚠ 업로드가 multipart/form-data 인 이 프로젝트 최초의 API 다.
 *   그래서 @RequestBody 가 아니라 @ModelAttribute 로 받는다 — 자세한 이유는
 *   ImageFileUploadRequestDto 주석과 GlobalExceptionHandler.handleBind 참고.
 */
@RestController
@RequestMapping("/api/lab-imaging/image-files")
@RequiredArgsConstructor
@Tag(name = "촬영/영상파일", description = "UC-IMG-03")
public class ImageFileController {

    private final ImageFileService imageFileService;

    @Operation(summary = "영상파일 업로드",
            description = "촬영한 영상파일을 SeaweedFS 에 저장하고 IMAGE_FILE 에 등록한다. "
                    + "사전요건(접수·항목 존재, 환자 본인 확인, 동의 등록, 촬영 일정 등록)을 만족해야 하며, "
                    + "성공 시 대상 촬영항목의 상태를 촬영완료(ACQUIRED)로 전이한다. "
                    + "허용 파일 형식: application/dicom, image/jpeg, image/png, image/tiff.",
            requestBody = @RequestBody(
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE)))
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImageFileSummaryDto>> uploadImageFile(
            @Valid @ModelAttribute ImageFileUploadRequestDto request) {

        ImageFileSummaryDto response = imageFileService.uploadImageFile(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(response, LabMessageCode.LAB048, "영상파일이 등록되었습니다.")
        );
    }

    @Operation(summary = "촬영항목별 영상파일 목록 조회",
            description = "촬영항목ID(IMAGE_ORDER_ITEM)로 등록된 영상파일 전체를 업로드순으로 조회한다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ImageFileSummaryDto>>> getImageFiles(
            @RequestParam String imageOrderItemId) {

        List<ImageFileSummaryDto> response =
                imageFileService.getImageFilesByOrderItemId(imageOrderItemId);

        return ResponseEntity.ok(
                ApiResponse.success(response, LabMessageCode.LAB049, "영상파일 조회에 성공했습니다.")
        );
    }

    /**
     * ⚠ ApiResponse<T> 로 감싸지 않는다. 파일 바이너리 자체가 응답 본문이라
     *   JSON 래퍼에 담을 수 없다 — 이 API 하나만 이 프로젝트의 공통 응답 포맷 예외다.
     *   실패(파일 없음 등)는 그대로 예외로 던져 GlobalExceptionHandler 가 ApiResponse 로
     *   응답하므로, "성공만 형식이 다르고 실패는 형식이 같은" 상태다.
     */
    @Operation(summary = "영상파일 다운로드",
            description = "SeaweedFS 에 저장된 원본 파일을 그대로 내려받는다.")
    @GetMapping("/{imageFileId}/download")
    public ResponseEntity<byte[]> downloadImageFile(@Parameter(description = "영상파일ID")
                                                      @PathVariable String imageFileId) {

        ImageFileService.DownloadedFile file = imageFileService.downloadImageFile(imageFileId);

        String encodedFileName = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFileName)
                .body(file.content());
    }
}
