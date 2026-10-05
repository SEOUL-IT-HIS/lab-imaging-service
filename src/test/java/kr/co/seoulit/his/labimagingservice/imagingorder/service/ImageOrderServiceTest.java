package kr.co.seoulit.his.labimagingservice.imagingorder.service;

import kr.co.seoulit.his.labimagingservice.businessdelegate.patient.PatientServiceBusinessDelegate;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.imagingorder.dto.ImageOrderCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingorder.dto.ImageOrderItemRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingorder.mapper.ImageOrderMapper;
import kr.co.seoulit.his.labimagingservice.imagingorder.repository.ImageOrderRepository;
import kr.co.seoulit.his.labimagingservice.imagingorder.repository.ImageReceptionRepository;
import kr.co.seoulit.his.labimagingservice.imagingschedule.repository.ImageScheduleRepository;
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
 * 영상 오더 접수 — 촬영항목 중복 입력 거절(LAB113, 04번 지시서 Phase 3-E-2).
 * LabOrderServiceTest 와 같은 이유·같은 패턴(그쪽 클래스 주석 참고).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImageOrderServiceTest {

    @Mock ImageOrderRepository imageOrderRepository;
    @Mock ImageReceptionRepository imageReceptionRepository;
    @Mock ImageOrderMapper imageOrderMapper;
    @Mock PatientServiceBusinessDelegate patientServiceBusinessDelegate;
    @Mock CommonCodeCache commonCodeCache;
    @Mock ImageScheduleRepository imageScheduleRepository;

    @InjectMocks ImageOrderService service;

    @BeforeEach
    void setUp() {
        when(patientServiceBusinessDelegate.validatePatient(anyString())).thenReturn(true);
        when(commonCodeCache.isValid(anyString(), anyString())).thenReturn(true);
        when(imageOrderRepository.existsByImageOrderNo(anyString())).thenReturn(false);
        when(imageOrderRepository.save(any())).thenThrow(new UnsupportedOperationException(
                "이 테스트는 save() 까지 도달하면 안 된다 — 중복 항목은 그 전에 거절돼야 한다"));
    }

    private static ImageOrderCreateRequestDto request(List<ImageOrderItemRequestDto> items) {
        return ImageOrderCreateRequestDto.builder()
                .imageOrderNo("IO-1")
                .systemCode("01")
                .patientId("patient-1")
                .treatTypeCode("01")
                .urgencyYn("N")
                .orderItems(items)
                .build();
    }

    @Test
    @DisplayName("같은 촬영항목코드가 두 번 입력되면 LAB113으로 거절하고 저장하지 않는다")
    void rejectsDuplicateItems() {
        ImageOrderCreateRequestDto request = request(List.of(
                ImageOrderItemRequestDto.builder().imageItemCode("01").build(),
                ImageOrderItemRequestDto.builder().imageItemCode("01").build()));

        assertThatThrownBy(() -> service.createOrder(request))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB113);
        verify(imageOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("서로 다른 촬영항목코드는 중복이 아니다 — 저장 단계까지 진행된다")
    void allowsDistinctItems() {
        ImageOrderCreateRequestDto request = request(List.of(
                ImageOrderItemRequestDto.builder().imageItemCode("01").build(),
                ImageOrderItemRequestDto.builder().imageItemCode("02").build()));

        assertThatThrownBy(() -> service.createOrder(request))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
