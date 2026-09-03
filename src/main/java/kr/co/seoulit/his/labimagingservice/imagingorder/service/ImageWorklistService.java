package kr.co.seoulit.his.labimagingservice.imagingorder.service;

import kr.co.seoulit.his.labimagingservice.common.status.ReceptionStatus;
import kr.co.seoulit.his.labimagingservice.imagingacquisition.repository.ConsentRepository;
import kr.co.seoulit.his.labimagingservice.imagingorder.dto.ImageWorklistItemDto;
import kr.co.seoulit.his.labimagingservice.imagingorder.dto.ImageWorklistStep;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageReceptionEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.repository.ImageOrderItemRepository;
import kr.co.seoulit.his.labimagingservice.imagingorder.mapper.ImageWorklistMapper;
import kr.co.seoulit.his.labimagingservice.imagingorder.repository.ImageReceptionRepository;
import kr.co.seoulit.his.labimagingservice.imagingschedule.entity.ImageScheduleEntity;
import kr.co.seoulit.his.labimagingservice.imagingschedule.repository.ImageScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 영상 워크리스트 조회 서비스.
 * 검사 쪽 LabWorklistService 와 같은 자리, 같은 조립 방식이고 단계 구성만 다르다.
 *
 * ⚠ ImageOrderService 가 아니라 별도 서비스로 둔 이유 —
 *   워크리스트는 접수 하나만 보는 게 아니라 일정·동의를 가로질러 "어디까지 진행됐는지"를
 *   조립하는 조회다. 접수 등록/조회를 담당하는 ImageOrderService 에 넣으면 그 클래스가
 *   다른 도메인 리포지토리를 여러 개 들고 있게 된다.
 *
 * ⚠ 쿼리 수는 접수가 몇 건이든 항상 3번이다. (접수 1 + 일정 1 + 동의 1)
 *   행마다 조회하면 N+1 이 되므로, 목록을 먼저 뽑고 ID 를 모아 IN 절로 일괄 조회한 뒤
 *   메모리에서 붙인다.
 *
 * ⚠ left join 한 방으로 가져오지 않는 이유 —
 *   CONSENT 는 오더와 1:N 이라 동의 3건인 오더가 결과에서 3행으로 늘어난다.
 *   (검사에서 SPECIMEN 때문에 피했던 것과 같은 문제)
 *
 * ── 아직 계산하지 않는 단계
 *   촬영(IMAGE_FILE)  : 등록 기능이 없어 항상 0 이다. (ZP2-21)
 *   판독(IMAGE_READING): 테이블은 있으나 엔티티를 만들지 않았다. (ZP2-23, 2026-09-02 결정)
 *   둘 다 세어봐야 값이 고정이라, 지금 엔티티를 만들면 쓰이지 않는 코드만 남는다.
 *   기능이 붙을 때 decideNextStep 에 조건 한 줄씩 추가하면 된다.
 */
@Service
@RequiredArgsConstructor
public class ImageWorklistService {

    private static final String YES = "Y";
    private static final String NO = "N";

    /** 촬영 등록 기능이 생기기 전까지 고정값. (위 클래스 주석 참고) */
    private static final int IMAGE_FILE_COUNT_NOT_IMPLEMENTED = 0;

    private final ImageReceptionRepository imageReceptionRepository;
    private final ImageScheduleRepository imageScheduleRepository;
    private final ConsentRepository consentRepository;
    private final ImageOrderItemRepository imageOrderItemRepository;
    private final ImageWorklistMapper imageWorklistMapper;

    /**
     * 워크리스트 조회.
     *
     * @param receptionStatusCode "ACCEPTED"=처리 대상, "EXCLUDED"=제외됨, null=전체
     */
    @Transactional(readOnly = true)
    public List<ImageWorklistItemDto> getWorklist(String receptionStatusCode) {
        List<ImageReceptionEntity> receptions = findReceptionsBy(receptionStatusCode);
        if (receptions.isEmpty()) {
            return List.of();
        }

        List<String> receptionIds = receptions.stream()
                .map(ImageReceptionEntity::getImageReceptionId)
                .toList();

        /*
         * ⚠ 동의는 접수가 아니라 오더에 붙는다. (CONSENT.image_order_id)
         *   IMAGE_ORDER : IMAGE_RECEPTION = 1:N 이라 한 오더의 접수가 여럿일 수 있고,
         *   그 경우 같은 동의를 여러 접수가 공유한다. 그래서 오더ID 로 모아 조회한다.
         */
        List<String> orderIds = receptions.stream()
                .map(reception -> reception.getImageOrder().getImageOrderId())
                .distinct()
                .toList();

        /*
         * ⚠ 접수마다 최종 일정이 여러 건이다(촬영항목마다 1건). 두 값을 함께 뽑는다.
         *   - 가장 이른 예정일시 : 목록 한 줄에 보여줄 시각
         *   - 일정이 잡힌 항목 수 : "3건 중 1건" 진행도
         */
        List<ImageScheduleEntity> schedules = imageScheduleRepository
                .findByImageReception_ImageReceptionIdInAndLatestYn(receptionIds, YES);

        Map<String, LocalDateTime> scheduledAtByReceptionId = findEarliestScheduledAt(schedules);
        Map<String, Long> scheduledItemCountByReceptionId = schedules.stream()
                .collect(Collectors.groupingBy(
                        schedule -> schedule.getImageReception().getImageReceptionId(),
                        Collectors.counting()));

        Map<String, Long> itemCountByOrderId = imageOrderItemRepository
                .findByImageOrder_ImageOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(
                        item -> item.getImageOrder().getImageOrderId(),
                        Collectors.counting()));

        Set<String> orderIdsWithConsent = Set.copyOf(
                consentRepository.findOrderIdsWithValidConsent(orderIds));

        return receptions.stream()
                .map(reception -> toItem(
                        reception,
                        scheduledAtByReceptionId.get(reception.getImageReceptionId()),
                        itemCountByOrderId
                                .getOrDefault(reception.getImageOrder().getImageOrderId(), 0L).intValue(),
                        scheduledItemCountByReceptionId
                                .getOrDefault(reception.getImageReceptionId(), 0L).intValue(),
                        orderIdsWithConsent.contains(reception.getImageOrder().getImageOrderId())))
                .toList();
    }

    /** 접수상태 필터에 따라 조회 메서드를 고른다. 값이 없거나 모르는 값이면 전체. */
    private List<ImageReceptionEntity> findReceptionsBy(String receptionStatusCode) {
        if (ReceptionStatus.ACCEPTED.name().equals(receptionStatusCode)
                || ReceptionStatus.EXCLUDED.name().equals(receptionStatusCode)) {
            return imageReceptionRepository.findWorklistByStatus(receptionStatusCode);
        }
        return imageReceptionRepository.findWorklistAll();
    }

    /**
     * 접수ID → 가장 이른 촬영 예정일시. 일정이 없는 접수는 키가 없다.
     *
     * ⚠ toMap 에 병합 함수를 반드시 준다. 접수 하나가 항목 수만큼 여러 행으로 나오므로
     *   병합 함수가 없으면 IllegalStateException(Duplicate key)이 난다.
     */
    private Map<String, LocalDateTime> findEarliestScheduledAt(List<ImageScheduleEntity> schedules) {
        return schedules.stream()
                .collect(Collectors.toMap(
                        schedule -> schedule.getImageReception().getImageReceptionId(),
                        ImageScheduleEntity::getScheduledAt,
                        (earlier, later) -> earlier.isBefore(later) ? earlier : later));
    }

    private ImageWorklistItemDto toItem(ImageReceptionEntity reception,
                                        LocalDateTime scheduledAt,
                                        int imageItemCount,
                                        int scheduledItemCount,
                                        boolean hasConsent) {

        ImageWorklistStep nextStep =
                decideNextStep(imageItemCount, scheduledItemCount, hasConsent);

        return imageWorklistMapper.toWorklistItem(
                reception,
                scheduledAt,
                imageItemCount,
                scheduledItemCount,
                hasConsent ? YES : NO,
                IMAGE_FILE_COUNT_NOT_IMPLEMENTED,
                nextStep);
    }

    /**
     * 다음에 해야 할 일을 정한다. 위에서부터 먼저 걸리는 것이 답이다.
     *
     * ⚠ 이 순서가 곧 업무 순서다. 동의는 촬영 앞을 막는 단계다 —
     *   조영제·침습검사는 동의 없이 촬영하면 안 된다.
     *
     * ⚠ 일정이 동의보다 먼저인 이유 —
     *   동의서에는 촬영 예정일이 들어가고, 일정이 정해져야 환자에게 언제 오라고 안내하면서
     *   동의를 받는다. 실제 업무 순서가 그렇다.
     *
     * ⚠ ACQUISITION 에서 멈춘다. 촬영 등록 기능이 없어 그 다음을 판단할 근거가 없다.
     *   그래도 값을 내려주는 이유는, 동의까지 끝낸 건이 목록에 계속 남는 이유를
     *   담당자가 알 수 있어야 하기 때문이다. (검사 쪽 RESULT 와 같은 취급)
     *
     * TODO(ZP2-21 촬영 등록): 영상파일이 있으면 READING 을 반환하도록 조건을 추가한다.
     * TODO(ZP2-23 판독): 판독이 끝난 건을 목록에서 어떻게 뺄지 정해지면 그 조건도 여기 둔다.
     */
    private ImageWorklistStep decideNextStep(int imageItemCount,
                                             int scheduledItemCount,
                                             boolean hasConsent) {
        /*
         * ⚠ "일정이 하나라도 있는가"가 아니라 "항목 전부에 일정이 있는가"로 본다. (2026-09-03)
         *   CT 만 잡고 MRI·초음파를 안 잡았는데 다음 단계로 넘기면, 안 잡힌 촬영이 그대로 묻힌다.
         */
        if (scheduledItemCount < imageItemCount) {
            return ImageWorklistStep.SCHEDULE;
        }
        if (!hasConsent) {
            return ImageWorklistStep.CONSENT;
        }
        return ImageWorklistStep.ACQUISITION;
    }
}
