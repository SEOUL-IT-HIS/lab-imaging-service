package kr.co.seoulit.his.labimagingservice.imagingschedule.service;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageReceptionEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.repository.ImageReceptionRepository;
import kr.co.seoulit.his.labimagingservice.imagingschedule.dto.ImageScheduleCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingschedule.dto.ImageScheduleRescheduleRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingschedule.dto.ImageScheduleResponseDto;
import kr.co.seoulit.his.labimagingservice.imagingschedule.entity.ImageScheduleEntity;
import kr.co.seoulit.his.labimagingservice.imagingschedule.mapper.ImageScheduleMapper;
import kr.co.seoulit.his.labimagingservice.imagingschedule.repository.ImageScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 영상 촬영 일정 서비스
 *
 * ⚠ 공통코드 검증(2026-08-04 연결): roomCode/equipmentCode/contraindicationCheckCode 를
 *   admin 실시간 조회가 아니라 CommonCodeCache(로컬 캐시)로 확인한다.
 *   신규 등록과 재조정 양쪽 모두 동일하게 검증한다 — 재조정도 세 코드를 다시 받기 때문이다.
 */
@Service
@RequiredArgsConstructor
public class ImageScheduleService {

    private final ImageScheduleRepository imageScheduleRepository;
    private final ImageReceptionRepository imageReceptionRepository;
    private final ImageScheduleMapper imageScheduleMapper;
    private final CommonCodeCache commonCodeCache;

    @Transactional
    public ImageScheduleResponseDto createImageSchedule(ImageScheduleCreateRequestDto request) {
        ImageReceptionEntity reception = imageReceptionRepository.findById(request.getImageReceptionId())
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB015, "영상 촬영 접수 정보를 찾을 수 없습니다."));

        validateScheduleCodes(request.getRoomCode(), request.getEquipmentCode(),
                request.getContraindicationCheckCode());

        ImageScheduleEntity schedule = ImageScheduleEntity.builder()
                .roomCode(request.getRoomCode())
                .equipmentCode(request.getEquipmentCode())
                .scheduledAt(request.getScheduledAt())
                .reservationYn(request.getReservationYn())
                .contraindicationCheckCode(request.getContraindicationCheckCode())
                .contraindicationNote(request.getContraindicationNote())
                .confirmedById(request.getConfirmedById())
                .latestYn("Y")
                .build();
        reception.addSchedule(schedule);

        ImageScheduleEntity saved = imageScheduleRepository.save(schedule);
        return imageScheduleMapper.toResponse(saved);
    }

    @Transactional
    public ImageScheduleResponseDto createImageReschedule(String imageReceptionId, ImageScheduleRescheduleRequestDto request) {
        ImageScheduleEntity current = imageScheduleRepository
                .findByImageReception_ImageReceptionIdAndLatestYn(imageReceptionId, "Y")
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB016, "재등록할 기존 영상 일정이 없습니다."));

        // 기존 일정을 내리기 전에 검증한다. 검증에서 실패하면 latest_yn 전환도 일어나면 안 된다.
        validateScheduleCodes(request.getRoomCode(), request.getEquipmentCode(),
                request.getContraindicationCheckCode());

        current.markAsNotLatest();

        imageScheduleRepository.saveAndFlush(current);

        ImageReceptionEntity reception = current.getImageReception();
        ImageScheduleEntity reschedule = ImageScheduleEntity.builder()
                .roomCode(request.getRoomCode())
                .equipmentCode(request.getEquipmentCode())
                .scheduledAt(request.getScheduledAt())
                .reservationYn(request.getReservationYn())
                .contraindicationCheckCode(request.getContraindicationCheckCode())
                .contraindicationNote(request.getContraindicationNote())
                .confirmedById(request.getConfirmedById())
                .latestYn("Y")
                .build();
        reception.addSchedule(reschedule);

        ImageScheduleEntity saved = imageScheduleRepository.save(reschedule);
        return imageScheduleMapper.toResponse(saved);
    }

    /** 일정 등록/재조정이 공통으로 받는 세 코드값을 공통코드 캐시로 검증한다. */
    private void validateScheduleCodes(String roomCode, String equipmentCode,
                                       String contraindicationCheckCode) {
        validateCode("EXAM_ROOM_CD", roomCode, "영상촬영실코드");
        validateCode("EQUIPMENT_CD", equipmentCode, "촬영장비코드");

        // ⚠ 금기확인결과코드는 필수값이라 null-skip 하지 않는다.
        //   (DTO @NotBlank + 컬럼 nullable=false — nullable 인 건 금기사항 "메모"(contraindicationNote) 쪽이다)
        //   null 이 들어오면 isValid 가 false 를 돌려주므로 여기서도 LAB017 로 걸린다.
        validateCode("CONTRAINDICATION_CD", contraindicationCheckCode, "금기확인결과코드");
    }

    /**
     * 공통코드 캐시로 코드값을 검증하고, 유효하지 않으면 LAB017로 실패시킨다.
     * (상세 주석은 LabOrderService.validateCode 참고)
     */
    private void validateCode(String groupCode, String code, String fieldLabel) {
        if (!commonCodeCache.isValid(groupCode, code)) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB017,
                    "유효하지 않은 " + fieldLabel + "입니다. (" + groupCode + "=" + code + ")"
            );
        }
    }
}
