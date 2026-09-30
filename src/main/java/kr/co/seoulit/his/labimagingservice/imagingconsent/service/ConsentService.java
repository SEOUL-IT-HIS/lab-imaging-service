package kr.co.seoulit.his.labimagingservice.imagingconsent.service;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.common.status.OrderItemStatus;
import kr.co.seoulit.his.labimagingservice.imagingconsent.dto.ConsentCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingconsent.dto.ConsentSummaryDto;
import kr.co.seoulit.his.labimagingservice.imagingconsent.dto.ConsentWithdrawRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingconsent.entity.ConsentEntity;
import kr.co.seoulit.his.labimagingservice.imagingconsent.mapper.ConsentMapper;
import kr.co.seoulit.his.labimagingservice.imagingconsent.repository.ConsentRepository;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageOrderEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.repository.ImageOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 조영제/침습검사 동의 서비스
 * 대응 유스케이스: UC-IMG-05 조영제/침습검사 동의 등록 (Jira ZP2-28)
 *
 * ⚠ Service 인터페이스 없이 클래스로 바로 구현한다. 사유는 LabOrderService 주석 참고.
 *
 * ── 범위
 *   1차 배포: 동의 등록(ZP2-84) · 필수값/유효성 검증(ZP2-83) · 오더별 동의 상태 조회(ZP2-80)
 *   5차 Phase 9: 거부 사유(refusal_note) · 동의 철회(withdrawConsent) — 1차 때 이월했던 기능.
 *   동의 "필요 여부" 판정은 이 클래스가 아니라 ConsentRequirementPolicy 에 있다(워크리스트·업로드 공용).
 *
 * ⚠ 환자 유효성(PatientServiceBusinessDelegate)은 호출하지 않는다.
 *   동의는 이미 접수된 영상오더에 붙는 것이고, 그 오더를 만들 때 환자ID를 이미 검증했다.
 *   (SpecimenService 가 검체 등록 시 재검증을 생략한 것과 같은 판단)
 */
@Service
@RequiredArgsConstructor
public class ConsentService {

    /** 동의서유형코드 공통코드 그룹. admin 등록 확인 완료 (CONTRAST/INVASIVE 등 5건) */
    private static final String CONSENT_TYPE_CD = "CONSENT_TYPE_CD";

    /** 철회사유코드 그룹 — admin 기존 그룹을 쓴다(신규 그룹 등록 안 함, 2026-09-28 확인) */
    private static final String CONSENT_WITHDRAW_CD = "CONSENT_WITHDRAW_CD";

    /** 철회되지 않은 동의를 가리키는 값. withdrawn_yn */
    private static final String NOT_WITHDRAWN = "N";

    /** 동의함. consent_yn ('N' 은 거부) */
    private static final String CONSENTED = "Y";

    private final ConsentRepository consentRepository;
    private final ImageOrderRepository imageOrderRepository;
    private final ConsentMapper consentMapper;
    private final CommonCodeCache commonCodeCache;

    /**
     * 동의 등록. (ZP2-84 / ZP2-83)
     *
     * 처리 순서
     *   1) 영상오더 존재 확인 — 없는 오더에 동의를 붙일 수는 없다.
     *   2) 동의서유형코드 공통코드 검증.
     *   3) 같은 유형의 철회 전 동의가 이미 있으면 차단 (중복 등록 방지).
     *   4) 저장.
     *
     * ⚠ withdrawnYn 은 요청으로 받지 않고 서버가 'N' 으로 시작시킨다.
     *   등록 시점에 이미 철회된 동의라는 것은 성립하지 않는다.
     */
    @Transactional
    public ConsentSummaryDto createConsent(ConsentCreateRequestDto request) {

        ImageOrderEntity imageOrder = imageOrderRepository.findById(request.getImageOrderId())
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB030,
                        "영상 오더 정보를 찾을 수 없습니다. (imageOrderId=" + request.getImageOrderId() + ")"
                ));

        validateCode(CONSENT_TYPE_CD, request.getConsentTypeCode(), "동의서유형코드");

        // ⚠ 거부 기록은 중복으로 보지 않는다 — 거부 후 재동의가 가능해야 한다. (D13)
        if (consentRepository.existsByImageOrder_ImageOrderIdAndConsentTypeCodeAndConsentYnAndWithdrawnYn(
                request.getImageOrderId(), request.getConsentTypeCode(), CONSENTED, NOT_WITHDRAWN)) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB031,
                    "이미 등록된 동의가 있습니다. (오더번호=" + imageOrder.getImageOrderNo()
                            + ", 유형=" + request.getConsentTypeCode() + ")"
            );
        }

        ConsentEntity consent = ConsentEntity.builder()
                .patientId(request.getPatientId())
                .consentTypeCode(request.getConsentTypeCode())
                .documentTemplateId(request.getDocumentTemplateId())
                .consentYn(request.getConsentYn())
                .consentDt(request.getConsentDt())
                .signedByName(request.getSignedByName())
                .witnessId(request.getWitnessId())
                .withdrawnYn(NOT_WITHDRAWN)
                // 거부 사유는 거부 건에만 남긴다 (5차 Phase 9-2). 동의 건에 사유가 붙으면 이력 화면에서 오해를 부른다.
                .refusalNote(CONSENTED.equals(request.getConsentYn()) ? null : blankToNull(request.getRefusalNote()))
                .build();
        consent.assignImageOrder(imageOrder);

        ConsentEntity saved = consentRepository.save(consent);
        return consentMapper.toResponse(saved);
    }

    /**
     * 영상오더별 동의 이력 조회. (ZP2-80 검사 진행 전 동의 상태 확인)
     *
     * ⚠ 결과 0건은 예외가 아니라 정상적인 빈 목록이다. "아직 동의를 안 받았다"는 것도 확인해야 할 상태다.
     *   (단건 조회는 "못 찾음 = 예외"가 맞지만, 목록은 다르다 — LabOrderService.getReceptions 와 같은 기준)
     *
     * ⚠ "촬영을 진행해도 되는가"의 판단은 여기서 하지 않는다 — ConsentRequirementPolicy(동의 필요 여부)와
     *   ConsentRepository.findOrderIdsWithValidConsent(유효 동의 = 동의함 + 미철회)가 한다(5차 Phase 9).
     *   이 메서드는 이력을 그대로 내려주고, 화면이 같은 기준(hasValidConsent)으로 표시한다.
     */
    @Transactional(readOnly = true)
    public List<ConsentSummaryDto> getConsentsByImageOrderId(String imageOrderId) {
        List<ConsentEntity> consents = consentRepository.findByImageOrderIdWithOrder(imageOrderId);
        return consentMapper.toResponseList(consents);
    }

    /**
     * 동의 철회. (5차 Phase 9-3, D14)
     *
     * 처리 순서
     *   1) 동의 존재 확인 (LAB094)
     *   2) 이미 철회된 건 거절 (LAB095)
     *   3) 거부(consent_yn=N) 기록은 철회 대상이 아니다 (LAB096) — 철회는 "했던 동의를 거둬들이는 것"이다.
     *   4) 철회사유 공통코드 검증 — 기존 그룹 CONSENT_WITHDRAW_CD (01 Condition Changed / 02 Other / 03 Patient Refused)
     *   5) withdrawn_yn=Y, 철회일시(서버 시각), 사유, 처리자 기록
     *
     * ⚠ D14: 이미 촬영(ACQUIRED)된 항목이 있어도 철회를 막지 않는다. 촬영된 영상은 지우지 않고 그대로 판독한다
     *   (워크리스트는 READING 에 머문다 — ImageWorklistService.decideNextStep). 새 촬영만 LAB052 로 막힌다.
     *   호출측이 안내 문구를 고르도록 acquired 를 함께 돌려준다(LAB097 "이미 촬영된 영상은 그대로 유지됩니다").
     * ⚠ 영상검사취소(UC-IMG-04, 6차)와 연계하지 않는다. 철회는 오더를 취소하지 않는다.
     */
    @Transactional
    public WithdrawResult withdrawConsent(String consentId, ConsentWithdrawRequestDto request) {
        ConsentEntity consent = consentRepository.findById(consentId)
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB094,
                        "동의 정보를 찾을 수 없습니다. (consentId=" + consentId + ")"));

        if (!NOT_WITHDRAWN.equals(consent.getWithdrawnYn())) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB095, "이미 철회된 동의입니다. (consentId=" + consentId + ")");
        }
        if (!CONSENTED.equals(consent.getConsentYn())) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB096, "거부 기록은 철회할 수 없습니다. (consentId=" + consentId + ")");
        }
        validateCode(CONSENT_WITHDRAW_CD, request.getWithdrawnReasonCode(), "철회사유코드");

        consent.withdraw(request.getWithdrawnReasonCode(), LocalDateTime.now(), request.getWithdrawnById());

        boolean acquired = consent.getImageOrder().getOrderItems().stream()
                .anyMatch(item -> OrderItemStatus.ACQUIRED.name().equals(item.getItemStatusCode()));

        return new WithdrawResult(consentMapper.toResponse(consent), acquired);
    }

    /** 철회 결과 — acquired 는 "이미 촬영된 항목이 있었다" (안내 문구 선택용, D14) */
    public record WithdrawResult(ConsentSummaryDto consent, boolean acquired) {
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
