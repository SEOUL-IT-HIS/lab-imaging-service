package kr.co.seoulit.his.labimagingservice.imagingschedule.service;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageReceptionEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.repository.ImageOrderItemRepository;
import kr.co.seoulit.his.labimagingservice.imagingorder.repository.ImageReceptionRepository;
import kr.co.seoulit.his.labimagingservice.imagingschedule.dto.ImageScheduleItemDto;
import kr.co.seoulit.his.labimagingservice.imagingschedule.dto.ImageScheduleCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingschedule.dto.ImageScheduleRescheduleRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingschedule.dto.ImageScheduleResponseDto;
import kr.co.seoulit.his.labimagingservice.imagingschedule.entity.ImageScheduleEntity;
import kr.co.seoulit.his.labimagingservice.imagingschedule.mapper.ImageScheduleMapper;
import kr.co.seoulit.his.labimagingservice.imagingschedule.repository.ImageScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    /** 최종(현재 유효) 일정 판별값. IMAGE_SCHEDULE.latest_yn */
    private static final String LATEST = "Y";

    private final ImageScheduleRepository imageScheduleRepository;
    private final ImageReceptionRepository imageReceptionRepository;
    private final ImageScheduleMapper imageScheduleMapper;
    private final ImageOrderItemRepository imageOrderItemRepository;
    private final CommonCodeCache commonCodeCache;

    @Transactional
    public ImageScheduleResponseDto createImageSchedule(ImageScheduleCreateRequestDto request) {
        ImageReceptionEntity reception = imageReceptionRepository.findById(request.getImageReceptionId())
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB015, "영상 촬영 접수 정보를 찾을 수 없습니다."));

        /*
         * 이미 최종(latest_yn=Y) 일정이 있으면 신규 등록이 아니라 재등록 대상이다.
         *
         * ⚠ 이 확인이 없으면 아래 INSERT 가 조건부 UNIQUE(UX_ISCH_LATEST)에 걸려
         *   DataIntegrityViolationException 이 LAB999(500)로 나간다.
         *   화면에는 "처리 중 오류가 발생했습니다" 만 떠서 담당자가 원인을 알 수 없다.
         *   (검사 쪽 LabScheduleService 에는 처음부터 있던 가드인데 영상에만 빠져 있었다 — 2026-09-03)
         */
        ImageOrderItemEntity orderItem = findOrderItemOfReception(reception, request.getImageOrderItemId());

        if (imageScheduleRepository
                .findByImageReception_ImageReceptionIdAndImageOrderItem_ImageOrderItemIdAndLatestYn(
                        request.getImageReceptionId(), request.getImageOrderItemId(), LATEST)
                .isPresent()) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB046,
                    "이미 등록된 영상 일정이 있습니다. 재등록을 사용하세요. (촬영항목코드="
                            + orderItem.getImageItemCode() + ")");
        }

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
        schedule.assignImageOrderItem(orderItem);

        ImageScheduleEntity saved = imageScheduleRepository.save(schedule);
        return imageScheduleMapper.toResponse(saved);
    }

    @Transactional
    public ImageScheduleResponseDto createImageReschedule(String imageReceptionId, ImageScheduleRescheduleRequestDto request) {
        /*
         * ⚠ 접수ID 만으로 찾으면 안 된다. 일정이 항목 단위라 접수 하나에 최종 일정이 여러 건이다.
         *   항목ID까지 넣어야 재조정할 대상 1건이 정해진다. (2026-09-03)
         */
        ImageScheduleEntity current = imageScheduleRepository
                .findByImageReception_ImageReceptionIdAndImageOrderItem_ImageOrderItemIdAndLatestYn(
                        imageReceptionId, request.getImageOrderItemId(), LATEST)
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB016,
                        "재등록할 기존 영상 일정이 없습니다. (촬영항목ID=" + request.getImageOrderItemId() + ")"));

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
        // 재조정본도 같은 촬영항목에 붙는다. 항목이 바뀌는 건 재조정이 아니라 다른 일정이다.
        reschedule.assignImageOrderItem(current.getImageOrderItem());

        ImageScheduleEntity saved = imageScheduleRepository.save(reschedule);
        return imageScheduleMapper.toResponse(saved);
    }

    /**
     * 접수 1건의 촬영항목 목록을 최종 일정과 함께 조회한다. (일정 등록 화면용)
     *
     * ⚠ 일정이 없는 항목도 빠뜨리지 않고 담는다. 등록 대상이 바로 그 항목들이다.
     *   일정 테이블에서 시작해 조회하면 아직 안 잡힌 항목이 목록에서 사라진다.
     *   그래서 촬영항목을 기준으로 뽑고 일정을 붙이는 방향으로 조립한다.
     *   (검사결과 등록 화면 LabResultService.getResultItemsByReceptionNo 와 같은 구조)
     *
     * ⚠ 일정은 접수+항목 조합의 최종본(latest_yn=Y)만 붙인다. 재조정 이력은 여기서 보여주지 않는다.
     */
    @Transactional(readOnly = true)
    public List<ImageScheduleItemDto> getScheduleItemsByReceptionNo(String receptionNo) {

        ImageReceptionEntity reception = imageReceptionRepository.findByReceptionNo(receptionNo)
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB015,
                        "영상 촬영 접수 정보를 찾을 수 없습니다. (receptionNo=" + receptionNo + ")"));

        List<ImageOrderItemEntity> items =
                imageOrderItemRepository.findByReceptionNo(receptionNo);
        if (items.isEmpty()) {
            // 접수는 있는데 항목이 없는 경우다. 빈 목록이 정상이므로 예외를 던지지 않는다.
            return List.of();
        }

        Map<String, ImageScheduleEntity> scheduleByItemId = imageScheduleRepository
                .findByImageReception_ImageReceptionIdAndLatestYn(
                        reception.getImageReceptionId(), LATEST).stream()
                .collect(Collectors.toMap(
                        schedule -> schedule.getImageOrderItem().getImageOrderItemId(),
                        schedule -> schedule));

        return items.stream()
                .map(item -> {
                    ImageScheduleEntity schedule = scheduleByItemId.get(item.getImageOrderItemId());
                    return ImageScheduleItemDto.builder()
                            .imageOrderItemId(item.getImageOrderItemId())
                            .imageItemCode(item.getImageItemCode())
                            // 일정이 없는 항목은 null 로 둔다. 화면이 "미등록"으로 읽는다.
                            .schedule(schedule == null ? null : imageScheduleMapper.toResponse(schedule))
                            .build();
                })
                .toList();
    }

    /**
     * 촬영항목이 그 접수의 오더에 실제로 속하는지 확인한다.
     *
     * ⚠ 항목ID를 그대로 믿으면 다른 오더의 촬영항목에 일정을 붙일 수 있다.
     *   그러면 어느 접수에서도 보이지 않는 일정이 생긴다. FK 만으로는 못 막는다 —
     *   IMAGE_ORDER_ITEM 이 존재하기만 하면 FK 는 통과하기 때문이다.
     */
    private ImageOrderItemEntity findOrderItemOfReception(ImageReceptionEntity reception,
                                                          String imageOrderItemId) {
        return reception.getImageOrder().getOrderItems().stream()
                .filter(item -> item.getImageOrderItemId().equals(imageOrderItemId))
                .findFirst()
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB047,
                        "이 접수의 촬영항목이 아닙니다. (imageOrderItemId=" + imageOrderItemId + ")"));
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
