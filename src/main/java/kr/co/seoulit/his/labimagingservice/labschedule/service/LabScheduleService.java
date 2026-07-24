package kr.co.seoulit.his.labimagingservice.labschedule.service;

import jakarta.validation.Valid;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabReceptionEntity;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabReceptionRepository;
import kr.co.seoulit.his.labimagingservice.labschedule.dto.LabRescheduleRequestDto;
import kr.co.seoulit.his.labimagingservice.labschedule.dto.LabScheduleCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.labschedule.dto.LabScheduleResponseDto;
import kr.co.seoulit.his.labimagingservice.labschedule.entity.LabScheduleEntity;
import kr.co.seoulit.his.labimagingservice.labschedule.mapper.LabScheduleMapper;
import kr.co.seoulit.his.labimagingservice.labschedule.repository.LabScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LabScheduleService {

    private final LabScheduleRepository labScheduleRepository;
    private final LabReceptionRepository labReceptionRepository;
    private final LabScheduleMapper labScheduleMapper;

    @Transactional
    public LabScheduleResponseDto createLabSchedule(LabScheduleCreateRequestDto request) {
        LabReceptionEntity reception = labReceptionRepository.findById(request.getLabReceptionId())
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB011, "접수 정보를 찾을 수 없습니다."));

        LabScheduleEntity schedule = LabScheduleEntity.builder()
                .scheduleTypeCode(request.getScheduleTypeCode())
                .scheduledAt(request.getScheduledAt())
                .reservationYn(request.getReservationYn())
                .guidanceNote(request.getGuidanceNote())
                .confirmedById(request.getConfirmedById())
                .latestYn("Y")
                .build();
        reception.addSchedule(schedule);

        LabScheduleEntity saved = labScheduleRepository.save(schedule);
        return labScheduleMapper.toResponse(saved);
        }

    @Transactional
    public LabScheduleResponseDto createLabReschedule(String labReceptionId, LabRescheduleRequestDto request) {
        LabScheduleEntity current = labScheduleRepository
                .findByLabReception_LabReceptionIdAndLatestYn(labReceptionId, "Y")
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB012, "재등록할 기존 일정이 없습니다."));
        current.markAsNotLatest();

        LabReceptionEntity reception = current.getLabReception();
        LabScheduleEntity next = LabScheduleEntity.builder()
                .scheduleTypeCode(current.getScheduleTypeCode())
                .scheduledAt(request.getScheduledAt())
                .reservationYn(request.getReservationYn())
                .guidanceNote(request.getGuidanceNote())
                .confirmedById(request.getConfirmedById())
                .latestYn("Y")
                .build();
        reception.addSchedule(next);
        LabScheduleEntity saved = labScheduleRepository.save(next);
        return labScheduleMapper.toResponse(saved);
        }
    }

