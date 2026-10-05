package kr.co.seoulit.his.labimagingservice.laborder.service;

import kr.co.seoulit.his.labimagingservice.businessdelegate.patient.PatientServiceBusinessDelegate;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderItemRequestDto;
import kr.co.seoulit.his.labimagingservice.laborder.mapper.LabOrderMapper;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabOrderRepository;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabReceptionRepository;
import kr.co.seoulit.his.labimagingservice.labschedule.repository.LabScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 검사 오더 접수 — 검사항목 중복 입력 거절(LAB113, 04번 지시서 Phase 3-E-2).
 *
 * ⚠ 이 검증은 수동 접수(LabOrderController)와 연계 수신(LabOrderIntakeService) 모두가
 *   거치는 LabOrderService.createOrder 안에 있다 — StaffValidator 와 달리 "처방의 확인"이
 *   아니라 데이터 정합성 검사라서 두 경로 모두에 적용해도 지시서 §0-2 제약(처방의/직원 사유로
 *   intake 를 거절하지 않는다)에 걸리지 않는다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LabOrderServiceTest {

    @Mock LabOrderRepository labOrderRepository;
    @Mock LabReceptionRepository labReceptionRepository;
    @Mock LabOrderMapper labOrderMapper;
    @Mock PatientServiceBusinessDelegate patientServiceBusinessDelegate;
    @Mock CommonCodeCache commonCodeCache;
    @Mock LabScheduleRepository labScheduleRepository;

    @InjectMocks LabOrderService service;

    @BeforeEach
    void setUp() {
        when(patientServiceBusinessDelegate.validatePatient(anyString())).thenReturn(true);
        when(commonCodeCache.isValid(anyString(), anyString())).thenReturn(true);
        when(labOrderRepository.existsByLabOrderNo(anyString())).thenReturn(false);
        when(labOrderRepository.save(any())).thenThrow(new UnsupportedOperationException(
                "이 테스트는 save() 까지 도달하면 안 된다 — 중복 항목은 그 전에 거절돼야 한다"));
    }

    private static LabOrderCreateRequestDto request(List<LabOrderItemRequestDto> items) {
        return LabOrderCreateRequestDto.builder()
                .labOrderNo("LO-1")
                .systemCode("01")
                .patientId("patient-1")
                .treatTypeCode("01")
                .urgencyYn("N")
                .orderItems(items)
                .build();
    }

    @Test
    @DisplayName("같은 검사항목코드가 두 번 입력되면 LAB113으로 거절하고 저장하지 않는다")
    void rejectsDuplicateItems() {
        LabOrderCreateRequestDto request = request(List.of(
                LabOrderItemRequestDto.builder().labItemCode("01").build(),
                LabOrderItemRequestDto.builder().labItemCode("01").build()));

        assertThatThrownBy(() -> service.createOrder(request))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB113);
        verify(labOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("서로 다른 검사항목코드는 중복이 아니다 — 저장 단계까지 진행된다")
    void allowsDistinctItems() {
        LabOrderCreateRequestDto request = request(List.of(
                LabOrderItemRequestDto.builder().labItemCode("01").build(),
                LabOrderItemRequestDto.builder().labItemCode("02").build()));

        // save() 가 테스트용 예외를 던지도록 세팅했으므로, 그 예외가 나온다는 것 자체가
        // "중복 검사를 통과해 save() 호출까지 도달했다"는 뜻이다.
        assertThatThrownBy(() -> service.createOrder(request))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
