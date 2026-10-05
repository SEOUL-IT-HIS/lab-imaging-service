package kr.co.seoulit.his.labimagingservice.imagingorder.controller;

import kr.co.seoulit.his.labimagingservice.common.cache.StaffDirectoryCache;
import kr.co.seoulit.his.labimagingservice.common.dto.ApiResponse;
import kr.co.seoulit.his.labimagingservice.common.session.ActorIdResolver;
import kr.co.seoulit.his.labimagingservice.common.session.StaffValidator;
import kr.co.seoulit.his.labimagingservice.imagingorder.dto.ImageOrderCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingorder.dto.ImageOrderSummaryDto;
import kr.co.seoulit.his.labimagingservice.imagingorder.service.ImageOrderService;
import kr.co.seoulit.his.labimagingservice.imagingorder.service.ImageWorklistService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ImageOrderController.createOrder — LabOrderControllerTest 와 같은 확인(직원 검증 +
 * physicianNo 서버 보정). (04번 지시서 Phase 2-B/2-E)
 */
class ImageOrderControllerTest {

    private final ImageOrderService imageOrderService = mock(ImageOrderService.class);
    private final ActorIdResolver actorIdResolver = mock(ActorIdResolver.class);
    private final ImageWorklistService imageWorklistService = mock(ImageWorklistService.class);
    private final StaffValidator staffValidator = mock(StaffValidator.class);
    private final StaffDirectoryCache staffDirectoryCache = mock(StaffDirectoryCache.class);

    private final ImageOrderController controller = new ImageOrderController(
            imageOrderService, actorIdResolver, imageWorklistService, staffValidator, staffDirectoryCache);

    private static ImageOrderCreateRequestDto.ImageOrderCreateRequestDtoBuilder baseRequest() {
        return ImageOrderCreateRequestDto.builder()
                .imageOrderNo("IO-1")
                .systemCode("01")
                .patientId("patient-1")
                .treatTypeCode("01")
                .urgencyYn("N");
    }

    @Test
    @DisplayName("physicianId가 디렉터리에 있으면 그 직원의 실제 사번으로 physicianNo를 덮어쓴다")
    void overridesPhysicianNoWhenDirectoryKnowsPhysician() {
        when(actorIdResolver.resolve(any(), any(), any())).thenReturn("received-by-1");
        when(staffDirectoryCache.findEmpNo("doctor-1")).thenReturn("10001");
        when(imageOrderService.createOrder(any()))
                .thenReturn(ImageOrderSummaryDto.builder().imageOrderId("order-1").build());

        ImageOrderCreateRequestDto request = baseRequest()
                .physicianId("doctor-1")
                .physicianNo("typed-by-user")
                .build();

        ResponseEntity<ApiResponse<ImageOrderSummaryDto>> response = controller.createOrder(null, request);

        verify(staffValidator).requireDoctorIfPresent("doctor-1", "physicianId");
        ArgumentCaptor<ImageOrderCreateRequestDto> captor = ArgumentCaptor.forClass(ImageOrderCreateRequestDto.class);
        verify(imageOrderService).createOrder(captor.capture());
        assertThat(captor.getValue().getPhysicianNo()).isEqualTo("10001");
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("physicianId가 디렉터리에서 안 찾아지면 화면이 보낸 physicianNo를 그대로 쓴다")
    void keepsOriginalPhysicianNoWhenDirectoryCannotResolve() {
        when(actorIdResolver.resolve(any(), any(), any())).thenReturn("received-by-1");
        when(staffDirectoryCache.findEmpNo(any())).thenReturn(null);
        when(imageOrderService.createOrder(any()))
                .thenReturn(ImageOrderSummaryDto.builder().imageOrderId("order-1").build());

        ImageOrderCreateRequestDto request = baseRequest()
                .physicianId(null)
                .physicianNo("legacy-manual-entry")
                .build();

        controller.createOrder(null, request);

        ArgumentCaptor<ImageOrderCreateRequestDto> captor = ArgumentCaptor.forClass(ImageOrderCreateRequestDto.class);
        verify(imageOrderService).createOrder(captor.capture());
        assertThat(captor.getValue().getPhysicianNo()).isEqualTo("legacy-manual-entry");
    }
}
