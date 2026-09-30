package kr.co.seoulit.his.labimagingservice.billing.service;

import kr.co.seoulit.his.labimagingservice.common.cache.CommonCodeCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.TreeSet;

/**
 * 기동 시 수가코드 매핑 누락 점검. (5차 Phase 5-3 ②, 후속조치 #3)
 *
 * admin 공통코드(TEST_TYPE_CD / IMG_ITEM_CD) 전체와 설정된 매핑을 대조해, 빠진 코드를 WARN 으로 한 번 찍는다.
 * 빠진 코드의 검사·영상은 확정해도 청구가 나가지 않고 발신 이력에 03(수가코드 매핑 없음)으로 남는다.
 *
 * ⚠ 기동을 막지 않는다. 매핑표를 아직 받지 못한 상태(2026-09 현재)에서도 서비스는 떠야 한다.
 * ⚠ 공통코드 캐시가 비어 있으면(admin 미기동 등) 대조할 수 없어 점검을 건너뛴다고만 남긴다.
 *   CommonCodeCache 는 @PostConstruct 로 먼저 적재되므로 ApplicationReadyEvent 시점에는 채워져 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeeCodeMappingChecker {

    private static final String TEST_TYPE_CD = "TEST_TYPE_CD";
    private static final String IMG_ITEM_CD = "IMG_ITEM_CD";

    private final CommonCodeCache commonCodeCache;
    private final FeeCodeResolver feeCodeResolver;

    @EventListener(ApplicationReadyEvent.class)
    public void checkOnStartup() {
        check(TEST_TYPE_CD, feeCodeResolver.labMappedCodes(), "app.billing.fee-code-mapping");
        check(IMG_ITEM_CD, feeCodeResolver.imageMappedCodes(), "app.billing.image-fee-code-mapping");
    }

    /** @return 빠진 코드 (테스트용으로 반환) */
    Set<String> check(String groupCode, Set<String> mappedCodes, String propertyPrefix) {
        Set<String> allCodes = commonCodeCache.getCodes(groupCode);
        if (allCodes.isEmpty()) {
            log.warn("[FEE-MAPPING] {} 공통코드가 캐시에 없어 수가코드 매핑 점검을 건너뜁니다.", groupCode);
            return Set.of();
        }
        Set<String> missing = new TreeSet<>(allCodes);
        missing.removeAll(mappedCodes);
        if (missing.isEmpty()) {
            log.info("[FEE-MAPPING] {} 전체 {}건 수가코드 매핑 있음", groupCode, allCodes.size());
        } else {
            log.warn("[FEE-MAPPING] {} 중 수가코드 매핑이 없는 코드 {}건: {} — 이 항목은 청구가 나가지 않는다({}.{{코드}} 추가 필요, 수납팀 매핑표 요청 중)",
                    groupCode, missing.size(), missing, propertyPrefix);
        }
        return missing;
    }
}
