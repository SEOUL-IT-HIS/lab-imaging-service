package kr.co.seoulit.his.labimagingservice.imagingconsent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 동의 필요 여부 판정 — 한 곳에 모은다. UC-IMG-05 (5차 Phase 9-1, D13 / D14)
 *
 * 쓰는 곳: ImageWorklistService.decideNextStep (CONSENT 단계를 거칠지), ImageFileService (업로드 LAB052 검사).
 * ⚠ 두 곳이 따로 판단하면 "워크리스트는 촬영 단계인데 업로드는 동의 없다고 막는" 식으로 어긋난다.
 *   동의 관련 판단을 추가할 때는 반드시 여기에 추가한다.
 *
 * ── 판정 규칙 (설정)
 *   app.imaging.consent.required-mode = ALL (기본)   : 모든 촬영에 동의가 필요 — 5차 이전 동작 그대로
 *                                     = LISTED       : required-item-codes 에 적힌 촬영항목코드만 동의 필요
 *   app.imaging.consent.required-item-codes = CT01,MR01 … (IMG_ITEM_CD)
 *   ⚠ admin 공통코드에 "동의필요" 속성 컬럼이 없어 설정으로 둔다(Phase 0 조사 결과 — COMMON_CODE 에 속성 컬럼 없음).
 *   ⚠ 모르는 모드 값이면 ALL 로 본다(fail-closed — 동의를 건너뛰는 쪽으로 틀리면 안 된다).
 *
 * ── 유효한 동의 = 동의함(Y) + 미철회(N). 판정 쿼리는 ConsentRepository.findOrderIdsWithValidConsent (D13)
 */
@Slf4j
@Component
public class ConsentRequirementPolicy {

    static final String MODE_ALL = "ALL";
    static final String MODE_LISTED = "LISTED";

    private final boolean listedMode;
    private final Set<String> requiredItemCodes;

    public ConsentRequirementPolicy(@Value("${app.imaging.consent.required-mode:ALL}") String mode,
                                    @Value("${app.imaging.consent.required-item-codes:}") List<String> requiredItemCodes) {
        this.listedMode = MODE_LISTED.equalsIgnoreCase(mode == null ? "" : mode.trim());
        if (!listedMode && !MODE_ALL.equalsIgnoreCase(mode == null ? "" : mode.trim())) {
            log.warn("[CONSENT] 알 수 없는 app.imaging.consent.required-mode={} — ALL(모두 동의 필요)로 동작합니다.", mode);
        }
        this.requiredItemCodes = requiredItemCodes == null ? Set.of() : requiredItemCodes.stream()
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        if (listedMode && this.requiredItemCodes.isEmpty()) {
            log.warn("[CONSENT] required-mode=LISTED 인데 required-item-codes 가 비어 있습니다 — 어떤 촬영도 동의를 요구하지 않습니다.");
        }
    }

    /** 촬영항목 1개가 동의를 요구하는가 (업로드 검사용) */
    public boolean isRequiredForItem(String imageItemCode) {
        return !listedMode || requiredItemCodes.contains(imageItemCode);
    }

    /** 오더(촬영항목 여러 개)가 동의를 요구하는가 — 항목 하나라도 요구하면 요구한다 (워크리스트용) */
    public boolean isRequiredForItems(Collection<String> imageItemCodes) {
        return !listedMode || imageItemCodes.stream().anyMatch(requiredItemCodes::contains);
    }

    /**
     * 촬영(업로드)을 진행해도 되는가.
     * ⚠ 이미 촬영된 항목(재촬영 파일 추가)도 같은 규칙이다 — 철회 후에는 새 촬영을 막는다.
     *   D14 의 "촬영 후 철회 허용"은 이미 올라간 영상을 지우지 않는다는 뜻이지 촬영을 계속한다는 뜻이 아니다.
     */
    public boolean mayAcquire(String imageItemCode, boolean hasValidConsent) {
        return hasValidConsent || !isRequiredForItem(imageItemCode);
    }
}
