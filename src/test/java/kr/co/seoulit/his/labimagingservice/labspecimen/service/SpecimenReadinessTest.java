package kr.co.seoulit.his.labimagingservice.labspecimen.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 결과 등록 가능 판정 규칙 (후속조치 #10).
 * 워크리스트 RESULT 단계와 결과 등록 서버 검증(LAB066)이 같이 쓰는 규칙이라, 경계값을 고정해 둔다.
 */
class SpecimenReadinessTest {

    @Test
    @DisplayName("검체가 없으면 결과 등록 불가")
    void noSpecimen() {
        assertThat(SpecimenReadiness.isReadyForResult(0, 0, 0)).isFalse();
    }

    @Test
    @DisplayName("미판정 검체가 남아 있으면 결과 등록 불가")
    void notAllJudged() {
        assertThat(SpecimenReadiness.isReadyForResult(2, 1, 0)).isFalse();
    }

    @Test
    @DisplayName("전부 판정됐고 재채취 요청이 없으면 결과 등록 가능")
    void allJudged() {
        assertThat(SpecimenReadiness.isReadyForResult(1, 1, 0)).isTrue();
    }

    @Test
    @DisplayName("재채취 요청이 해소되지 않았으면 결과 등록 불가 (검체 1 / 요청 1)")
    void recollectionPending() {
        assertThat(SpecimenReadiness.isRecollectionPending(1, 1)).isTrue();
        assertThat(SpecimenReadiness.isReadyForResult(1, 1, 1)).isFalse();
    }

    @Test
    @DisplayName("재채취로 검체가 더 들어왔고 새 검체도 판정됐으면 결과 등록 가능 (검체 2 / 요청 1)")
    void recollectionResolved() {
        assertThat(SpecimenReadiness.isRecollectionPending(2, 1)).isFalse();
        assertThat(SpecimenReadiness.isReadyForResult(2, 2, 1)).isTrue();
    }

    @Test
    @DisplayName("재채취 요청이 한 번도 없으면 검체 0건이어도 '재채취 대기'가 아니다")
    void zeroZeroIsNotPending() {
        assertThat(SpecimenReadiness.isRecollectionPending(0, 0)).isFalse();
    }
}
