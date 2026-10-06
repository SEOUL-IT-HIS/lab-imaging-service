package kr.co.seoulit.his.labimagingservice.imagingorder.service;

import kr.co.seoulit.his.labimagingservice.imagingorder.dto.ImageWorklistStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 영상 워크리스트 다음 단계 판정 (06번 지시서 Phase 3 — 동의 불필요 촬영이 CONSENT 단계를 건너뛰는지).
 *
 * ⚠ ImageWorklistService.decideNextStep 은 package-private static 메서드라 같은 패키지에서만
 *   직접 부를 수 있다 — 그래서 이 테스트를 ImageWorklistService 가 있는 패키지에 둔다
 *   (레포지토리·매퍼를 목으로 채워 서비스 전체를 띄울 필요가 없다).
 */
class ImageWorklistServiceDecideNextStepTest {

    @Test
    @DisplayName("일정이 모든 항목에 안 잡혀 있으면 SCHEDULE — 동의 상태와 무관하다")
    void scheduleIncompleteStaysAtSchedule() {
        assertThat(ImageWorklistService.decideNextStep(2, 1, true, false, 0))
                .isEqualTo(ImageWorklistStep.SCHEDULE);
        assertThat(ImageWorklistService.decideNextStep(2, 1, false, false, 0))
                .isEqualTo(ImageWorklistStep.SCHEDULE);
    }

    @Test
    @DisplayName("동의 불필요 + 동의 없음 → CONSENT 단계를 건너뛰고 ACQUISITION 으로 간다")
    void consentNotRequiredSkipsConsentStep() {
        ImageWorklistStep step = ImageWorklistService.decideNextStep(1, 1, false, false, 0);

        assertThat(step).isEqualTo(ImageWorklistStep.ACQUISITION);
    }

    @Test
    @DisplayName("동의 필요 + 동의 없음 → CONSENT")
    void consentRequiredAndMissingStaysAtConsent() {
        ImageWorklistStep step = ImageWorklistService.decideNextStep(1, 1, true, false, 0);

        assertThat(step).isEqualTo(ImageWorklistStep.CONSENT);
    }

    @Test
    @DisplayName("동의 필요 + 유효한 동의 있음 → ACQUISITION")
    void consentRequiredAndValidMovesToAcquisition() {
        ImageWorklistStep step = ImageWorklistService.decideNextStep(1, 1, true, true, 0);

        assertThat(step).isEqualTo(ImageWorklistStep.ACQUISITION);
    }

    @Test
    @DisplayName("동의 필요 + 거부·철회(=유효 동의 없음) → 기존 동작 그대로 CONSENT 에 머문다")
    void consentRequiredWithRefusalOrWithdrawalStaysAtConsent() {
        // 거부·철회는 모두 "유효한 동의 없음"(hasConsent=false) 으로 들어온다 — 배지만 따로 붙을 뿐
        // decideNextStep 입력 자체는 동의 없음과 같다(ImageWorklistService.consentStatusOf 참고).
        ImageWorklistStep step = ImageWorklistService.decideNextStep(1, 1, true, false, 0);

        assertThat(step).isEqualTo(ImageWorklistStep.CONSENT);
    }

    @Test
    @DisplayName("촬영 파일이 하나라도 있으면 동의 상태와 무관하게 READING — 이미 촬영된 영상은 판독해야 한다")
    void anyImageFilePrioritizesReadingOverConsent() {
        assertThat(ImageWorklistService.decideNextStep(1, 1, true, false, 1))
                .isEqualTo(ImageWorklistStep.READING);
        assertThat(ImageWorklistService.decideNextStep(1, 1, false, false, 1))
                .isEqualTo(ImageWorklistStep.READING);
    }
}
