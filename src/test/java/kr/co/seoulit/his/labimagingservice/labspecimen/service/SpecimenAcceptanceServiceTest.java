package kr.co.seoulit.his.labimagingservice.labspecimen.service;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.common.validation.DateTimeValidator;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabReceptionEntity;
import kr.co.seoulit.his.labimagingservice.labspecimen.dto.SpecimenAcceptanceRequestDto;
import kr.co.seoulit.his.labimagingservice.labspecimen.dto.SpecimenAcceptanceSummaryDto;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.FitnessStatus;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenAcceptanceEntity;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenEntity;
import kr.co.seoulit.his.labimagingservice.labspecimen.mapper.SpecimenAcceptanceMapper;
import kr.co.seoulit.his.labimagingservice.labspecimen.repository.SpecimenAcceptanceRepository;
import kr.co.seoulit.his.labimagingservice.labspecimen.repository.SpecimenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 검체 인수 + 적합성 판정 — DateTimeValidator 연동(LAB107/108, 04번 지시서 Phase 3-B).
 * 모드별 동작 자체(허용 오차, strict 토글)는 DateTimeValidatorTest 가 담당한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SpecimenAcceptanceServiceTest {

    private static final String SPECIMEN_ID = "sp-1";

    @Mock SpecimenAcceptanceRepository specimenAcceptanceRepository;
    @Mock SpecimenRepository specimenRepository;
    @Mock SpecimenAcceptanceMapper specimenAcceptanceMapper;
    @Mock CommonCodeCache commonCodeCache;
    @Mock DateTimeValidator dateTimeValidator;

    @InjectMocks SpecimenAcceptanceService service;

    private SpecimenEntity specimen;
    private LabReceptionEntity reception;

    @BeforeEach
    void setUp() {
        reception = mock(LabReceptionEntity.class);

        specimen = mock(SpecimenEntity.class);
        when(specimen.getCollectedAt()).thenReturn(LocalDateTime.now().minusHours(1));
        when(specimen.getLabReception()).thenReturn(reception);
        when(specimenRepository.findById(SPECIMEN_ID)).thenReturn(Optional.of(specimen));
        when(specimenAcceptanceRepository.existsBySpecimen_SpecimenId(SPECIMEN_ID)).thenReturn(false);
        when(commonCodeCache.isValid(anyString(), anyString())).thenReturn(true);
        when(specimenAcceptanceRepository.save(any(SpecimenAcceptanceEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(specimenAcceptanceMapper.toResponse(any())).thenReturn(mock(SpecimenAcceptanceSummaryDto.class));
    }

    private static SpecimenAcceptanceRequestDto fitRequest(LocalDateTime acceptedAt) {
        return SpecimenAcceptanceRequestDto.builder()
                .acceptedAt(acceptedAt)
                .acceptedById("emp-1")
                .fitnessStatus(FitnessStatus.FIT)
                .recollectionRequestedYn("N")
                .build();
    }

    @Test
    @DisplayName("정상 인수 — DateTimeValidator 두 검사를 모두 거치고 저장된다")
    void acceptsWhenDateTimeValid() {
        LocalDateTime acceptedAt = LocalDateTime.now();

        assertThatCode(() -> service.acceptSpecimen(SPECIMEN_ID, fitRequest(acceptedAt)))
                .doesNotThrowAnyException();

        LocalDateTime collectedAt = specimen.getCollectedAt();
        verify(dateTimeValidator).rejectIfFuture(acceptedAt, "acceptedAt");
        verify(dateTimeValidator).rejectIfEarlierThan(acceptedAt, collectedAt, "acceptedAt", "collectedAt");
        verify(specimenAcceptanceRepository).save(any());
    }

    @Test
    @DisplayName("인수일시가 미래면(DateTimeValidator 가 LAB107을 던지면) 저장하지 않는다")
    void rejectsFutureAcceptedAt() {
        Mockito.doThrow(new LabImagingBusinessException(LabMessageCode.LAB107, "미래 시각/일자는 입력할 수 없습니다."))
                .when(dateTimeValidator).rejectIfFuture(any(LocalDateTime.class), eq("acceptedAt"));

        assertThatThrownBy(() -> service.acceptSpecimen(SPECIMEN_ID, fitRequest(LocalDateTime.now())))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB107);
        verify(specimenAcceptanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("인수일시가 채취일시보다 빠르면(DateTimeValidator 가 LAB108을 던지면) 저장하지 않는다")
    void rejectsAcceptedBeforeCollected() {
        Mockito.doThrow(new LabImagingBusinessException(LabMessageCode.LAB108, "시각 순서가 올바르지 않습니다."))
                .when(dateTimeValidator).rejectIfEarlierThan(
                        any(LocalDateTime.class), any(LocalDateTime.class), anyString(), anyString());

        assertThatThrownBy(() -> service.acceptSpecimen(SPECIMEN_ID, fitRequest(LocalDateTime.now())))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB108);
        verify(specimenAcceptanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 검체는 LAB020")
    void specimenNotFound() {
        when(specimenRepository.findById("no-such-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acceptSpecimen("no-such-id", fitRequest(LocalDateTime.now())))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB020);
    }

    @Test
    @DisplayName("이미 인수/판정이 완료된 검체는 LAB022")
    void alreadyAccepted() {
        when(specimenAcceptanceRepository.existsBySpecimen_SpecimenId(SPECIMEN_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.acceptSpecimen(SPECIMEN_ID, fitRequest(LocalDateTime.now())))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB022);
    }

    @Test
    @DisplayName("취소된 접수의 검체면 LAB121 (05번 지시서 Phase 3)")
    void rejectsCancelledReception() {
        Mockito.doThrow(new LabImagingBusinessException(LabMessageCode.LAB121, "취소된 접수입니다."))
                .when(reception).requireNotCancelled();

        assertThatThrownBy(() -> service.acceptSpecimen(SPECIMEN_ID, fitRequest(LocalDateTime.now())))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB121);
        verify(specimenAcceptanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("부적합인데 사유가 없으면 LAB998")
    void unfitWithoutReasonRejected() {
        SpecimenAcceptanceRequestDto request = SpecimenAcceptanceRequestDto.builder()
                .acceptedAt(LocalDateTime.now())
                .fitnessStatus(FitnessStatus.UNFIT)
                .recollectionRequestedYn("Y")
                .build();

        assertThatThrownBy(() -> service.acceptSpecimen(SPECIMEN_ID, request))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB998);
    }
}
