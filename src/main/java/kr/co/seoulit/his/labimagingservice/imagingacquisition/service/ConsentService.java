package kr.co.seoulit.his.labimagingservice.imagingacquisition.service;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.imagingacquisition.dto.ConsentCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingacquisition.dto.ConsentSummaryDto;
import kr.co.seoulit.his.labimagingservice.imagingacquisition.entity.ConsentEntity;
import kr.co.seoulit.his.labimagingservice.imagingacquisition.mapper.ConsentMapper;
import kr.co.seoulit.his.labimagingservice.imagingacquisition.repository.ConsentRepository;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageOrderEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.repository.ImageOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 조영제/침습검사 동의 서비스
 * 대응 유스케이스: UC-IMG-05 조영제/침습검사 동의 등록 (Jira ZP2-28)
 *
 * ⚠ Service 인터페이스 없이 클래스로 바로 구현한다. 사유는 LabOrderService 주석 참고.
 *
 * ── 1차 배포 범위 (2026-08-24 결정)
 *   포함: 동의 등록(ZP2-84) · 필수값/유효성 검증(ZP2-83) · 오더별 동의 상태 조회(ZP2-80)
 *   제외: 동의 철회 — 4차로 이월. ConsentWithdrawRequestDto 와 ConsentEntity#withdraw() 는
 *        이미 준비돼 있어 Service/Controller 메서드만 추가하면 된다.
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

    /** 철회되지 않은 동의를 가리키는 값. withdrawn_yn */
    private static final String NOT_WITHDRAWN = "N";

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

        if (consentRepository.existsByImageOrder_ImageOrderIdAndConsentTypeCodeAndWithdrawnYn(
                request.getImageOrderId(), request.getConsentTypeCode(), NOT_WITHDRAWN)) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB031,
                    "이미 등록된 동의가 있습니다. (오더번호=" + imageOrder.getImageOrderNo()
                            + ", 유형=" + request.getConsentTypeCode() + ")"
            );
        }

        ConsentEntity consent = ConsentEntity.builder()
                .patientNo(request.getPatientNo())
                .patientId(request.getPatientId())
                .consentTypeCode(request.getConsentTypeCode())
                .documentTemplateId(request.getDocumentTemplateId())
                .consentYn(request.getConsentYn())
                .consentDt(request.getConsentDt())
                .signedByName(request.getSignedByName())
                .witnessId(request.getWitnessId())
                .withdrawnYn(NOT_WITHDRAWN)
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
     * ⚠ "촬영을 진행해도 되는가"의 최종 판단은 여기서 하지 않는다.
     *   어떤 촬영항목이 동의를 필요로 하는지(IMAGE_ORDER_ITEM 기준)가 아직 정해지지 않았다.
     *   지금은 이력을 그대로 내려주고, 화면이 "철회 전(withdrawnYn='N') + 동의함(consentYn='Y')"
     *   건이 있는지로 표시한다. 판정 규칙이 확정되면 이 메서드 옆에 별도 메서드로 추가할 것.
     */
    @Transactional(readOnly = true)
    public List<ConsentSummaryDto> getConsentsByImageOrderId(String imageOrderId) {
        List<ConsentEntity> consents = consentRepository.findByImageOrderIdWithOrder(imageOrderId);
        return consentMapper.toResponseList(consents);
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
