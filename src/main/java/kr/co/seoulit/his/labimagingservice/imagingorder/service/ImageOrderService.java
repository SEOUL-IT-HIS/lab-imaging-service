package kr.co.seoulit.his.labimagingservice.imagingorder.service;

import kr.co.seoulit.his.labimagingservice.businessdelegate.patient.PatientServiceBusinessDelegate;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.OrderItemStatus;
import kr.co.seoulit.his.labimagingservice.common.OrderStatus;
import kr.co.seoulit.his.labimagingservice.common.ReceptionStatus;
import kr.co.seoulit.his.labimagingservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.labimagingservice.common.exception.DuplicateOrderException;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.imagingorder.dto.ImageOrderCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingorder.dto.ImageOrderSummaryDto;
import kr.co.seoulit.his.labimagingservice.imagingorder.dto.ImageOrderItemRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageOrderEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageReceptionEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.mapper.ImageOrderMapper;
import kr.co.seoulit.his.labimagingservice.imagingorder.repository.ImageOrderRepository;
import kr.co.seoulit.his.labimagingservice.imagingorder.repository.ImageReceptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 영상 오더 접수 서비스
 * 대응 유스케이스: UC-IMG-01 영상오더접수 (Jira ZP2-19)
 * ※ 2026-07-16부터 Consumer가 GR2 처방코어로 일원화됨(기존: GR2/ZQ2/UD2 개별 호출).
 *   PATCH cancel도 동일하게 코어 경유로 변경.
 *
 * ⚠ Service 인터페이스 없이 클래스로 바로 구현했습니다 (ImageOrderServiceImpl 형태 아님).
 *   - 구현체가 1개뿐이고 교체될 계획이 없어 인터페이스로 얻을 다형성 이득이 없음
 *   - Spring 4+ 는 CGLIB 프록시로 클래스에도 @Transactional AOP를 걸 수 있어
 *     인터페이스가 프록시 생성 조건이 아님 (과거 JDK 동적 프록시 시절과 다름)
 *   - Mockito도 클래스 자체를 mock 가능해 테스트 목적으로도 인터페이스가 필수는 아님
 *   반대로 인터페이스+Impl로 분리해야 하는 경우: 구현체가 여러 개이거나(예: 알림/결제 방식별
 *   전략 구현), 모듈 경계에서 계약(API)만 노출하고 구현 세부사항은 감춰야 할 때. 이 서비스가
 *   그런 상황이 되면(다른 구현체가 추가되면) 인터페이스를 추출하는 것이 맞습니다.
 *
 * ⚠ 타 서비스 연동 현황은 LabOrderService와 동일합니다 (환자검증·공통코드 검증 모두 연동 완료,
 *   단 환자서비스 /validation 미구현으로 현재는 createOrder 가 항상 LAB998 — 상세 사유는 그쪽 주석 참고).
 *   검증 대상 필드만 다릅니다: systemCode / treatTypeCode / imageItemCode.
 */
@Service
@RequiredArgsConstructor
public class ImageOrderService {

    private final ImageOrderRepository imageOrderRepository;
    private final ImageReceptionRepository imageReceptionRepository;
    private final ImageOrderMapper imageOrderMapper;
    private final PatientServiceBusinessDelegate patientServiceBusinessDelegate;
    private final CommonCodeCache commonCodeCache;


    // ------------영상  접수 단건 조회---------------
    @Transactional(readOnly = true)
    public ImageOrderSummaryDto getReceptionByNo(String receptionNo) {
        ImageReceptionEntity reception = imageReceptionRepository.findByReceptionNo(receptionNo)
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB015,
                        "영상 촬영 접수 정보를 찾을 수 없습니다. (receptionNo=" + receptionNo + ")"));
        return imageOrderMapper.toResponse(reception.getImageOrder(), reception);
    }

    // ------------영상 촬영 접수 목록 조회 (미일정 대상)---------------
    @Transactional(readOnly = true)
    public List<ImageOrderSummaryDto> getReceptions() {
        return imageReceptionRepository.findUnscheduledWithImageOrder().stream()
                .map(reception -> imageOrderMapper.toResponse(reception.getImageOrder(), reception))
                .toList();
    }

    @Transactional
    public ImageOrderSummaryDto createOrder(ImageOrderCreateRequestDto request) {

        // 환자 유효성 검증 — 상세 동작은 LabOrderService.createOrder 주석 참고
        if (!patientServiceBusinessDelegate.validatePatient(request.getPatientNo())) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB998,
                    "유효하지 않은 환자번호입니다. (patientNo=" + request.getPatientNo() + ")"
            );
        }

        // 공통코드 검증 — admin 실시간 조회가 아니라 로컬 캐시(CommonCodeCache)로 확인한다.
        validateCode("SYSTEM_SOURCE_CD", request.getSystemCode(), "연계시스템코드");
        validateCode("RCPT_TYPE_CD", request.getTreatTypeCode(), "진료구분코드");
        for (ImageOrderItemRequestDto itemRequest : request.getOrderItems()) {
            validateCode("IMG_ITEM_CD", itemRequest.getImageItemCode(), "촬영항목코드");
        }

        if (imageOrderRepository.existsByImageOrderNo(request.getImageOrderNo())) {
            throw new DuplicateOrderException(
                    LabMessageCode.LAB007,
                    "이미 접수된 오더입니다. (imageOrderNo=" + request.getImageOrderNo() + ")"
            );
        }

        ImageOrderEntity imageOrder = ImageOrderEntity.builder()
                .imageOrderNo(request.getImageOrderNo())
                .systemCode(request.getSystemCode())
                .patientNo(request.getPatientNo())
                .physicianNo(request.getPhysicianNo())
                .treatTypeCode(request.getTreatTypeCode())
                .urgencyYn(request.getUrgencyYn())
                .orderStatusCode(OrderStatus.RECEIVED.name())
                .receivedAt(LocalDateTime.now())
                .build();

        for (ImageOrderItemRequestDto itemRequest : request.getOrderItems()) {
            ImageOrderItemEntity item = ImageOrderItemEntity.builder()
                    .imageItemCode(itemRequest.getImageItemCode())
                    .itemStatusCode(OrderItemStatus.REGISTERED.name())
                    .build();
            imageOrder.addOrderItem(item);
        }

        ImageReceptionEntity reception = ImageReceptionEntity.builder()
                .receptionNo(generateReceptionNo()) // TODO: 실제 채번 규칙 확정 필요
                .patientNo(request.getPatientNo())
                .receptionStatusCode(ReceptionStatus.ACCEPTED.name())
                .urgencyYn(request.getUrgencyYn())
                .receivedById(request.getReceivedById())
                .ackSentYn("N")
                .ackSentAt(null)
                .build();
        imageOrder.addReception(reception);

        ImageOrderEntity saved = imageOrderRepository.save(imageOrder);
        ImageReceptionEntity savedReception = saved.getReceptions().get(0);

        return imageOrderMapper.toResponse(saved, savedReception);
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

    /**
     * ⚠ 임시 채번 로직입니다. 실제 접수번호 채번 규칙이 정해지면 반드시 교체해야 합니다.
     */
    private String generateReceptionNo() {
        return "IR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
