package kr.co.seoulit.his.labimagingservice.imagingorder.controller;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.dto.ApiResponse;
import kr.co.seoulit.his.labimagingservice.imagingorder.dto.ImageOrderCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingorder.dto.ImageOrderCreateResponseDto;
import kr.co.seoulit.his.labimagingservice.imagingorder.service.ImageOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 영상 오더 관련 API
 * 대응 유스케이스: UC-IMG-01 영상오더접수 (Jira ZP2-19)
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Image Order", description = "영상 오더 접수 API")
public class ImageOrderController {

    private final ImageOrderService imageOrderService;

    @Operation(summary = "영상 오더 접수", description = "외부 시스템(외래/병동/응급)에서 발생한 영상 오더를 접수하고, 영상검사접수(IMAGE_RECEPTION)를 함께 생성한다.")
    @PostMapping("/image-orders")
    public ResponseEntity<ApiResponse<ImageOrderCreateResponseDto>> createOrder(
            @Valid @RequestBody ImageOrderCreateRequestDto request) {

        ImageOrderCreateResponseDto response = imageOrderService.createOrder(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(response, LabMessageCode.LAB005, "영상 접수가 생성되었습니다.")
        );
    }
}
