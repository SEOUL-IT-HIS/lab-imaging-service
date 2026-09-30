package kr.co.seoulit.his.labimagingservice.billing.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 검사항목코드(labItemCode) → 수가코드(feeCode) 변환.
 *
 * ⚠ 2026-09-09 기준 실제 매핑표 미확정. 테스트용 임시값이다.
 *   수납팀에서 검사항목코드-수가코드 매핑표를 받으면 이 설정값만 교체한다.
 *   코드 구조는 이미 확정된 것이므로 값 교체 외 로직 변경은 불필요하다.
 *   (application.properties 의 app.billing.fee-code-mapping.{labItemCode}=... 참고 —
 *    지금은 전부 FEE002 로 통일된 테스트값이다)
 *
 * ── 5차 Phase 5 변경 (후속조치 #3)
 *   예전에는 매핑이 없으면 IllegalStateException 을 던지고, 호출한 쪽이 로그만 남겼다 — 누락이 로그에만 있어
 *   아무도 몰랐다. 이제는 Optional 로 돌려주고, BillingChargeService 가 발신 이력에 "03 전송실패 +
 *   수가코드 매핑 없음: {코드}"로 남겨 조회 화면에 드러나게 한다. (확정은 여전히 그대로 성공한다)
 *   기동 시 전체 코드와 매핑을 대조하는 점검은 FeeCodeMappingChecker 가 한다.
 *
 * ── 영상 매핑 (5차 Phase 7) : app.billing.image-fee-code-mapping.{IMG_ITEM_CD}=...
 *   검사와 코드 체계(TEST_TYPE_CD / IMG_ITEM_CD)가 달라 맵을 나눴다. 매핑값은 수납팀 요청 대상이다(지어내지 않는다).
 *
 * ⚠ Map<String, String> 빈이 둘이라 @Qualifier 로 이름을 지정한다. 타입만으로 주입하면 어느 쪽인지 모호하다.
 */
@Slf4j
@Component
public class FeeCodeResolver {

    private final Map<String, String> feeCodeMapping;
    private final Map<String, String> imageFeeCodeMapping;

    public FeeCodeResolver(@Qualifier("feeCodeMapping") Map<String, String> feeCodeMapping,
                           @Qualifier("imageFeeCodeMapping") Map<String, String> imageFeeCodeMapping) {
        this.feeCodeMapping = feeCodeMapping;
        this.imageFeeCodeMapping = imageFeeCodeMapping;
    }

    /** 검사항목(TEST_TYPE_CD) 수가코드. 매핑이 없으면 empty. */
    public Optional<String> findLabFeeCode(String labItemCode) {
        return Optional.ofNullable(feeCodeMapping.get(labItemCode));
    }

    /** 영상항목(IMG_ITEM_CD) 수가코드. 매핑이 없으면 empty. */
    public Optional<String> findImageFeeCode(String imageItemCode) {
        return Optional.ofNullable(imageFeeCodeMapping.get(imageItemCode));
    }

    public Set<String> labMappedCodes() {
        return feeCodeMapping.keySet();
    }

    public Set<String> imageMappedCodes() {
        return imageFeeCodeMapping.keySet();
    }
}
