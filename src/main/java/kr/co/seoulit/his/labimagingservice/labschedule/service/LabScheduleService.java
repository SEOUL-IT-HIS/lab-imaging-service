package kr.co.seoulit.his.labimagingservice.labschedule.service;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabReceptionEntity;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabReceptionRepository;
import kr.co.seoulit.his.labimagingservice.labschedule.dto.LabScheduleRescheduleRequestDto;
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
                        LabMessageCode.LAB013, "검사접수 정보를 찾을 수 없습니다."));

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
    public LabScheduleResponseDto createLabReschedule(String labReceptionId, LabScheduleRescheduleRequestDto request) {
        LabScheduleEntity current = labScheduleRepository
                .findByLabReception_LabReceptionIdAndLatestYn(labReceptionId, "Y")
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB014, "재등록할 기존 검사 일정이 없습니다."));
        // 기존 "최종본" 일정(latest_yn='Y')의 상태를 도메인 메서드로 N으로 전환한다.
// (Entity 내부에 캡슐화된 메서드 — 필드를 밖에서 직접 setLatestYn("N")으로 바꾸지 않고
//  "최종본에서 내려온다"는 업무 의미가 드러나는 이름의 메서드를 통해 변경한다)
// 이 시점엔 아직 메모리(영속성 컨텍스트)상의 값만 바뀐 상태이고, DB에는 반영되지 않았다.
        current.markAsNotLatest();

// 방금 바꾼 변경사항(latest_yn: Y→N)을 DB에 "즉시" 반영(UPDATE 실행)한다.
// save()만 쓰면 Hibernate가 내부적으로 INSERT를 UPDATE보다 먼저 내보내는 순서를 따르기 때문에,
// 아래에서 새 일정을 INSERT할 때 "기존 행도 Y, 새 행도 Y"인 순간이 생겨
// latest_yn='Y' 조건부 UNIQUE 제약(UX_LSCH_LATEST)을 위반하게 된다.
// saveAndFlush()는 그 UPDATE 문을 여기서 바로 DB로 보내 반영시켜서,
// 뒤이어 나갈 INSERT보다 반드시 먼저 실행되도록 순서를 강제한다.
        labScheduleRepository.saveAndFlush(current);

        LabReceptionEntity reception = current.getLabReception();
        LabScheduleEntity reschedule = LabScheduleEntity.builder()
                .scheduleTypeCode(current.getScheduleTypeCode())
                .scheduledAt(request.getScheduledAt())
                .reservationYn(request.getReservationYn())
                .guidanceNote(request.getGuidanceNote())
                .confirmedById(request.getConfirmedById())
                .latestYn("Y")
                .build();
        reception.addSchedule(reschedule);

        LabScheduleEntity saved = labScheduleRepository.save(reschedule);
        return labScheduleMapper.toResponse(saved);
        }
    }

