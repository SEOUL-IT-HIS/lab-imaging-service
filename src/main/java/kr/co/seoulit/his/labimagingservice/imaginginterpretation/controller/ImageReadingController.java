package kr.co.seoulit.his.labimagingservice.imaginginterpretation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.dto.ApiResponse;
import kr.co.seoulit.his.labimagingservice.imaginginterpretation.dto.ImageReadingAssignRequestDto;
import kr.co.seoulit.his.labimagingservice.imaginginterpretation.dto.ImageReadingConfirmRequestDto;
import kr.co.seoulit.his.labimagingservice.imaginginterpretation.dto.ImageReadingFindingsRequestDto;
import kr.co.seoulit.his.labimagingservice.imaginginterpretation.dto.ImageReadingSummaryDto;
import kr.co.seoulit.his.labimagingservice.imaginginterpretation.service.ImageReadingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 영상판독 API
 * 대응 유스케이스: UC-IMG-04 영상판독처리 (Jira ZP2-23)
 *
 * 엔드포인트
 *   GET  /api/lab-imaging/image-readings/worklist                     판독 워크리스트 (findOrCreate)
 *   GET  /api/lab-imaging/image-readings/{imageOrderItemId}           단건 상세 (findOrCreate)
 *   POST /api/lab-imaging/image-readings/{imageReadingId}/assign      담당자 배정
 *   PUT  /api/lab-imaging/image-readings/{imageReadingId}/findings    소견 입력/수정
 *   POST /api/lab-imaging/image-readings/{imageReadingId}/confirm     확정(전자서명)
 *
 * ⚠ 확정은 PUT 이 아니라 POST /{id}/confirm 이다. (LabResultController 와 같은 규칙 —
 *   PUT 은 값 교체, 확정은 상태를 한 방향으로 넘기는 행위)
 *
 * ⚠ 응답은 항상 ApiResponse<T> 로 감싸고, 성공 메시지는 LabMessageCode 상수를 쓴다.
 *
 * ⚠ 영상파일 목록/다운로드 엔드포인트는 여기 없다. imagingacquisition 패키지의
 *   ImageFileController 를 그대로 쓴다 — 화면(ImageReadingDetail)이 두 API 를 함께 호출해 조립한다.
 */
@RestController
@RequestMapping("/api/lab-imaging/image-readings")
@RequiredArgsConstructor
@Tag(name = "영상 판독", description = "UC-IMG-04")
public class ImageReadingController {

    private final ImageReadingService imageReadingService;

    /**
     * ⚠ /{imageOrderItemId} 보다 위에 둔다. "worklist" 가 고정 세그먼트라 Spring 이 알아서
     *   구체적인 쪽을 먼저 고르지만(ImageOrderController.getWorklist 주석 참고), 읽는 사람이
     *   멈추지 않게 선언 순서도 맞춘다.
     */
    @Operation(summary = "판독 워크리스트 조회",
            description = "영상파일이 1건 이상 등록된 촬영항목을 모아 판독 대상 목록을 조회한다. "
                    + "IMAGE_READING 이 없는 항목은 이 호출 시점에 대기(01) 상태로 자동 생성된다(findOrCreate). "
                    + "응급(urgencyYn=Y) 건이 위로 온다.")
    @GetMapping("/worklist")
    public ResponseEntity<ApiResponse<List<ImageReadingSummaryDto>>> getReadingWorklist() {

        List<ImageReadingSummaryDto> response = imageReadingService.getReadingWorklist();

        return ResponseEntity.ok(
                ApiResponse.success(response, LabMessageCode.LAB058, "판독 워크리스트 조회에 성공했습니다.")
        );
    }

    @Operation(summary = "판독 단건 상세 조회",
            description = "촬영항목ID(IMAGE_ORDER_ITEM)로 판독 정보를 조회한다. "
                    + "IMAGE_READING 이 없으면 이 호출 시점에 대기(01) 상태로 자동 생성된다(findOrCreate).")
    @GetMapping("/{imageOrderItemId}")
    public ResponseEntity<ApiResponse<ImageReadingSummaryDto>> getReadingByOrderItemId(
            @PathVariable String imageOrderItemId) {

        ImageReadingSummaryDto response = imageReadingService.getReadingByOrderItemId(imageOrderItemId);

        return ResponseEntity.ok(
                ApiResponse.success(response, LabMessageCode.LAB065, "판독 조회에 성공했습니다.")
        );
    }

    @Operation(summary = "판독 담당자 배정",
            description = "판독상태를 대기(01)에서 판독중(02)으로 전이하고 배정자·배정일시를 기록한다. "
                    + "이미 판독중인 건도 담당자 변경(재배정)으로 허용하지만, 확정(03)된 건은 LAB062 로 거절한다.")
    @PostMapping("/{imageReadingId}/assign")
    public ResponseEntity<ApiResponse<ImageReadingSummaryDto>> assignReading(
            @PathVariable String imageReadingId,
            @Valid @RequestBody ImageReadingAssignRequestDto request) {

        ImageReadingSummaryDto response =
                imageReadingService.assignReading(imageReadingId, request.getAssignedToId());

        return ResponseEntity.ok(
                ApiResponse.success(response, LabMessageCode.LAB057, "판독이 배정되었습니다.")
        );
    }

    @Operation(summary = "판독 소견 입력/수정",
            description = "확정 전(대기/판독중)인 판독만 소견을 입력·수정할 수 있다. "
                    + "이미 확정된 판독을 수정하려 하면 LAB062 로 거절한다.")
    @PutMapping("/{imageReadingId}/findings")
    public ResponseEntity<ApiResponse<ImageReadingSummaryDto>> updateFindings(
            @PathVariable String imageReadingId,
            @Valid @RequestBody ImageReadingFindingsRequestDto request) {

        ImageReadingSummaryDto response =
                imageReadingService.updateFindings(imageReadingId, request.getFindings());

        return ResponseEntity.ok(
                ApiResponse.success(response, LabMessageCode.LAB059, "판독 소견이 저장되었습니다.")
        );
    }

    @Operation(summary = "판독 확정(전자서명)",
            description = "판독상태를 확정(03)으로 전이하고 확정자·확정일시를 기록한다. "
                    + "소견이 비어 있으면 LAB063, 이미 확정된 건은 LAB062 로 거절한다. 확정 후에는 수정할 수 없다.")
    @PostMapping("/{imageReadingId}/confirm")
    public ResponseEntity<ApiResponse<ImageReadingSummaryDto>> confirmReading(
            @PathVariable String imageReadingId,
            @Valid @RequestBody ImageReadingConfirmRequestDto request) {

        ImageReadingSummaryDto response =
                imageReadingService.confirmReading(imageReadingId, request.getSignedById());

        return ResponseEntity.ok(
                ApiResponse.success(response, LabMessageCode.LAB060, "판독이 확정되었습니다.")
        );
    }
}
