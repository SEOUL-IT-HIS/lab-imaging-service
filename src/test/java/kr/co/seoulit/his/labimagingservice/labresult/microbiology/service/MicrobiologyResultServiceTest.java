package kr.co.seoulit.his.labimagingservice.labresult.microbiology.service;

import kr.co.seoulit.his.labimagingservice.billing.service.BillingChargeService;
import kr.co.seoulit.his.labimagingservice.labresult.service.LabResultTransmissionService;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabReceptionEntity;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabOrderItemRepository;
import kr.co.seoulit.his.labimagingservice.labresult.microbiology.dto.MicrobiologyResultCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.labresult.microbiology.dto.MicrobiologyResultUpdateRequestDto;
import kr.co.seoulit.his.labimagingservice.labresult.microbiology.dto.MicrobiologySusceptibilityDto;
import kr.co.seoulit.his.labimagingservice.labresult.microbiology.entity.MicrobiologyResultEntity;
import kr.co.seoulit.his.labimagingservice.labresult.microbiology.mapper.MicrobiologyResultMapper;
import kr.co.seoulit.his.labimagingservice.labresult.microbiology.repository.MicrobiologyResultRepository;
import kr.co.seoulit.his.labimagingservice.labresult.type.LabResultType;
import kr.co.seoulit.his.labimagingservice.labresult.type.LabResultTypeResolver;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.FitnessStatus;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenAcceptanceEntity;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenEntity;
import kr.co.seoulit.his.labimagingservice.labspecimen.repository.SpecimenAcceptanceRepository;
import kr.co.seoulit.his.labimagingservice.labspecimen.repository.SpecimenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 미생물 결과 등록 규칙 (UC-RST-02, ZP2-91 + 5차 "접수당 항목 1 = 결과 1" 제약).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MicrobiologyResultServiceTest {

    @Mock MicrobiologyResultRepository microbiologyResultRepository;
    @Mock SpecimenRepository specimenRepository;
    @Mock SpecimenAcceptanceRepository specimenAcceptanceRepository;
    @Mock LabOrderItemRepository labOrderItemRepository;
    @Mock CommonCodeCache commonCodeCache;
    @Mock MicrobiologyResultMapper mapper;
    @Mock BillingChargeService billingChargeService;
    @Mock LabResultTransmissionService labResultTransmissionService;

    MicrobiologyResultService service;
    SpecimenEntity specimen;
    SpecimenAcceptanceEntity acceptance;
    LabOrderItemEntity microItem;

    @BeforeEach
    void setUp() {
        service = new MicrobiologyResultService(microbiologyResultRepository, specimenRepository,
                specimenAcceptanceRepository, labOrderItemRepository, commonCodeCache,
                new LabResultTypeResolver(Map.of("05", LabResultType.MICROBIOLOGY)), mapper, billingChargeService, labResultTransmissionService);

        LabReceptionEntity reception = mock(LabReceptionEntity.class);
        when(reception.getLabReceptionId()).thenReturn("rec-1");
        when(reception.getReceptionNo()).thenReturn("LR-1");

        specimen = mock(SpecimenEntity.class);
        when(specimen.getSpecimenId()).thenReturn("sp-1");
        when(specimen.getLabReception()).thenReturn(reception);
        when(specimenRepository.findById("sp-1")).thenReturn(Optional.of(specimen));

        acceptance = mock(SpecimenAcceptanceEntity.class);
        when(acceptance.getFitnessStatusCode()).thenReturn(FitnessStatus.FIT);
        when(specimenAcceptanceRepository.findBySpecimen_SpecimenId("sp-1")).thenReturn(Optional.of(acceptance));

        microItem = givenItems("05", "01").get(0); // 미생물 1 + 일반 1
        when(microbiologyResultRepository.existsBySpecimen_LabReception_LabReceptionId("rec-1")).thenReturn(false);
        when(commonCodeCache.isValid(anyString(), anyString())).thenReturn(true);
        when(microbiologyResultRepository.save(any(MicrobiologyResultEntity.class))).thenAnswer(i -> i.getArgument(0));
    }

    private List<LabOrderItemEntity> givenItems(String... codes) {
        List<LabOrderItemEntity> items = new java.util.ArrayList<>();
        for (String code : codes) {
            LabOrderItemEntity item = mock(LabOrderItemEntity.class);
            when(item.getLabItemCode()).thenReturn(code);
            items.add(item);
        }
        when(labOrderItemRepository.findByReceptionNo("LR-1")).thenReturn(items);
        return items;
    }

    private MicrobiologyResultCreateRequestDto.MicrobiologyResultCreateRequestDtoBuilder positive() {
        return MicrobiologyResultCreateRequestDto.builder()
                .specimenId("sp-1").cultureStatusCode("03").organismCode("01").causativeYn("Y")
                .recordedById("emp-1");
    }

    private static MicrobiologySusceptibilityDto s(String antibiotic, String result) {
        return new MicrobiologySusceptibilityDto(antibiotic, result);
    }

    private void assertRejected(Runnable call, String code) {
        assertThatThrownBy(call::run).extracting("messageCode").isEqualTo(code);
        verify(microbiologyResultRepository, never()).save(any());
    }

    @Test
    @DisplayName("양성 + 균종 + 감수성 → 01(중간보고)로 저장")
    void createPositive() {
        service.createResult(positive().susceptibilities(List.of(s("01", "01"), s("02", "03"))).build());
        verify(microbiologyResultRepository).save(any(MicrobiologyResultEntity.class));
    }

    @Test
    @DisplayName("음성인데 균종을 보내면 LAB077")
    void negativeWithOrganism() {
        assertRejected(() -> service.createResult(positive().cultureStatusCode("02").build()), LabMessageCode.LAB077);
    }

    @Test
    @DisplayName("배양중인데 감수성을 보내면 LAB077")
    void incubatingWithSusceptibility() {
        assertRejected(() -> service.createResult(MicrobiologyResultCreateRequestDto.builder()
                .specimenId("sp-1").cultureStatusCode("01").susceptibilities(List.of(s("01", "01"))).build()),
                LabMessageCode.LAB077);
    }

    @Test
    @DisplayName("양성이어도 균종 없이 감수성을 보내면 LAB077")
    void susceptibilityWithoutOrganism() {
        assertRejected(() -> service.createResult(positive().organismCode(null).causativeYn(null)
                .susceptibilities(List.of(s("01", "01"))).build()), LabMessageCode.LAB077);
    }

    @Test
    @DisplayName("같은 항생제를 두 번 보내면 LAB078")
    void duplicateAntibiotic() {
        assertRejected(() -> service.createResult(positive()
                .susceptibilities(List.of(s("01", "01"), s("01", "03"))).build()), LabMessageCode.LAB078);
    }

    @Test
    @DisplayName("부적합(또는 미판정) 검체면 LAB076")
    void unfitSpecimen() {
        when(acceptance.getFitnessStatusCode()).thenReturn(FitnessStatus.UNFIT);
        assertRejected(() -> service.createResult(positive().build()), LabMessageCode.LAB076);
    }

    @Test
    @DisplayName("접수에 미생물 항목이 2개면 LAB074 (항목-결과 1:1 을 정할 수 없음)")
    void twoMicrobiologyItems() {
        givenItems("05", "05");
        assertRejected(() -> service.createResult(positive().build()), LabMessageCode.LAB074);
    }

    @Test
    @DisplayName("접수에 미생물 항목이 없으면 LAB074")
    void noMicrobiologyItem() {
        givenItems("01", "02");
        assertRejected(() -> service.createResult(positive().build()), LabMessageCode.LAB074);
    }

    @Test
    @DisplayName("접수에 이미 미생물 결과가 있으면 LAB075")
    void alreadyRegisteredForReception() {
        when(microbiologyResultRepository.existsBySpecimen_LabReception_LabReceptionId("rec-1")).thenReturn(true);
        assertRejected(() -> service.createResult(positive().build()), LabMessageCode.LAB075);
    }

    @Test
    @DisplayName("미생물 항목이 취소됐으면 LAB121 (05번 지시서 Phase 3)")
    void rejectsCancelledItem() {
        org.mockito.Mockito.doThrow(new LabImagingBusinessException(LabMessageCode.LAB121, "취소된 검사항목입니다."))
                .when(microItem).requireNotCancelled();

        assertRejected(() -> service.createResult(positive().build()), LabMessageCode.LAB121);
    }

    @Test
    @DisplayName("확정 — 미생물 항목이 취소됐으면 LAB121 (05번 지시서 Phase 3)")
    void confirmRejectsCancelledItem() {
        MicrobiologyResultEntity recorded = MicrobiologyResultEntity.builder()
                .cultureStatusCode("02").resultStatusCode("01").recordedAt(LocalDateTime.now()).recordedById("emp-1")
                .build();
        recorded.assignSpecimen(specimen);
        when(microbiologyResultRepository.findDetailById("mr-1")).thenReturn(Optional.of(recorded));
        org.mockito.Mockito.doThrow(new LabImagingBusinessException(LabMessageCode.LAB121, "취소된 검사항목입니다."))
                .when(microItem).requireNotCancelled();

        assertThatThrownBy(() -> service.confirmResult("mr-1", "emp-2"))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB121);
        verify(billingChargeService, never()).requestLabCharge(any());
    }

    @Test
    @DisplayName("확정된 결과를 수정하면 LAB040, 다시 확정하면 LAB041")
    void confirmedIsLocked() {
        MicrobiologyResultEntity confirmed = MicrobiologyResultEntity.builder()
                .cultureStatusCode("02").resultStatusCode("02").recordedAt(LocalDateTime.now()).recordedById("emp-1")
                .build();
        confirmed.assignSpecimen(specimen);
        when(microbiologyResultRepository.findDetailById("mr-1")).thenReturn(Optional.of(confirmed));

        assertThatThrownBy(() -> service.updateResult("mr-1",
                MicrobiologyResultUpdateRequestDto.builder().cultureStatusCode("02").build()))
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB040);
        assertThatThrownBy(() -> service.confirmResult("mr-1", "emp-2"))
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB041);
    }

    @Test
    @DisplayName("등록(01) 결과는 확정하면 02 가 되고 확정자가 기록된다")
    void confirm() {
        MicrobiologyResultEntity recorded = MicrobiologyResultEntity.builder()
                .cultureStatusCode("02").resultStatusCode("01").recordedAt(LocalDateTime.now()).recordedById("emp-1")
                .build();
        recorded.assignSpecimen(specimen);
        when(microbiologyResultRepository.findDetailById("mr-1")).thenReturn(Optional.of(recorded));

        service.confirmResult("mr-1", "emp-2");

        assertThat(recorded.getResultStatusCode()).isEqualTo("02");
        assertThat(recorded.getConfirmedById()).isEqualTo("emp-2");
        // 확정하면 결과 전송이 요청된다 (5차 Phase 6)
        verify(labResultTransmissionService).transmitMicrobiology(eq(recorded), any());
    }
}
