package kr.co.seoulit.his.labimagingservice.imagingorder.service;

import kr.co.seoulit.his.labimagingservice.common.cache.StaffDirectoryCache;
import kr.co.seoulit.his.labimagingservice.imagingorder.dto.ImageOrderCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingorder.dto.ImageOrderItemRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingorder.dto.ImageOrderSummaryDto;
import kr.co.seoulit.his.labimagingservice.imagingorder.messaging.dto.ImageOrderRequestedData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 영상오더 연계 수신 서비스 (처방코어 → 검사영상서비스). UC-IMG-01 (5차 Phase 8)
 *
 * 하는 일: 코어 계약(ImageOrderRequestedData) → 영상 도메인 계약(ImageOrderCreateRequestDto) 변환 후
 *   기존 ImageOrderService.createOrder 호출. 저장 로직을 다시 만들지 않는다 — createOrder 가 환자 검증·공통코드
 *   검증·오더번호 중복·IMAGE_ORDER/ITEM/RECEPTION 생성을 한 트랜잭션으로 한다(REST 등록과 같은 경로).
 *
 * ⚠ 검사오더의 LabOrderIntakeService 와 달리 여기서는 수신로그를 남기지 않는다. 수신로그는 Consumer 가
 *   event_id 와 함께 한 번만 남긴다(검사오더 경로는 Consumer·IntakeService 가 각각 남겨 한 이벤트에 2행이 생긴다 —
 *   그 경로는 동작 불변 조건이라 손대지 않았다).
 * ⚠ @Transactional 을 걸지 않는다. 걸면 createOrder 가 여기에 합류해, 실패 시 Consumer 의 결과 기록 흐름과 얽힌다.
 *   예외는 그대로 던진다 — 업무 거절/재시도 판단은 Consumer 가 한다.
 *
 * ⚠ 직원 검증(StaffValidator)을 쓰지 않는다 — LabOrderIntakeService 와 같은 이유(§0-2, 그쪽 javadoc
 *   참고). doctorId 가 의사로 확인되지 않아도 WARN 로그만 남기고 접수는 그대로 진행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageOrderIntakeService {

    /** 수신 출처 SYSTEM_SOURCE_CD — 처방코어는 외래 채널 "01" (검사오더와 같다, 2026-09-16 확인값) */
    public static final String SYSTEM_CODE_OUTPATIENT = "01";
    /** 진료구분 RCPT_TYPE_CD 01=외래 — 코어 payload 에 채널 구분이 없어 고정 (검사오더와 같은 한계, 후속 #5) */
    private static final String TREAT_TYPE_OUTPATIENT = "01";
    /** 코어 payload 에 응급 개념이 없다 (검사오더와 같은 한계, 후속 #5) */
    private static final String URGENCY_NO = "N";
    /** 사람이 아닌 시스템 접수 */
    private static final String RECEIVED_BY_SYSTEM = "SYSTEM";

    private final ImageOrderService imageOrderService;
    private final StaffDirectoryCache staffDirectoryCache;

    public ImageOrderSummaryDto intake(ImageOrderRequestedData data) {
        warnIfDoctorIdNotRecognized(data.getDoctorId());
        return imageOrderService.createOrder(toCreateRequest(data));
    }

    /** LabOrderIntakeService.warnIfDoctorIdNotRecognized 와 같다 — intake는 절대 거절하지 않는다. */
    private void warnIfDoctorIdNotRecognized(String doctorId) {
        if (doctorId == null || doctorId.isBlank()) {
            return;
        }
        try {
            if (staffDirectoryCache.isAvailable() && !staffDirectoryCache.isDoctor(doctorId)) {
                log.warn("[STAFF_VALIDATION] 연계 수신 doctorId가 의사로 확인되지 않습니다. 접수는 그대로 진행합니다. (doctorId={})",
                        doctorId);
            }
        } catch (RuntimeException e) {
            log.warn("[STAFF_VALIDATION] 직원 디렉터리 조회 중 오류가 있었지만 접수는 그대로 진행합니다.", e);
        }
    }

    /** 순수 변환. itemName·encounterId 는 옮기지 않는다(저장 컬럼 없음 / 표시명은 공통코드에서). */
    ImageOrderCreateRequestDto toCreateRequest(ImageOrderRequestedData data) {
        List<ImageOrderItemRequestDto> items = data.getOrderItems() == null
                ? List.of()
                : data.getOrderItems().stream()
                        .map(item -> ImageOrderItemRequestDto.builder().imageItemCode(item.getItemCode()).build())
                        .toList();

        return ImageOrderCreateRequestDto.builder()
                .imageOrderNo(data.getPrescriptionId())
                .systemCode(SYSTEM_CODE_OUTPATIENT)
                .patientId(data.getPatientId())
                .physicianNo(null)
                .physicianId(data.getDoctorId())
                .treatTypeCode(TREAT_TYPE_OUTPATIENT)
                .urgencyYn(URGENCY_NO)
                .receivedById(RECEIVED_BY_SYSTEM)
                .orderItems(items)
                .build();
    }
}
