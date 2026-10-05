package kr.co.seoulit.his.labimagingservice.imagingschedule.service;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.common.validation.DateTimeValidator;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageOrderEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageReceptionEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.repository.ImageOrderItemRepository;
import kr.co.seoulit.his.labimagingservice.imagingorder.repository.ImageReceptionRepository;
import kr.co.seoulit.his.labimagingservice.imagingschedule.dto.ImageScheduleCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingschedule.dto.ImageScheduleResponseDto;
import kr.co.seoulit.his.labimagingservice.imagingschedule.entity.ImageScheduleEntity;
import kr.co.seoulit.his.labimagingservice.imagingschedule.mapper.ImageScheduleMapper;
import kr.co.seoulit.his.labimagingservice.imagingschedule.repository.ImageScheduleRepository;
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
import java.util.List;
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
 * 영상 촬영 일정 등록/재조정 — DateTimeValidator 연동(LAB116, 04번 지시서 Phase 3-B).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImageScheduleServiceTest {

    private static final String RECEPTION_ID = "irec-1";
    private static final String ITEM_ID = "item-1";

    @Mock ImageScheduleRepository imageScheduleRepository;
    @Mock ImageReceptionRepository imageReceptionRepository;
    @Mock ImageScheduleMapper imageScheduleMapper;
    @Mock ImageOrderItemRepository imageOrderItemRepository;
    @Mock CommonCodeCache commonCodeCache;
    @Mock DateTimeValidator dateTimeValidator;

    @InjectMocks ImageScheduleService service;

    @BeforeEach
    void setUp() {
        ImageOrderItemEntity item = mock(ImageOrderItemEntity.class);
        when(item.getImageOrderItemId()).thenReturn(ITEM_ID);

        ImageOrderEntity order = mock(ImageOrderEntity.class);
        when(order.getOrderItems()).thenReturn(List.of(item));

        ImageReceptionEntity reception = mock(ImageReceptionEntity.class);
        when(reception.getImageOrder()).thenReturn(order);
        when(imageReceptionRepository.findById(RECEPTION_ID)).thenReturn(Optional.of(reception));

        when(imageScheduleRepository
                .findByImageReception_ImageReceptionIdAndImageOrderItem_ImageOrderItemIdAndLatestYn(
                        RECEPTION_ID, ITEM_ID, "Y"))
                .thenReturn(Optional.empty());
        when(commonCodeCache.isValid(anyString(), anyString())).thenReturn(true);
        when(imageScheduleRepository.save(any(ImageScheduleEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(imageScheduleRepository.saveAndFlush(any(ImageScheduleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(imageScheduleMapper.toResponse(any())).thenReturn(mock(ImageScheduleResponseDto.class));
    }

    private static ImageScheduleCreateRequestDto.ImageScheduleCreateRequestDtoBuilder createRequest(
            LocalDateTime scheduledAt) {
        return ImageScheduleCreateRequestDto.builder()
                .imageReceptionId(RECEPTION_ID)
                .imageOrderItemId(ITEM_ID)
                .roomCode("01")
                .equipmentCode("01")
                .scheduledAt(scheduledAt)
                .reservationYn("N")
                .contraindicationCheckCode("01");
    }

    @Test
    @DisplayName("등록 — scheduledAt을 DateTimeValidator.rejectIfPastDate 로 검증하고, 통과하면 저장된다")
    void createChecksPastDateAndSaves() {
        LocalDateTime scheduledAt = LocalDateTime.now().plusDays(1);

        assertThatCode(() -> service.createImageSchedule(createRequest(scheduledAt).build()))
                .doesNotThrowAnyException();

        verify(dateTimeValidator).rejectIfPastDate(scheduledAt, "scheduledAt");
        verify(imageScheduleRepository).save(any());
    }

    @Test
    @DisplayName("등록 — 과거 날짜면(DateTimeValidator 가 LAB116을 던지면) 저장하지 않는다")
    void createRejectsPastDate() {
        Mockito.doThrow(new LabImagingBusinessException(LabMessageCode.LAB116, "과거 날짜로 일정을 등록할 수 없습니다."))
                .when(dateTimeValidator).rejectIfPastDate(any(LocalDateTime.class), eq("scheduledAt"));

        assertThatThrownBy(() -> service.createImageSchedule(
                createRequest(LocalDateTime.now().minusDays(1)).build()))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB116);
        verify(imageScheduleRepository, never()).save(any());
    }
}
