package kr.co.seoulit.his.labimagingservice.billing.service;

import kr.co.seoulit.his.labimagingservice.billing.messaging.dto.BillingChargeRequestData;
import kr.co.seoulit.his.labimagingservice.common.status.ReceptionStatus;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageOrderEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.interfacelog.send.SendEventType;
import kr.co.seoulit.his.labimagingservice.interfacelog.send.service.InterfaceSendLogService;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabReceptionEntity;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabReceptionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 청구 요청 (LAB → 수납). UC-COM-03 / ZP2-45·124 (5차 Phase 5)
 *
 * 예전 LabResultService.publishBillingCharge + BillingChargeProducer 를 발신 이력 모듈 위로 옮겼다.
 *   - 페이로드(BillingChargeRequestData, 봉투 없는 평문 JSON, 키=receptionId)는 바꾸지 않았다. 수납팀 합의 규격이다.
 *   - 예전의 "실패하면 로그만"을 "이력 저장 + 재처리"로 바꿨다(후속조치 #8).
 *   - 수가코드 매핑이 없으면 이력에 03 + "수가코드 매핑 없음: {코드}"로 남겨 조회 화면에 드러나게 했다(후속조치 #3).
 *
 * ⚠ 이 클래스의 메서드는 예외를 던지지 않는다. 청구는 확정·촬영 등록의 부수 효과라, 여기서 무엇이 실패하든
 *   업무 처리는 성공해야 한다(수납팀과 합의한 원칙). 전부 잡아서 로그로 남긴다.
 * ⚠ 업무 트랜잭션 "안에서" 불러야 한다. 이력 행이 확정과 같은 트랜잭션에 기록되고, 커밋된 뒤에만 발행된다(D8).
 *
 * ⚠ 청구 1회 보장 — 발신 이력의 (event_type_code=02, reference_id=검사항목ID/영상촬영항목ID)가 UNIQUE 라,
 *   같은 항목을 두 번 청구하지 않는다(InterfaceSendLogService.recordPending 이 먼저 확인한다).
 */
@Slf4j
@Service
public class BillingChargeService {

    /** 청구 수량. 검사·촬영 1건 = 청구 1건 (BillingChargeRequestData 참고) */
    private static final String BILLING_QUANTITY = "1";

    private final FeeCodeResolver feeCodeResolver;
    private final InterfaceSendLogService interfaceSendLogService;
    private final LabReceptionRepository labReceptionRepository;
    private final String sourceServiceCode;
    private final String billingSystemCode;

    public BillingChargeService(FeeCodeResolver feeCodeResolver,
                                InterfaceSendLogService interfaceSendLogService,
                                LabReceptionRepository labReceptionRepository,
                                @Value("${app.billing.source-service-code}") String sourceServiceCode,
                                @Value("${app.interface-send.billing-system-code}") String billingSystemCode) {
        this.feeCodeResolver = feeCodeResolver;
        this.interfaceSendLogService = interfaceSendLogService;
        this.labReceptionRepository = labReceptionRepository;
        this.sourceServiceCode = sourceServiceCode;
        this.billingSystemCode = billingSystemCode;
    }

    /**
     * 검사항목 1건의 청구를 요청한다. (일반·미생물·병리 결과 확정 시 — 항목당 1회)
     *
     * ⚠ itemName 은 검사항목코드를 그대로 보낸다. 이 서비스는 타 서비스 소유 표시명(codeName)을 저장·매핑하지 않는다
     *   (개발표준가이드 14.1). 예전 발행과 같다.
     */
    public void requestLabCharge(LabOrderItemEntity item) {
        try {
            LabOrderEntity order = item.getLabOrder();
            Optional<String> feeCode = feeCodeResolver.findLabFeeCode(item.getLabItemCode());

            BillingChargeRequestData data = BillingChargeRequestData.builder()
                    .patientId(order.getPatientId())
                    .receptionId(findAcceptedReceptionId(order))
                    .admissionId(null)
                    .sourceServiceCode(sourceServiceCode)
                    .sourceRecordId(item.getLabOrderItemId())
                    .feeCode(feeCode.orElse(null))
                    .itemName(item.getLabItemCode())
                    .quantity(BILLING_QUANTITY)
                    .amount(null)
                    .build();

            record(item.getLabOrderItemId(), item.getLabItemCode(), feeCode.isPresent(), data);

        } catch (Exception e) {
            log.error("[BILLING] 청구 요청 준비 실패 — 확정은 그대로 진행한다. labOrderItemId={}", item.getLabOrderItemId(), e);
        }
    }

    /**
     * 영상 촬영항목 1건의 청구를 요청한다. (5차 Phase 7, D11 — 첫 촬영 완료(ACQUIRED) 시 1회)
     *
     * ⚠ 검사와 같은 청구 규격(BillingChargeRequestData)·같은 토픽을 쓴다. 다른 점은 셋뿐이다:
     *   수가코드 = app.billing.image-fee-code-mapping, sourceRecordId = 영상촬영항목ID, receptionId = 영상접수ID.
     * ⚠ 재촬영으로 파일이 더 올라와도 다시 부르지 않는다(호출측이 첫 전이만 부른다). 그래도 두 번 불리면
     *   발신 이력의 (02, 영상촬영항목ID) UNIQUE 가 막는다.
     */
    public void requestImageCharge(ImageOrderItemEntity item, String imageReceptionId) {
        try {
            ImageOrderEntity order = item.getImageOrder();
            Optional<String> feeCode = feeCodeResolver.findImageFeeCode(item.getImageItemCode());

            BillingChargeRequestData data = BillingChargeRequestData.builder()
                    .patientId(order.getPatientId())
                    .receptionId(imageReceptionId)
                    .admissionId(null)
                    .sourceServiceCode(sourceServiceCode)
                    .sourceRecordId(item.getImageOrderItemId())
                    .feeCode(feeCode.orElse(null))
                    .itemName(item.getImageItemCode())
                    .quantity(BILLING_QUANTITY)
                    .amount(null)
                    .build();

            record(item.getImageOrderItemId(), item.getImageItemCode(), feeCode.isPresent(), data);

        } catch (Exception e) {
            log.error("[BILLING] 영상 청구 요청 준비 실패 — 촬영 등록은 그대로 진행한다. imageOrderItemId={}",
                    item.getImageOrderItemId(), e);
        }
    }

    /**
     * 발신 이력에 남긴다. 수가코드가 있으면 01(발행 예약), 없으면 03(매핑 없음 — 원문은 참고용으로 같이 남긴다).
     */
    void record(String referenceId, String itemCode, boolean hasFeeCode, BillingChargeRequestData data) {
        if (hasFeeCode) {
            // 청구 원문에는 event_id 가 없다(평문 규격). 받은 eventId 는 쓰지 않는다 — 이력의 event_id 가 추적 키다.
            interfaceSendLogService.recordPending(SendEventType.BILLING, referenceId, billingSystemCode, eventId -> data);
        } else {
            interfaceSendLogService.recordFailed(SendEventType.BILLING, referenceId, billingSystemCode, data,
                    "수가코드 매핑 없음: " + itemCode);
        }
    }

    /**
     * 오더의 "처리 대상(ACCEPTED)" 접수ID. (예전 LabResultService.findAcceptedReceptionId 를 옮겼다)
     * ⚠ LAB_ORDER : LAB_RECEPTION 은 1:N 이라 여러 건이면 가장 최근 접수를 쓰고 경고를 남긴다.
     */
    private String findAcceptedReceptionId(LabOrderEntity order) {
        List<LabReceptionEntity> receptions = labReceptionRepository
                .findByLabOrder_LabOrderIdAndReceptionStatusCodeOrderByCreatedAtDesc(
                        order.getLabOrderId(), ReceptionStatus.ACCEPTED.name());
        if (receptions.isEmpty()) {
            throw new IllegalStateException("ACCEPTED 상태 접수를 찾을 수 없습니다. labOrderId=" + order.getLabOrderId());
        }
        if (receptions.size() > 1) {
            log.warn("한 오더에 ACCEPTED 상태 접수가 {}건 있습니다. 가장 최근 접수를 사용합니다. labOrderId={}",
                    receptions.size(), order.getLabOrderId());
        }
        return receptions.get(0).getLabReceptionId();
    }
}
