package kr.co.seoulit.his.labimagingservice.laborder.controller;

import kr.co.seoulit.his.common.session.SessionUser;
import kr.co.seoulit.his.labimagingservice.common.cache.StaffDirectoryCache;
import kr.co.seoulit.his.labimagingservice.common.dto.ApiResponse;
import kr.co.seoulit.his.labimagingservice.common.session.ActorIdResolver;
import kr.co.seoulit.his.labimagingservice.common.session.StaffValidator;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderSummaryDto;
import kr.co.seoulit.his.labimagingservice.laborder.service.LabOrderService;
import kr.co.seoulit.his.labimagingservice.laborder.service.LabWorklistService;
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
 * LabOrderController.createOrder — 수동 등록 전용 직원 검증 + physicianNo 서버 보정.
 * (04번 지시서 Phase 2-B/2-E)
 *
 * ⚠ MockMvc 를 쓰지 않는다. @LoginUser 리졸버·검증 인터셉터까지 모두 올리는 통합 테스트는
 *   과하다 — 여기서 확인하려는 건 "컨트롤러가 StaffValidator/StaffDirectoryCache 를 호출해서
 *   physicianNo 를 바꿔 넘기는지"뿐이라, 메서드를 직접 호출하는 단위 테스트로 충분하다.
 */
class LabOrderControllerTest {

    private final LabOrderService labOrderService = mock(LabOrderService.class);
    private final ActorIdResolver actorIdResolver = mock(ActorIdResolver.class);
    private final LabWorklistService labWorklistService = mock(LabWorklistService.class);
    private final StaffValidator staffValidator = mock(StaffValidator.class);
    private final StaffDirectoryCache staffDirectoryCache = mock(StaffDirectoryCache.class);

    private final LabOrderController controller = new LabOrderController(
            labOrderService, actorIdResolver, labWorklistService, staffValidator, staffDirectoryCache);

    private static LabOrderCreateRequestDto.LabOrderCreateRequestDtoBuilder baseRequest() {
        return LabOrderCreateRequestDto.builder()
                .labOrderNo("LO-1")
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
        when(labOrderService.createOrder(any()))
                .thenReturn(LabOrderSummaryDto.builder().labOrderId("order-1").build());

        LabOrderCreateRequestDto request = baseRequest()
                .physicianId("doctor-1")
                .physicianNo("typed-by-user")
                .build();

        ResponseEntity<ApiResponse<LabOrderSummaryDto>> response = controller.createOrder(null, request);

        verify(staffValidator).requireDoctorIfPresent("doctor-1", "physicianId");
        ArgumentCaptor<LabOrderCreateRequestDto> captor = ArgumentCaptor.forClass(LabOrderCreateRequestDto.class);
        verify(labOrderService).createOrder(captor.capture());
        assertThat(captor.getValue().getPhysicianNo()).isEqualTo("10001");
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("physicianId가 디렉터리에서 안 찾아지면 화면이 보낸 physicianNo를 그대로 쓴다")
    void keepsOriginalPhysicianNoWhenDirectoryCannotResolve() {
        when(actorIdResolver.resolve(any(), any(), any())).thenReturn("received-by-1");
        when(staffDirectoryCache.findEmpNo(any())).thenReturn(null);
        when(labOrderService.createOrder(any()))
                .thenReturn(LabOrderSummaryDto.builder().labOrderId("order-1").build());

        LabOrderCreateRequestDto request = baseRequest()
                .physicianId(null)
                .physicianNo("legacy-manual-entry")
                .build();

        controller.createOrder(null, request);

        ArgumentCaptor<LabOrderCreateRequestDto> captor = ArgumentCaptor.forClass(LabOrderCreateRequestDto.class);
        verify(labOrderService).createOrder(captor.capture());
        assertThat(captor.getValue().getPhysicianNo()).isEqualTo("legacy-manual-entry");
    }
}
