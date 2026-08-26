package kr.co.seoulit.his.labimagingservice.laborder.service;

import kr.co.seoulit.his.labimagingservice.businessdelegate.patient.PatientServiceBusinessDelegate;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.status.OrderItemStatus;
import kr.co.seoulit.his.labimagingservice.common.status.OrderStatus;
import kr.co.seoulit.his.labimagingservice.common.status.ReceptionStatus;
import kr.co.seoulit.his.labimagingservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.labimagingservice.common.exception.DuplicateOrderException;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderSummaryDto;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabReceptionDetailDto;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderItemRequestDto;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabReceptionEntity;
import kr.co.seoulit.his.labimagingservice.laborder.mapper.LabOrderMapper;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabOrderRepository;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabReceptionRepository;
import kr.co.seoulit.his.labimagingservice.labschedule.entity.LabScheduleEntity;
import kr.co.seoulit.his.labimagingservice.labschedule.repository.LabScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 검사 오더 접수 서비스
 * 대응 유스케이스: UC-SPC-01 검사오더접수 (Jira ZP2-12)
 * ※ 2026-07-16부터 Consumer가 GR2 처방코어로 일원화됨(기존: GR2/ZQ2/UD2 개별 호출).
 *   PATCH cancel도 동일하게 코어 경유로 변경.
 *
 * ⚠ Service 인터페이스 없이 클래스로 바로 구현했습니다 (LabOrderServiceImpl 형태 아님).
 *   - 구현체가 1개뿐이고 교체될 계획이 없어 인터페이스로 얻을 다형성 이득이 없음
 *   - Spring 4+ 는 CGLIB 프록시로 클래스에도 @Transactional AOP를 걸 수 있어
 *     인터페이스가 프록시 생성 조건이 아님 (과거 JDK 동적 프록시 시절과 다름)
 *   - Mockito도 클래스 자체를 mock 가능해 테스트 목적으로도 인터페이스가 필수는 아님
 *   반대로 인터페이스+Impl로 분리해야 하는 경우: 구현체가 여러 개이거나(예: 알림/결제 방식별
 *   전략 구현), 모듈 경계에서 계약(API)만 노출하고 구현 세부사항은 감춰야 할 때. 이 서비스가
 *   그런 상황이 되면(다른 구현체가 추가되면) 인터페이스를 추출하는 것이 맞습니다.
 *
 * ⚠ 타 서비스 연동 현황 (2026-08-04)
 * - PatientServiceBusinessDelegate: 코드는 완료. 단 2026-08-04 현재 환자서비스의
 *   /validation 이 미구현이라 404 → false 로 떨어져 createOrder 가 항상 LAB998 을 반환합니다.
 *   환자서비스 구현 완료 시 별도 수정 없이 정상 동작합니다.
 * - 공통코드 검증: 연동 완료. CommonCodeCache(로컬 캐시)로 systemCode/treatTypeCode/labItemCode 를 검증합니다.
 * - orderStatusCode/itemStatusCode/receptionStatusCode 는 admin 공통코드가 아니라 서비스 내부 Enum입니다.
 *   (common.status 패키지 — 2026-08-04 팀 결정)
 */
@Service
@RequiredArgsConstructor
public class LabOrderService {

    private final LabOrderRepository labOrderRepository;
    private final LabReceptionRepository labReceptionRepository;
    private final LabOrderMapper labOrderMapper;
    private final PatientServiceBusinessDelegate patientServiceBusinessDelegate;
    private final CommonCodeCache commonCodeCache;
    private final LabScheduleRepository labScheduleRepository;

    /** 최종(현재 유효) 일정 판별값. LAB_SCHEDULE.latest_yn */
    private static final String LATEST_YN = "Y";

    // ------------검사  접수 단건 조회---------------
    @Transactional(readOnly = true)
    public LabReceptionDetailDto getReceptionByNo(String receptionNo) {
        LabReceptionEntity reception = labReceptionRepository.findByReceptionNo(receptionNo)
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB013,
                        "검사접수 정보를 찾을 수 없습니다. (receptionNo=" + receptionNo + ")"));

        // 목록과 같은 정보를 보여주기 위해 예정일시도 채운다. (일정 미등록이면 null)
        LocalDateTime scheduledAt = labScheduleRepository
                .findByLabReception_LabReceptionIdAndLatestYn(reception.getLabReceptionId(), LATEST_YN)
                .map(LabScheduleEntity::getScheduledAt)
                .orElse(null);

        return labOrderMapper.toDetailResponse(reception.getLabOrder(), reception, scheduledAt);
    }

    /*
     * 접수 목록 조회(getReceptions)는 삭제했다. (2026-08-14)
     * 워크리스트(LabWorklistService)가 같은 목록을 진행 상태까지 얹어서 대체한다.
     * 함께 빠진 것: findReceptionsBy, findLatestScheduledAt,
     *              LabReceptionRepository 의 findUnscheduled/findScheduled/findAllWithLabOrder,
     *              LabOrderMapper 의 3-파라미터 toResponse.
     */

    // ------------ 워크리스트 제외 / 복구 ---------------
    /**
     * 접수를 워크리스트에서 제외한다.
     *
     * ⚠ 워크리스트는 "결과가 등록되지 않은 접수는 전부 목록에 있다"가 원칙이라,
     *   담당자가 직접 빼주지 않으면 처리되지 않을 건(환자 미방문, 잘못 들어온 오더 등)이
     *   목록에 영원히 남는다. 기간이 지났다고 자동으로 숨기지 않는 이유는,
     *   실제로는 처리해야 하는데 누락된 건까지 같이 사라지기 때문이다.
     *
     * ⚠ 삭제가 아니다. 상태만 바뀌고 행은 그대로 남아 restoreReception 으로 되돌릴 수 있다.
     */
    @Transactional
    public void excludeReception(String receptionNo, String exclusionReason) {
        LabReceptionEntity reception = findReceptionByNo(receptionNo);

        reception.exclude(exclusionReason, LocalDateTime.now());
    }

    /**
     * 제외된 접수를 워크리스트로 되돌린다.
     *
     * ⚠ EXCLUDED 상태만 복구할 수 있다.
     *   앞으로 결과 등록이 구현되면 "결과등록완료"로 목록에서 빠지는 경로가 생기는데,
     *   그건 업무가 끝나서 빠진 것이라 되돌릴 대상이 아니다. 여기서 상태를 확인하지 않으면
     *   완료된 접수까지 워크리스트로 되살아난다.
     */
    @Transactional
    public void restoreReception(String receptionNo) {
        LabReceptionEntity reception = findReceptionByNo(receptionNo);

        if (!ReceptionStatus.EXCLUDED.name().equals(reception.getReceptionStatusCode())) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB026,
                    "제외된 접수가 아니어서 복구할 수 없습니다. (receptionNo=" + receptionNo
                            + ", 상태=" + reception.getReceptionStatusCode() + ")"
            );
        }

        reception.restore();
    }

    /** 접수번호로 접수를 찾는다. 없으면 LAB013. (제외/복구가 같은 조회를 쓴다) */
    private LabReceptionEntity findReceptionByNo(String receptionNo) {
        return labReceptionRepository.findByReceptionNo(receptionNo)
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB013,
                        "검사접수 정보를 찾을 수 없습니다. (receptionNo=" + receptionNo + ")"));
    }

    @Transactional
    public LabOrderSummaryDto createOrder(LabOrderCreateRequestDto request) {

        // 환자 유효성 검증 — 존재하지 않거나 비활성/통합된 환자면 접수를 만들지 않는다.
        // 환자서비스 장애(타임아웃/5xx)는 false가 아니라 예외로 전파되어 LAB999(500)로 응답한다.
        if (!patientServiceBusinessDelegate.validatePatient(request.getPatientId())) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB998,
                    "유효하지 않은 환자ID입니다. (patientId=" + request.getPatientId() + ")"
            );
        }

        // 공통코드 검증 — admin 실시간 조회가 아니라 로컬 캐시(CommonCodeCache)로 확인한다.
        validateCode("SYSTEM_SOURCE_CD", request.getSystemCode(), "연계시스템코드");
        validateCode("RCPT_TYPE_CD", request.getTreatTypeCode(), "진료구분코드");
        for (LabOrderItemRequestDto itemRequest : request.getOrderItems()) {
            validateCode("TEST_TYPE_CD", itemRequest.getLabItemCode(), "검사항목코드");
        }

        // 중복 접수 방지: 오더번호(lab_order_no) UNIQUE 위반을 사전에 확인
        if (labOrderRepository.existsByLabOrderNo(request.getLabOrderNo())) {
            throw new DuplicateOrderException(
                    LabMessageCode.LAB004,
                    "이미 접수된 오더입니다. (labOrderNo=" + request.getLabOrderNo() + ")"
            );
        }

        LabOrderEntity labOrder = LabOrderEntity.builder()
                .labOrderNo(request.getLabOrderNo())
                .systemCode(request.getSystemCode())
                .patientId(request.getPatientId())
                .physicianNo(request.getPhysicianNo())
                .physicianId(request.getPhysicianId())
                .treatTypeCode(request.getTreatTypeCode())
                .urgencyYn(request.getUrgencyYn())
                .orderStatusCode(OrderStatus.RECEIVED.name())
                .receivedAt(LocalDateTime.now())
                .build();

        for (LabOrderItemRequestDto itemRequest : request.getOrderItems()) {
            LabOrderItemEntity item = LabOrderItemEntity.builder()
                    .labItemCode(itemRequest.getLabItemCode())
                    .itemStatusCode(OrderItemStatus.REGISTERED.name())
                    .build();
            labOrder.addOrderItem(item);
        }

        // 오더 접수 = 접수(LAB_RECEPTION) 동시 생성 (0단계 스코프 확정 사항)
        LabReceptionEntity reception = LabReceptionEntity.builder()
                .receptionNo(generateReceptionNo()) // TODO: 실제 채번 규칙 확정 필요 (현재는 임시 로직)
                .patientId(request.getPatientId())
                .receptionStatusCode(ReceptionStatus.ACCEPTED.name())
                .urgencyYn(request.getUrgencyYn())
                .receivedById(request.getReceivedById())
                .ackSentYn("N")
                .ackSentAt(null)
                .build();
        labOrder.addReception(reception);

        LabOrderEntity saved = labOrderRepository.save(labOrder);
        LabReceptionEntity savedReception = saved.getReceptions().get(0);

        return labOrderMapper.toResponse(saved, savedReception);
    }

    /**
     * 공통코드 캐시로 코드값을 검증하고, 유효하지 않으면 LAB017로 실패시킨다.
     *
     * ⚠ 캐시에 그룹 자체가 없어도 false다. admin에 해당 그룹이 등록돼 있는데도 계속 실패한다면
     *   코드값이 아니라 캐시 적재를 먼저 의심해야 한다 (기동 로그의 "공통코드 캐시를 갱신했습니다" 확인).
     */
    private void validateCode(String groupCode, String code, String fieldLabel) {
        if (!commonCodeCache.isValid(groupCode, code)) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB017,
                    "유효하지 않은 " + fieldLabel + "입니다. (" + groupCode + "=" + code + ")"
            );
        }
    }

    /**
     * ⚠ 임시 채번 로직입니다. 실제 접수번호 채번 규칙(부서별 접두어, 일자별 시퀀스 등)이
     *   정해지면 교체해야 합니다.
     */
    private String generateReceptionNo() {
        return "LR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
