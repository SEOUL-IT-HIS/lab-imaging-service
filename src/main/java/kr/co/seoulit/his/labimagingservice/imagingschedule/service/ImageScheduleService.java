package kr.co.seoulit.his.labimagingservice.imagingschedule.service;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
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

@Service
@RequiredArgsConstructor
public class ImageScheduleService {

    private final ImageScheduleRepository imageScheduleRepository;
    private final ImageReceptionRepository imageReceptionRepository;
    private final ImageScheduleMapper imageScheduleMapper;

    @Transactional
    public ImageScheduleResponseDto createImageSchedule(ImageScheduleCreateRequestDto request) {
        ImageReceptionEntity reception = imageReceptionRepository.findById(request.getImageReceptionId())
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB015, "영상접수 정보를 찾을 수 없습니다."));

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
}
