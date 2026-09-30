package kr.co.seoulit.his.labimagingservice.labresult.pathology.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.co.seoulit.his.common.session.SessionUser;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.dto.ApiResponse;
import kr.co.seoulit.his.labimagingservice.common.session.ActorIdResolver;
import kr.co.seoulit.his.labimagingservice.common.session.LoginUser;
import kr.co.seoulit.his.labimagingservice.labresult.dto.LabResultConfirmRequestDto;
import kr.co.seoulit.his.labimagingservice.labresult.pathology.dto.PathologyResultCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.labresult.pathology.dto.PathologyResultSummaryDto;
import kr.co.seoulit.his.labimagingservice.labresult.pathology.dto.PathologyResultUpdateRequestDto;
import kr.co.seoulit.his.labimagingservice.labresult.pathology.service.PathologyResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 병리검사결과 API
 * 대응 유스케이스: UC-RST-03 병리검사결과등록 (Jira ZP2-15: 93~98)
 *
 * 엔드포인트
 *   POST /api/lab-imaging/pathology-results                          등록 (multipart: request=JSON, file=첨부 선택)
 *   PUT  /api/lab-imaging/pathology-results/{id}                     수정 (multipart, file 을 보내면 첨부 교체)
 *   POST /api/lab-imaging/pathology-results/{id}/confirm             확정
 *   GET  /api/lab-imaging/pathology-results/{id}                     단건
 *   GET  /api/lab-imaging/pathology-results/receptions/{receptionNo} 접수의 결과 목록
 *   GET  /api/lab-imaging/pathology-results/{id}/attachment          첨부 파일 (inline — 화면 미리보기용)
 *
 * ⚠ 영상파일 다운로드는 attachment(내려받기)로 주지만 여기는 inline 이다. 병리 첨부는 화면에서 바로
 *   썸네일·PDF 로 보여주는 용도(ZP2-98)라서다.
 */
@RestController
@RequestMapping("/api/lab-imaging/pathology-results")
@RequiredArgsConstructor
@Tag(name = "병리검사 결과", description = "UC-RST-03")
public class PathologyResultController {

    private final PathologyResultService pathologyResultService;
    private final ActorIdResolver actorIdResolver;

    @Operation(summary = "병리 결과 등록",
            description = "multipart/form-data — 'request' 파트(JSON)와 'file' 파트(선택, jpg/png/pdf). "
                    + "첨부 업로드가 실패하면 결과도 저장하지 않는다(LAB055/LAB056).")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PathologyResultSummaryDto>> createResult(
            @LoginUser SessionUser loginUser,
            @Valid @RequestPart("request") PathologyResultCreateRequestDto request,
            @RequestPart(value = "file", required = false) MultipartFile file) {

        PathologyResultSummaryDto response = pathologyResultService.createResult(request.toBuilder()
                .recordedById(actorIdResolver.resolve(loginUser, request.getRecordedById(), "recordedById")).build(),
                file);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(response, LabMessageCode.LAB080, "병리 검사 결과가 등록되었습니다."));
    }

    @Operation(summary = "병리 결과 수정", description = "확정 전만. 'file' 파트를 보내면 첨부를 교체한다(재등록).")
    @PutMapping(value = "/{pathologyResultId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PathologyResultSummaryDto>> updateResult(
            @PathVariable String pathologyResultId,
            @Valid @RequestPart("request") PathologyResultUpdateRequestDto request,
            @RequestPart(value = "file", required = false) MultipartFile file) {

        return ResponseEntity.ok(ApiResponse.success(
                pathologyResultService.updateResult(pathologyResultId, request, file),
                LabMessageCode.LAB083, "병리 검사 결과가 수정되었습니다."));
    }

    @Operation(summary = "병리 결과 확정", description = "01 → 02. 확정 후 수정 불가. 확정자는 로그인 사용자.")
    @PostMapping("/{pathologyResultId}/confirm")
    public ResponseEntity<ApiResponse<PathologyResultSummaryDto>> confirmResult(
            @PathVariable String pathologyResultId,
            @LoginUser SessionUser loginUser,
            @Valid @RequestBody LabResultConfirmRequestDto request) {

        String confirmedById = actorIdResolver.resolve(loginUser, request.getConfirmedById(), "confirmedById");
        return ResponseEntity.ok(ApiResponse.success(
                pathologyResultService.confirmResult(pathologyResultId, confirmedById),
                LabMessageCode.LAB084, "병리 검사 결과가 확정되었습니다."));
    }

    @Operation(summary = "병리 결과 단건 조회")
    @GetMapping("/{pathologyResultId}")
    public ResponseEntity<ApiResponse<PathologyResultSummaryDto>> getResult(@PathVariable String pathologyResultId) {
        return ResponseEntity.ok(ApiResponse.success(
                pathologyResultService.getResult(pathologyResultId),
                LabMessageCode.LAB081, "병리 검사 결과 조회에 성공했습니다."));
    }

    @Operation(summary = "접수의 병리 결과 목록", description = "병리 항목마다 0~1건. 0건은 빈 목록.")
    @GetMapping("/receptions/{receptionNo}")
    public ResponseEntity<ApiResponse<List<PathologyResultSummaryDto>>> getResultsByReception(
            @PathVariable String receptionNo) {
        return ResponseEntity.ok(ApiResponse.success(
                pathologyResultService.getResultsByReceptionNo(receptionNo),
                LabMessageCode.LAB081, "병리 검사 결과 조회에 성공했습니다."));
    }

    @Operation(summary = "병리 첨부 파일", description = "inline 으로 내려준다(화면 미리보기). 첨부가 없으면 LAB086.")
    @GetMapping("/{pathologyResultId}/attachment")
    public ResponseEntity<byte[]> getAttachment(@PathVariable String pathologyResultId) {

        PathologyResultService.Attachment file = pathologyResultService.downloadAttachment(pathologyResultId);
        String encodedFileName = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedFileName)
                .body(file.content());
    }
}
