package kr.co.seoulit.his.labimagingservice.labschedule.service;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.common.validation.DateTimeValidator;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabReceptionEntity;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabReceptionRepository;
import kr.co.seoulit.his.labimagingservice.labschedule.dto.LabScheduleCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.labschedule.dto.LabScheduleRescheduleRequestDto;
import kr.co.seoulit.his.labimagingservice.labschedule.dto.LabScheduleResponseDto;
import kr.co.seoulit.his.labimagingservice.labschedule.entity.LabScheduleEntity;
import kr.co.seoulit.his.labimagingservice.labschedule.mapper.LabScheduleMapper;
import kr.co.seoulit.his.labimagingservice.labschedule.repository.LabScheduleRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 검사 일정 등록/재조정 — DateTimeValidator 연동(LAB116, 04번 지시서 Phase 3-B).
 * 모드별 동작 자체(날짜만 비교, strict 토글)는 DateTimeValidatorTest 가 담당한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LabScheduleServiceTest {

    private static final String RECEPTION_ID = "rec-1";

    @Mock LabScheduleRepository labScheduleRepository;
    @Mock LabReceptionRepository labReceptionRepository;
    @Mock LabScheduleMapper labScheduleMapper;
    @Mock DateTimeValidator dateTimeValidator;

    @InjectMocks LabScheduleService service;

    @BeforeEach
    void setUp() {
        LabReceptionEntity reception = mock(LabReceptionEntity.class);
        when(reception.getReceptionNo()).thenReturn("LR-1");
        when(labReceptionRepository.findById(RECEPTION_ID)).thenReturn(Optional.of(reception));
        when(labScheduleRepository.findByLabReception_LabReceptionIdAndLatestYn(RECEPTION_ID, "Y"))
                .thenReturn(Optional.empty());
        when(labScheduleRepository.save(any(LabScheduleEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(labScheduleRepository.saveAndFlush(any(LabScheduleEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(labScheduleMapper.toResponse(any())).thenReturn(mock(LabScheduleResponseDto.class));
    }

    @Test
    @DisplayName("등록 — scheduledAt을 DateTimeValidator.rejectIfPastDate 로 검증하고, 통과하면 저장된다")
    void createChecksPastDateAndSaves() {
        LocalDateTime scheduledAt = LocalDateTime.now().plusDays(1);
        LabScheduleCreateRequestDto request = LabScheduleCreateRequestDto.builder()
                .labReceptionId(RECEPTION_ID).scheduledAt(scheduledAt).reservationYn("N").build();

        assertThatCode(() -> service.createLabSchedule(request)).doesNotThrowAnyException();

        verify(dateTimeValidator).rejectIfPastDate(scheduledAt, "scheduledAt");
        verify(labScheduleRepository).save(any());
    }

    @Test
    @DisplayName("등록 — 과거 날짜면(DateTimeValidator 가 LAB116을 던지면) 저장하지 않는다")
    void createRejectsPastDate() {
        Mockito.doThrow(new LabImagingBusinessException(LabMessageCode.LAB116, "과거 날짜로 일정을 등록할 수 없습니다."))
                .when(dateTimeValidator).rejectIfPastDate(any(LocalDateTime.class), eq("scheduledAt"));

        LabScheduleCreateRequestDto request = LabScheduleCreateRequestDto.builder()
                .labReceptionId(RECEPTION_ID).scheduledAt(LocalDateTime.now().minusDays(1)).reservationYn("N").build();

        assertThatThrownBy(() -> service.createLabSchedule(request))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB116);
        verify(labScheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("재조정 — scheduledAt을 검증하고, 과거 날짜면 기존 일정의 latest_yn 전환도 일어나지 않는다")
    void rescheduleRejectsPastDateBeforeMarkingNotLatest() {
        LabScheduleEntity current = mock(LabScheduleEntity.class);
        when(labScheduleRepository.findByLabReception_LabReceptionIdAndLatestYn(RECEPTION_ID, "Y"))
                .thenReturn(Optional.of(current));
        Mockito.doThrow(new LabImagingBusinessException(LabMessageCode.LAB116, "과거 날짜로 일정을 등록할 수 없습니다."))
                .when(dateTimeValidator).rejectIfPastDate(any(LocalDateTime.class), eq("scheduledAt"));

        LabScheduleRescheduleRequestDto request = LabScheduleRescheduleRequestDto.builder()
                .scheduledAt(LocalDateTime.now().minusDays(1)).reservationYn("N").build();

        assertThatThrownBy(() -> service.createLabReschedule(RECEPTION_ID, request))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB116);
        verify(current, never()).markAsNotLatest();
        verify(labScheduleRepository, never()).saveAndFlush(any());
    }
}
