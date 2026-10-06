package kr.co.seoulit.his.labimagingservice.imagingconsent.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동의 필요 여부 판정 (06번 지시서 Phase 0/3 — UC-IMG-05, 5차 Phase 9-1).
 *
 * ⚠ @Value 생성자 파라미터는 Spring 빈 등록 시의 설정 바인딩용이라, 이 테스트처럼 new 로 바로
 *   만들면 어노테이션은 무시되고 전달한 값이 그대로 쓰인다(다른 *Test 클래스들의 관례와 같다).
 */
class ConsentRequirementPolicyTest {

    @Test
    @DisplayName("ALL 모드 — 모든 항목이 동의를 요구한다")
    void allModeRequiresEveryItem() {
        ConsentRequirementPolicy policy = new ConsentRequirementPolicy("ALL", List.of());

        assertThat(policy.isRequiredForItem("04")).isTrue();
        assertThat(policy.isRequiredForItems(List.of("04"))).isTrue();
        assertThat(policy.mayAcquire("04", false)).isFalse();
    }

    @Test
    @DisplayName("LISTED 모드 — 목록에 없는 항목은 동의가 필요 없다")
    void listedModeItemNotInListIsNotRequired() {
        ConsentRequirementPolicy policy = new ConsentRequirementPolicy("LISTED", List.of("01", "02"));

        assertThat(policy.isRequiredForItem("04")).isFalse();
        assertThat(policy.mayAcquire("04", false)).isTrue(); // 동의 없이도 촬영 가능
    }

    @Test
    @DisplayName("LISTED 모드 — 목록에 있는 항목은 동의가 필요하다")
    void listedModeItemInListIsRequired() {
        ConsentRequirementPolicy policy = new ConsentRequirementPolicy("LISTED", List.of("01", "02"));

        assertThat(policy.isRequiredForItem("01")).isTrue();
        assertThat(policy.mayAcquire("01", false)).isFalse();
        assertThat(policy.mayAcquire("01", true)).isTrue();
    }

    @Test
    @DisplayName("LISTED 모드 — 오더에 필요 항목과 불필요 항목이 섞이면 오더 전체는 필요하다고 본다")
    void listedModeMixedItemsRequiresWholeOrder() {
        ConsentRequirementPolicy policy = new ConsentRequirementPolicy("LISTED", List.of("01"));

        assertThat(policy.isRequiredForItems(List.of("01", "04"))).isTrue();
        assertThat(policy.isRequiredForItems(List.of("04", "03"))).isFalse();
    }

    @Test
    @DisplayName("LISTED 모드인데 목록이 비어 있으면 아무 항목도 동의를 요구하지 않는다")
    void listedModeWithEmptyListRequiresNothing() {
        ConsentRequirementPolicy policy = new ConsentRequirementPolicy("LISTED", List.of());

        assertThat(policy.isRequiredForItem("01")).isFalse();
        assertThat(policy.isRequiredForItems(List.of("01", "02", "03"))).isFalse();
    }

    @Test
    @DisplayName("모르는 모드 값은 ALL 로 본다 (fail-closed)")
    void unknownModeFallsBackToAll() {
        ConsentRequirementPolicy policy = new ConsentRequirementPolicy("UNKNOWN_MODE", List.of("01"));

        assertThat(policy.isRequiredForItem("99")).isTrue();
    }

    @Test
    @DisplayName("모드값 대소문자·공백은 무시한다 (listed / 공백 포함 항목코드)")
    void modeIsCaseInsensitiveAndItemCodesAreTrimmed() {
        ConsentRequirementPolicy policy = new ConsentRequirementPolicy("  listed  ", List.of(" 01 ", "02"));

        assertThat(policy.isRequiredForItem("01")).isTrue();
        assertThat(policy.isRequiredForItem("03")).isFalse();
    }

    @Test
    @DisplayName("mode 가 null 이면 ALL 로 본다")
    void nullModeFallsBackToAll() {
        ConsentRequirementPolicy policy = new ConsentRequirementPolicy(null, null);

        assertThat(policy.isRequiredForItem("anything")).isTrue();
    }
}
