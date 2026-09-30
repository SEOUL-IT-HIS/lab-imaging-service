package kr.co.seoulit.his.labimagingservice.imagingconsent.service;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.imagingconsent.dto.ConsentCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingconsent.dto.ConsentWithdrawRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingconsent.entity.ConsentEntity;
import kr.co.seoulit.his.labimagingservice.imagingconsent.mapper.ConsentMapper;
import kr.co.seoulit.his.labimagingservice.imagingconsent.repository.ConsentRepository;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageOrderEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.repository.ImageOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 동의 중복 판정 (D13) — 중복은 "유효한 동의(Y) + 미철회"만 대상이다. 거부 기록은 재동의를 막지 않는다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConsentServiceTest {

    @Mock ConsentRepository consentRepository;
    @Mock ImageOrderRepository imageOrderRepository;
    @Mock ConsentMapper consentMapper;
    @Mock CommonCodeCache commonCodeCache;

    @InjectMocks ConsentService consentService;

    private final ConsentCreateRequestDto request = ConsentCreateRequestDto.builder()
            .imageOrderId("io-1")
            .consentTypeCode("01")
            .documentTemplateId("tpl-1")
            .consentYn("Y")
            .consentDt(LocalDate.now())
            .signedByName("Kim")
            .witnessId("emp-1")
            .build();

    @BeforeEach
    void setUp() {
        when(imageOrderRepository.findById("io-1")).thenReturn(Optional.of(mock(ImageOrderEntity.class)));
        when(commonCodeCache.isValid(anyString(), anyString())).thenReturn(true);
        when(consentRepository.save(any(ConsentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("중복 판정은 consentYn='Y' + withdrawnYn='N' 조건으로 조회한다 (거부 기록 제외)")
    void duplicateCheckUsesConsentedOnly() {
        consentService.createConsent(request);

        verify(consentRepository).existsByImageOrder_ImageOrderIdAndConsentTypeCodeAndConsentYnAndWithdrawnYn(
                "io-1", "01", "Y", "N");
    }

    @Test
    @DisplayName("유효한 동의가 이미 있으면 LAB031")
    void duplicateRejected() {
        when(consentRepository.existsByImageOrder_ImageOrderIdAndConsentTypeCodeAndConsentYnAndWithdrawnYn(
                "io-1", "01", "Y", "N")).thenReturn(true);

        assertThatThrownBy(() -> consentService.createConsent(request))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB031);
    }

    @Test
    @DisplayName("거부 사유는 거부(N) 건에만 저장하고 동의(Y) 건에는 버린다 (Phase 9-2)")
    void refusalNoteOnlyForRefusal() {
        ArgumentCaptor<ConsentEntity> saved = ArgumentCaptor.forClass(ConsentEntity.class);

        consentService.createConsent(request.toBuilder().refusalNote("ignored").build());
        consentService.createConsent(request.toBuilder().consentYn("N").refusalNote("  allergy  ").build());

        verify(consentRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getRefusalNote()).isNull();
        assertThat(saved.getAllValues().get(1).getRefusalNote()).isEqualTo("allergy");
    }

    // ---------- 철회 (Phase 9-3, D14) ----------

    private ConsentEntity consent(String consentYn, String withdrawnYn, String itemStatus) {
        ImageOrderItemEntity item = mock(ImageOrderItemEntity.class);
        when(item.getItemStatusCode()).thenReturn(itemStatus);
        ImageOrderEntity order = mock(ImageOrderEntity.class);
        when(order.getOrderItems()).thenReturn(List.of(item));
        ConsentEntity entity = ConsentEntity.builder().consentYn(consentYn).withdrawnYn(withdrawnYn).build();
        entity.assignImageOrder(order);
        when(consentRepository.findById("c-1")).thenReturn(Optional.of(entity));
        return entity;
    }

    private final ConsentWithdrawRequestDto withdraw = ConsentWithdrawRequestDto.builder()
            .withdrawnReasonCode("01").withdrawnById("emp-9").build();

    @Test
    @DisplayName("철회 → withdrawn_yn=Y, 사유·처리자·서버시각 기록. 촬영 전이면 acquired=false")
    void withdrawBeforeAcquisition() {
        ConsentEntity entity = consent("Y", "N", "REGISTERED");

        ConsentService.WithdrawResult result = consentService.withdrawConsent("c-1", withdraw);

        assertThat(entity.getWithdrawnYn()).isEqualTo("Y");
        assertThat(entity.getWithdrawnReasonCode()).isEqualTo("01");
        assertThat(entity.getWithdrawnById()).isEqualTo("emp-9");
        assertThat(entity.getWithdrawnAt()).isNotNull();
        assertThat(result.acquired()).isFalse();
        verify(commonCodeCache).isValid("CONSENT_WITHDRAW_CD", "01");
    }

    @Test
    @DisplayName("촬영 후 철회도 허용하고 acquired=true 로 알린다 (D14 — 촬영 영상 유지 안내)")
    void withdrawAfterAcquisition() {
        consent("Y", "N", "ACQUIRED");

        assertThat(consentService.withdrawConsent("c-1", withdraw).acquired()).isTrue();
    }

    @Test
    @DisplayName("이미 철회 LAB095 / 거부 기록 LAB096 / 없는 동의 LAB094 / 모르는 사유코드 LAB017")
    void withdrawRejections() {
        consent("Y", "Y", "REGISTERED");
        assertThatThrownBy(() -> consentService.withdrawConsent("c-1", withdraw))
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB095);

        consent("N", "N", "REGISTERED");
        assertThatThrownBy(() -> consentService.withdrawConsent("c-1", withdraw))
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB096);

        assertThatThrownBy(() -> consentService.withdrawConsent("none", withdraw))
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB094);

        consent("Y", "N", "REGISTERED");
        when(commonCodeCache.isValid("CONSENT_WITHDRAW_CD", "01")).thenReturn(false);
        assertThatThrownBy(() -> consentService.withdrawConsent("c-1", withdraw))
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB017);
    }
}
