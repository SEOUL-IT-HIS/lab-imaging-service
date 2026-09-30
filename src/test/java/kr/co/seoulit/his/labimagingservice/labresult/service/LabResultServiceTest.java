package kr.co.seoulit.his.labimagingservice.labresult.service;

import kr.co.seoulit.his.labimagingservice.billing.service.BillingChargeService;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabReceptionEntity;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabOrderItemRepository;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabReceptionRepository;
import kr.co.seoulit.his.labimagingservice.labresult.dto.LabResultCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.labresult.entity.LabResultEntity;
import kr.co.seoulit.his.labimagingservice.labresult.mapper.LabResultMapper;
import kr.co.seoulit.his.labimagingservice.labresult.repository.LabResultRepository;
import kr.co.seoulit.his.labimagingservice.labresult.type.LabResultType;
import kr.co.seoulit.his.labimagingservice.labresult.type.LabResultTypeResolver;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 결과 등록 전 적합성 판정 서버 검증 (후속조치 #10, LAB066).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LabResultServiceTest {

    private static final String ITEM_ID = "item-1";
    private static final String ORDER_ID = "order-1";
    private static final String RECEPTION_ID = "rec-1";

    @Mock LabResultRepository labResultRepository;
    @Mock LabOrderItemRepository labOrderItemRepository;
    @Mock LabReceptionRepository labReceptionRepository;
    @Mock SpecimenRepository specimenRepository;
    @Mock SpecimenAcceptanceRepository specimenAcceptanceRepository;
    @Mock LabResultMapper labResultMapper;
    @Mock CommonCodeCache commonCodeCache;
    @Mock BillingChargeService billingChargeService;
    @Mock LabResultTransmissionService labResultTransmissionService;

    LabResultService service;
    LabReceptionEntity reception;

    @BeforeEach
    void setUp() {
        // 검사항목 "01" 은 매핑에 없으므로 GENERAL — 일반검사 결과 등록 경로를 탄다.
        service = new LabResultService(labResultRepository, labOrderItemRepository, labReceptionRepository,
                specimenRepository, specimenAcceptanceRepository, labResultMapper, commonCodeCache,
                billingChargeService, labResultTransmissionService, new LabResultTypeResolver(Map.of("05", LabResultType.MICROBIOLOGY)));

        LabOrderEntity order = mock(LabOrderEntity.class);
        when(order.getLabOrderId()).thenReturn(ORDER_ID);
        LabOrderItemEntity item = mock(LabOrderItemEntity.class);
        when(item.getLabOrder()).thenReturn(order);
        when(item.getLabItemCode()).thenReturn("01");

        reception = mock(LabReceptionEntity.class);
        when(reception.getLabReceptionId()).thenReturn(RECEPTION_ID);

        when(labOrderItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
        when(labResultRepository.existsByLabOrderItem_LabOrderItemId(ITEM_ID)).thenReturn(false);
        when(commonCodeCache.isValid(anyString(), anyString())).thenReturn(true);
        when(labResultRepository.save(any(LabResultEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private LabResultCreateRequestDto request() {
        return LabResultCreateRequestDto.builder()
                .labOrderItemId(ITEM_ID)
                .resultValue("4.2")
                .recordedById("emp-1")
                .build();
    }

    private void givenAcceptedReceptions(List<LabReceptionEntity> receptions) {
        when(labReceptionRepository.findByLabOrder_LabOrderIdAndReceptionStatusCodeOrderByCreatedAtDesc(
                eq(ORDER_ID), anyString())).thenReturn(receptions);
    }

    private SpecimenEntity specimen(String id) {
        SpecimenEntity specimen = mock(SpecimenEntity.class);
        when(specimen.getSpecimenId()).thenReturn(id);
        when(specimen.getLabReception()).thenReturn(reception);
        return specimen;
    }

    private SpecimenAcceptanceEntity acceptance(SpecimenEntity specimen, String recollectionYn) {
        SpecimenAcceptanceEntity acceptance = mock(SpecimenAcceptanceEntity.class);
        when(acceptance.getSpecimen()).thenReturn(specimen);
        when(acceptance.getRecollectionRequestedYn()).thenReturn(recollectionYn);
        return acceptance;
    }

    @Test
    @DisplayName("처리 대상 접수가 없으면 LAB066")
    void noReception() {
        givenAcceptedReceptions(List.of());

        assertThatThrownBy(() -> service.createLabResult(request()))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB066);
        verify(labResultRepository, never()).save(any());
    }

    @Test
    @DisplayName("검체는 있는데 판정이 없으면 LAB066")
    void notJudged() {
        givenAcceptedReceptions(List.of(reception));
        SpecimenEntity s1 = specimen("s1");
        when(specimenRepository.findByLabReception_LabReceptionIdIn(anyList())).thenReturn(List.of(s1));
        when(specimenAcceptanceRepository.findBySpecimen_SpecimenIdIn(anyList())).thenReturn(List.of());

        assertThatThrownBy(() -> service.createLabResult(request()))
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB066);
        verify(labResultRepository, never()).save(any());
    }

    @Test
    @DisplayName("재채취 요청이 해소되지 않았으면 LAB066")
    void recollectionPending() {
        givenAcceptedReceptions(List.of(reception));
        SpecimenEntity s1 = specimen("s1");
        when(specimenRepository.findByLabReception_LabReceptionIdIn(anyList())).thenReturn(List.of(s1));
        // ⚠ 스텁된 mock 은 다른 when(...).thenReturn(...) 안에서 만들면 안 된다(UnfinishedStubbing). 먼저 만든다.
        SpecimenAcceptanceEntity a1 = acceptance(s1, "Y");
        when(specimenAcceptanceRepository.findBySpecimen_SpecimenIdIn(anyList())).thenReturn(List.of(a1));

        assertThatThrownBy(() -> service.createLabResult(request()))
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB066);
    }

    @Test
    @DisplayName("전부 판정됐고 재채취 요청이 없으면 저장된다")
    void ready() {
        givenAcceptedReceptions(List.of(reception));
        SpecimenEntity s1 = specimen("s1");
        when(specimenRepository.findByLabReception_LabReceptionIdIn(anyList())).thenReturn(List.of(s1));
        SpecimenAcceptanceEntity a1 = acceptance(s1, "N");
        when(specimenAcceptanceRepository.findBySpecimen_SpecimenIdIn(anyList())).thenReturn(List.of(a1));

        service.createLabResult(request());

        verify(labResultRepository).save(any(LabResultEntity.class));
    }
}
