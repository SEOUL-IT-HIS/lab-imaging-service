package kr.co.seoulit.his.labimagingservice.labspecimen.service;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabReceptionEntity;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabReceptionRepository;
import kr.co.seoulit.his.labimagingservice.labspecimen.dto.SpecimenCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenEntity;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenType;
import kr.co.seoulit.his.labimagingservice.labspecimen.mapper.SpecimenMapper;
import kr.co.seoulit.his.labimagingservice.labspecimen.repository.SpecimenAcceptanceRepository;
import kr.co.seoulit.his.labimagingservice.labspecimen.repository.SpecimenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 검체 등록 시 환자 대조 (후속조치 #2, UC-SPC-03).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SpecimenServiceTest {

    private static final String RECEPTION_ID = "rec-1";
    private static final String ORDER_PATIENT_ID = "patient-A";

    @Mock SpecimenRepository specimenRepository;
    @Mock SpecimenAcceptanceRepository specimenAcceptanceRepository;
    @Mock CommonCodeCache commonCodeCache;
    @Mock SpecimenMapper specimenMapper;
    @Mock LabReceptionRepository labReceptionRepository;

    @InjectMocks SpecimenService specimenService;

    @BeforeEach
    void setUp() {
        LabOrderEntity order = mock(LabOrderEntity.class);
        when(order.getPatientId()).thenReturn(ORDER_PATIENT_ID);

        LabReceptionEntity reception = mock(LabReceptionEntity.class);
        when(reception.getLabOrder()).thenReturn(order);
        when(reception.getLabReceptionId()).thenReturn(RECEPTION_ID);

        when(labReceptionRepository.findById(RECEPTION_ID)).thenReturn(Optional.of(reception));
        when(commonCodeCache.isValid(anyString(), anyString())).thenReturn(true);
        when(specimenRepository.existsBySpecimenBarcode(anyString())).thenReturn(false);
        when(specimenRepository.save(any(SpecimenEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private SpecimenCreateRequestDto request(String patientId) {
        return SpecimenCreateRequestDto.builder()
                .labReceptionId(RECEPTION_ID)
                .specimenContainerCode("01")
                .specimenType(SpecimenType.values()[0])
                .patientId(patientId)
                .collectedAt(LocalDateTime.now())
                .collectedById("emp-1")
                .build();
    }

    @Test
    @DisplayName("요청 환자ID 가 접수의 환자와 다르면 LAB051 로 거절하고 저장하지 않는다")
    void mismatchRejected() {
        assertThatThrownBy(() -> specimenService.createSpecimen(request("patient-B")))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB051);

        verify(specimenRepository, never()).save(any());
    }

    @Test
    @DisplayName("요청에 환자ID 가 없으면 접수의 환자ID 로 채워 저장한다")
    void missingFilledFromReception() {
        specimenService.createSpecimen(request(null));

        ArgumentCaptor<SpecimenEntity> captor = ArgumentCaptor.forClass(SpecimenEntity.class);
        verify(specimenRepository).save(captor.capture());
        assertThat(captor.getValue().getPatientId()).isEqualTo(ORDER_PATIENT_ID);
    }

    @Test
    @DisplayName("요청 환자ID 가 접수의 환자와 같으면 그대로 저장한다")
    void matchSaved() {
        specimenService.createSpecimen(request(ORDER_PATIENT_ID));

        ArgumentCaptor<SpecimenEntity> captor = ArgumentCaptor.forClass(SpecimenEntity.class);
        verify(specimenRepository).save(captor.capture());
        assertThat(captor.getValue().getPatientId()).isEqualTo(ORDER_PATIENT_ID);
    }
}
