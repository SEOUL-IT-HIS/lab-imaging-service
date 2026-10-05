package kr.co.seoulit.his.labimagingservice.imaginginterpretation.service;

import kr.co.seoulit.his.labimagingservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.labimagingservice.common.cache.StaffDirectoryCache;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.common.session.StaffValidator;
import kr.co.seoulit.his.labimagingservice.imaginginterpretation.dto.ImageReadingSummaryDto;
import kr.co.seoulit.his.labimagingservice.imaginginterpretation.entity.ImageReadingEntity;
import kr.co.seoulit.his.labimagingservice.imaginginterpretation.mapper.ImageReadingMapper;
import kr.co.seoulit.his.labimagingservice.imaginginterpretation.repository.ImageReadingRepository;
import kr.co.seoulit.his.labimagingservice.imagingorder.repository.ImageOrderItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
 * ImageReadingService.assignReading — StaffValidator 연동 확인. (04번 지시서 Phase 2-B/2-E)
 *
 * ⚠ 의사 검증 자체(모드별 동작)는 StaffValidatorTest 에서 이미 검증했다. 여기서는
 *   "assignReading 이 검증을 실제로 거치는지", "ENFORCE 거절 시 담당자 배정(reading.assign)이
 *   일어나지 않는지"만 확인한다 — 중복 검증을 피한다.
 */
class ImageReadingServiceTest {

    private final ImageReadingRepository imageReadingRepository = mock(ImageReadingRepository.class);
    private final ImageOrderItemRepository imageOrderItemRepository = mock(ImageOrderItemRepository.class);
    private final ImageReadingMapper imageReadingMapper = mock(ImageReadingMapper.class);
    private final CommonCodeCache commonCodeCache = mock(CommonCodeCache.class);
    private final StaffDirectoryCache staffDirectoryCache = mock(StaffDirectoryCache.class);

    private ImageReadingEntity reading;

    @BeforeEach
    void setUp() {
        reading = mock(ImageReadingEntity.class);
        when(reading.getReadingStatusCode()).thenReturn("02");
        when(imageReadingRepository.findById("reading-1")).thenReturn(Optional.of(reading));
        when(commonCodeCache.isValid(any(), any())).thenReturn(true);
        when(imageReadingMapper.toResponse(reading)).thenReturn(mock(ImageReadingSummaryDto.class));
        when(staffDirectoryCache.isAvailable()).thenReturn(true);
    }

    private ImageReadingService service(StaffValidator.Mode mode) {
        StaffValidator staffValidator = new StaffValidator(staffDirectoryCache, mode, StaffValidator.UnavailablePolicy.ALLOW);
        return new ImageReadingService(
                imageReadingRepository, imageOrderItemRepository, imageReadingMapper, commonCodeCache, staffValidator);
    }

    @Test
    @DisplayName("ENFORCE 모드에서 의사가 아닌 담당자를 배정하면 거절하고 reading.assign은 호출되지 않는다")
    void enforceModeRejectsNonDoctorAssignment() {
        when(staffDirectoryCache.isDoctor("nurse-emp")).thenReturn(false);

        assertThatThrownBy(() -> service(StaffValidator.Mode.ENFORCE).assignReading("reading-1", "nurse-emp"))
                .isInstanceOf(LabImagingBusinessException.class);

        verify(reading, never()).assign(any(), any(), any());
    }

    @Test
    @DisplayName("WARN 모드에서는 의사가 아닌 담당자도 배정된다(막지 않는다)")
    void warnModePassesNonDoctorAssignment() {
        when(staffDirectoryCache.isDoctor("nurse-emp")).thenReturn(false);

        assertThatCode(() -> service(StaffValidator.Mode.WARN).assignReading("reading-1", "nurse-emp"))
                .doesNotThrowAnyException();

        verify(reading).assign(eq("nurse-emp"), any(), eq("02"));
    }

    @Test
    @DisplayName("ENFORCE 모드에서 의사인 담당자는 정상 배정된다")
    void enforceModePassesDoctorAssignment() {
        when(staffDirectoryCache.isDoctor("doctor-emp")).thenReturn(true);

        assertThatCode(() -> service(StaffValidator.Mode.ENFORCE).assignReading("reading-1", "doctor-emp"))
                .doesNotThrowAnyException();

        verify(reading).assign(eq("doctor-emp"), any(), eq("02"));
    }
}
