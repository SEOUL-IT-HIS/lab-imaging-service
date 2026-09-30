package kr.co.seoulit.his.labimagingservice.imagingorder.service;

import kr.co.seoulit.his.labimagingservice.imagingconsent.dto.ConsentFlagDto;
import kr.co.seoulit.his.labimagingservice.imagingconsent.service.ConsentRequirementPolicy;
import kr.co.seoulit.his.labimagingservice.imagingorder.dto.ImageWorklistStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kr.co.seoulit.his.labimagingservice.imagingorder.service.ImageWorklistService.decideNextStep;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 영상 워크리스트 단계·동의 상태 (5차 Phase 9, D13/D14).
 */
class ImageWorklistStepTest {

    @Test
    @DisplayName("D14: 촬영 파일이 있으면 동의가 철회돼도 CONSENT 로 되돌아가지 않는다 (READING 유지)")
    void acquiredStaysInReading() {
        assertThat(decideNextStep(1, 1, true, false, 1)).isEqualTo(ImageWorklistStep.READING);
    }

    @Test
    @DisplayName("기존 흐름 유지: 일정 → 동의 → 촬영")
    void normalFlow() {
        assertThat(decideNextStep(2, 1, true, false, 0)).isEqualTo(ImageWorklistStep.SCHEDULE);
        assertThat(decideNextStep(1, 1, true, false, 0)).isEqualTo(ImageWorklistStep.CONSENT);
        assertThat(decideNextStep(1, 1, true, true, 0)).isEqualTo(ImageWorklistStep.ACQUISITION);
    }

    @Test
    @DisplayName("동의가 필요 없는 오더는 CONSENT 단계를 건너뛴다 (Phase 9-1)")
    void consentNotRequiredSkips() {
        assertThat(decideNextStep(1, 1, false, false, 0)).isEqualTo(ImageWorklistStep.ACQUISITION);
    }

    @Test
    @DisplayName("정책: ALL 은 항상 필요, LISTED 는 목록 항목만, 모르는 모드는 ALL(fail-closed)")
    void policy() {
        ConsentRequirementPolicy all = new ConsentRequirementPolicy("ALL", List.of());
        ConsentRequirementPolicy listed = new ConsentRequirementPolicy("LISTED", List.of("CT01", " MR01 "));
        ConsentRequirementPolicy unknown = new ConsentRequirementPolicy("SOMETIMES", List.of());

        assertThat(all.isRequiredForItems(List.of("XR01"))).isTrue();
        assertThat(listed.isRequiredForItems(List.of("XR01"))).isFalse();
        assertThat(listed.isRequiredForItems(List.of("XR01", "MR01"))).isTrue();
        assertThat(unknown.isRequiredForItem("XR01")).isTrue();

        assertThat(listed.mayAcquire("XR01", false)).isTrue();
        assertThat(listed.mayAcquire("CT01", false)).isFalse();
        assertThat(all.mayAcquire("XR01", true)).isTrue();
    }

    @Test
    @DisplayName("배지: 유효 동의 없음 + 미철회 거부 → 거부, 철회 기록 → 철회. 유효 동의가 있으면 배지 없음")
    void badges() {
        ImageWorklistService service = new ImageWorklistService(null, null, null, null, null, null, null,
                new ConsentRequirementPolicy("ALL", List.of()));

        var refused = service.consentStatusOf(List.of(new ConsentFlagDto("o", "N", "N")), List.of("CT01"));
        assertThat(refused.valid()).isFalse();
        assertThat(refused.refused()).isTrue();

        var withdrawn = service.consentStatusOf(List.of(new ConsentFlagDto("o", "Y", "Y")), List.of("CT01"));
        assertThat(withdrawn.withdrawn()).isTrue();
        assertThat(withdrawn.refused()).isFalse();

        var reConsented = service.consentStatusOf(List.of(
                new ConsentFlagDto("o", "N", "N"), new ConsentFlagDto("o", "Y", "Y"), new ConsentFlagDto("o", "Y", "N")),
                List.of("CT01"));
        assertThat(reConsented.valid()).isTrue();
        assertThat(reConsented.refused()).isFalse();
        assertThat(reConsented.withdrawn()).isFalse();
    }
}
