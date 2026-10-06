package kr.co.seoulit.his.labimagingservice.labresult.service;

import kr.co.seoulit.his.labimagingservice.billing.service.BillingChargeService;
import kr.co.seoulit.his.labimagingservice.businessdelegate.patient.PatientServiceBusinessDelegate;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabReceptionEntity;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabOrderItemRepository;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabReceptionRepository;
import kr.co.seoulit.his.labimagingservice.labresult.dto.LabResultCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.labresult.dto.LabResultDetailDto;
import kr.co.seoulit.his.labimagingservice.labresult.dto.LabResultDetailRequestDto;
import kr.co.seoulit.his.labimagingservice.labresult.dto.LabResultSummaryDto;
import kr.co.seoulit.his.labimagingservice.labresult.dto.LabResultUpdateRequestDto;
import kr.co.seoulit.his.labimagingservice.labresult.entity.LabReferenceRangeEntity;
import kr.co.seoulit.his.labimagingservice.labresult.entity.LabResultDetailEntity;
import kr.co.seoulit.his.labimagingservice.labresult.entity.LabResultEntity;
import kr.co.seoulit.his.labimagingservice.labresult.entity.LabResultItemRuleEntity;
import kr.co.seoulit.his.labimagingservice.labresult.mapper.LabResultMapper;
import kr.co.seoulit.his.labimagingservice.labresult.repository.LabReferenceRangeRepository;
import kr.co.seoulit.his.labimagingservice.labresult.repository.LabResultDetailRepository;
import kr.co.seoulit.his.labimagingservice.labresult.repository.LabResultItemRuleRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
 * 결과 등록 전 적합성 판정 서버 검증 (후속조치 #10, LAB066) + 결과항목(상세) (6차, 2-2).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LabResultServiceTest {

    private static final String ITEM_ID = "item-1";
    private static final String ORDER_ID = "order-1";
    private static final String RECEPTION_ID = "rec-1";
    private static final String PATIENT_ID = "patient-1";
    /** 결과항목이 있는 검사(CBC) — setUp 의 기본 항목 "01" 과 구분해서 쓴다. */
    private static final String CBC_ITEM_ID = "item-cbc";

    @Mock LabResultRepository labResultRepository;
    @Mock LabOrderItemRepository labOrderItemRepository;
    @Mock LabReceptionRepository labReceptionRepository;
    @Mock SpecimenRepository specimenRepository;
    @Mock SpecimenAcceptanceRepository specimenAcceptanceRepository;
    @Mock LabResultMapper labResultMapper;
    @Mock CommonCodeCache commonCodeCache;
    @Mock BillingChargeService billingChargeService;
    @Mock LabResultTransmissionService labResultTransmissionService;
    @Mock LabResultItemRuleRepository labResultItemRuleRepository;
    @Mock LabReferenceRangeRepository labReferenceRangeRepository;
    @Mock LabResultDetailRepository labResultDetailRepository;
    @Mock PatientServiceBusinessDelegate patientServiceBusinessDelegate;

    LabResultService service;
    LabReceptionEntity reception;
    LabOrderEntity order;
    LabOrderItemEntity item;

    @BeforeEach
    void setUp() {
        // 검사항목 "01" 은 매핑에 없으므로 GENERAL — 일반검사 결과 등록 경로를 탄다.
        service = new LabResultService(labResultRepository, labOrderItemRepository, labReceptionRepository,
                specimenRepository, specimenAcceptanceRepository, labResultMapper, commonCodeCache,
                billingChargeService, labResultTransmissionService,
                new LabResultTypeResolver(Map.of("05", LabResultType.MICROBIOLOGY)),
                labResultItemRuleRepository, labReferenceRangeRepository, labResultDetailRepository,
                patientServiceBusinessDelegate);

        order = mock(LabOrderEntity.class);
        when(order.getLabOrderId()).thenReturn(ORDER_ID);
        when(order.getPatientId()).thenReturn(PATIENT_ID);
        item = mock(LabOrderItemEntity.class);
        when(item.getLabOrder()).thenReturn(order);
        when(item.getLabItemCode()).thenReturn("01");

        reception = mock(LabReceptionEntity.class);
        when(reception.getLabReceptionId()).thenReturn(RECEPTION_ID);

        when(labOrderItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
        when(labResultRepository.existsByLabOrderItem_LabOrderItemId(ITEM_ID)).thenReturn(false);
        when(commonCodeCache.isValid(anyString(), anyString())).thenReturn(true);
        when(labResultRepository.save(any(LabResultEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        // "01"은 결과항목 규칙이 없는 검사(기존 방식)로 둔다 — 명시하지 않아도 Mockito 기본값(빈 리스트)과 같지만
        // 의도를 분명히 드러낸다.
        when(labResultItemRuleRepository.findByTestTypeCodeAndUseYnOrderByItemSeqAsc(eq("01"), anyString()))
                .thenReturn(List.of());
        // 매퍼는 실제 구현이 아니라 mock 이므로, toResponseWithDetails 가 안전하게 동작하도록
        // 최소한의 응답 골격을 돌려준다(필드 값 자체는 각 테스트가 필요하면 ArgumentCaptor 로 저장 요청을 검증한다).
        when(labResultMapper.toResponse(any(LabResultEntity.class)))
                .thenAnswer(inv -> LabResultSummaryDto.builder().build());
        when(labResultMapper.toDetailResponseList(any())).thenAnswer(inv -> List.of());
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
        readySpecimen();

        service.createLabResult(request());

        verify(labResultRepository).save(any(LabResultEntity.class));
    }

    @Test
    @DisplayName("취소된 검사항목이면 LAB121 — 결과를 등록할 수 없다 (05번 지시서 Phase 3)")
    void createRejectsCancelledItem() {
        readySpecimen();
        org.mockito.Mockito.doThrow(new LabImagingBusinessException(LabMessageCode.LAB121, "취소된 검사항목입니다."))
                .when(item).requireNotCancelled();

        assertThatThrownBy(() -> service.createLabResult(request()))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB121);
        verify(labResultRepository, never()).save(any());
    }

    @Test
    @DisplayName("확정 — 취소된 검사항목이면 LAB121 (05번 지시서 Phase 3)")
    void confirmRejectsCancelledItem() {
        LabResultEntity recorded = LabResultEntity.builder()
                .resultValue("4.2").abnormalYn("N").resultStatusCode("01")
                .recordedAt(java.time.LocalDateTime.now()).recordedById("emp-1")
                .build();
        recorded.assignLabOrderItem(item);
        when(labResultRepository.findById("result-1")).thenReturn(Optional.of(recorded));
        org.mockito.Mockito.doThrow(new LabImagingBusinessException(LabMessageCode.LAB121, "취소된 검사항목입니다."))
                .when(item).requireNotCancelled();

        assertThatThrownBy(() -> service.confirmLabResult("result-1", "emp-2"))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB121);
        verify(billingChargeService, never()).requestLabCharge(any());
    }

    // ==================================================================
    // 결과항목(상세) — 6차, 2-2
    // ==================================================================

    private void readySpecimen() {
        givenAcceptedReceptions(List.of(reception));
        SpecimenEntity s1 = specimen("s1");
        when(specimenRepository.findByLabReception_LabReceptionIdIn(anyList())).thenReturn(List.of(s1));
        SpecimenAcceptanceEntity a1 = acceptance(s1, "N");
        when(specimenAcceptanceRepository.findBySpecimen_SpecimenIdIn(anyList())).thenReturn(List.of(a1));
    }

    /** CBC(02) 검사항목을 등록해 결과항목 방식 테스트의 대상 항목으로 쓴다. */
    private LabOrderItemEntity cbcItem() {
        LabOrderItemEntity item = mock(LabOrderItemEntity.class);
        when(item.getLabOrder()).thenReturn(order);
        when(item.getLabItemCode()).thenReturn("02");
        when(labOrderItemRepository.findById(CBC_ITEM_ID)).thenReturn(Optional.of(item));
        when(labResultRepository.existsByLabOrderItem_LabOrderItemId(CBC_ITEM_ID)).thenReturn(false);
        return item;
    }

    private static LabResultItemRuleEntity itemRule(String testTypeCode, String resultItemCode,
                                                     int itemSeq, String defaultUnit) {
        LabResultItemRuleEntity rule = mock(LabResultItemRuleEntity.class);
        when(rule.getTestTypeCode()).thenReturn(testTypeCode);
        when(rule.getResultItemCode()).thenReturn(resultItemCode);
        when(rule.getItemSeq()).thenReturn(itemSeq);
        when(rule.getDefaultUnit()).thenReturn(defaultUnit);
        return rule;
    }

    private static LabReferenceRangeEntity referenceRange(String resultItemCode, String sexCode, String range) {
        LabReferenceRangeEntity entity = mock(LabReferenceRangeEntity.class);
        when(entity.getResultItemCode()).thenReturn(resultItemCode);
        when(entity.getSexCode()).thenReturn(sexCode);
        when(entity.getReferenceRange()).thenReturn(range);
        return entity;
    }

    private static LabResultDetailRequestDto detailRequest(String resultItemCode, String resultValue) {
        return LabResultDetailRequestDto.builder().resultItemCode(resultItemCode).resultValue(resultValue).build();
    }

    private LabResultCreateRequestDto cbcRequest(List<LabResultDetailRequestDto> details) {
        return LabResultCreateRequestDto.builder()
                .labOrderItemId(CBC_ITEM_ID)
                .recordedById("emp-1")
                .details(details)
                .build();
    }

    /** CBC 규칙(02 백혈구/03 적혈구/04 혈소판) 3개를 등록한다. */
    private void givenCbcRules() {
        List<LabResultItemRuleEntity> rules = List.of(
                itemRule("02", "02", 1, "x10^3/uL"),
                itemRule("02", "03", 2, "x10^6/uL"),
                itemRule("02", "04", 3, "x10^3/uL"));
        when(labResultItemRuleRepository.findByTestTypeCodeAndUseYnOrderByItemSeqAsc(eq("02"), anyString()))
                .thenReturn(rules);
    }

    @Test
    @DisplayName("결과항목 방식 — CBC 3항목 등록 시 헤더 result_value 는 NULL, abnormal_yn 은 상세 중 하나라도 이상이면 Y로 집계된다")
    void detailsMixedNormalAndAbnormal() {
        cbcItem();
        readySpecimen();
        givenCbcRules();
        List<LabReferenceRangeEntity> ranges = List.of(
                referenceRange("02", "ALL", "4.0-10.0"),
                referenceRange("03", "ALL", "4.35-5.65"),
                referenceRange("04", "ALL", "150-400"));
        when(labReferenceRangeRepository.findByResultItemCodeInAndUseYn(anyList(), anyString()))
                .thenReturn(ranges);

        service.createLabResult(cbcRequest(List.of(
                detailRequest("02", "6.2"),
                detailRequest("03", "4.6"),
                detailRequest("04", "120")))); // 150 미만 → 이상

        ArgumentCaptor<LabResultEntity> captor = ArgumentCaptor.forClass(LabResultEntity.class);
        verify(labResultRepository).save(captor.capture());
        LabResultEntity saved = captor.getValue();

        assertThat(saved.getResultValue()).isNull();
        assertThat(saved.getResultUnit()).isNull();
        assertThat(saved.getReferenceRange()).isNull();
        assertThat(saved.getAbnormalYn()).isEqualTo("Y");
        assertThat(saved.getDetails()).extracting(LabResultDetailEntity::getResultItemCode)
                .containsExactly("02", "03", "04");
        assertThat(saved.getDetails()).extracting(LabResultDetailEntity::getAbnormalYn)
                .containsExactly("N", "N", "Y");
        assertThat(saved.getDetails().get(0).getResultUnit()).isEqualTo("x10^3/uL"); // 서버가 정한 단위
    }

    @Test
    @DisplayName("일부 항목만 입력해도 등록된다 — 규칙에 정의된 항목을 전부 채울 필요는 없다")
    void partialDetailsAllowed() {
        cbcItem();
        readySpecimen();
        givenCbcRules();
        List<LabReferenceRangeEntity> ranges = List.of(referenceRange("03", "ALL", "4.35-5.65"));
        when(labReferenceRangeRepository.findByResultItemCodeInAndUseYn(anyList(), anyString()))
                .thenReturn(ranges);

        service.createLabResult(cbcRequest(List.of(detailRequest("03", "4.6"))));

        ArgumentCaptor<LabResultEntity> captor = ArgumentCaptor.forClass(LabResultEntity.class);
        verify(labResultRepository).save(captor.capture());
        assertThat(captor.getValue().getDetails()).hasSize(1);
    }

    @Test
    @DisplayName("성별별 참고범위 — 환자가 남성이면 남성용 참고범위(적혈구)가 적용된다")
    void referenceRangeAppliesMaleValue() {
        cbcItem();
        readySpecimen();
        givenCbcRules();
        when(patientServiceBusinessDelegate.findGenderCode(PATIENT_ID)).thenReturn("01");
        List<LabReferenceRangeEntity> ranges = List.of(
                referenceRange("03", "01", "4.35-5.65"),
                referenceRange("03", "02", "3.92-5.13"));
        when(labReferenceRangeRepository.findByResultItemCodeInAndUseYn(anyList(), anyString()))
                .thenReturn(ranges);

        // 남성 기준(4.35-5.65)으로는 이상, 여성 기준(3.92-5.13)이었다면 정상이었을 값 — 실제로 남성 기준이 적용됐는지 구분한다.
        service.createLabResult(cbcRequest(List.of(detailRequest("03", "4.1"))));

        ArgumentCaptor<LabResultEntity> captor = ArgumentCaptor.forClass(LabResultEntity.class);
        verify(labResultRepository).save(captor.capture());
        LabResultDetailEntity detail = captor.getValue().getDetails().get(0);
        assertThat(detail.getReferenceRange()).isEqualTo("4.35-5.65");
        assertThat(detail.getAbnormalYn()).isEqualTo("Y");
    }

    @Test
    @DisplayName("성별별 참고범위 — 환자가 여성이면 여성용 참고범위가 적용된다")
    void referenceRangeAppliesFemaleValue() {
        cbcItem();
        readySpecimen();
        givenCbcRules();
        when(patientServiceBusinessDelegate.findGenderCode(PATIENT_ID)).thenReturn("02");
        List<LabReferenceRangeEntity> ranges = List.of(
                referenceRange("03", "01", "4.35-5.65"),
                referenceRange("03", "02", "3.92-5.13"));
        when(labReferenceRangeRepository.findByResultItemCodeInAndUseYn(anyList(), anyString()))
                .thenReturn(ranges);

        service.createLabResult(cbcRequest(List.of(detailRequest("03", "4.1"))));

        ArgumentCaptor<LabResultEntity> captor = ArgumentCaptor.forClass(LabResultEntity.class);
        verify(labResultRepository).save(captor.capture());
        LabResultDetailEntity detail = captor.getValue().getDetails().get(0);
        assertThat(detail.getReferenceRange()).isEqualTo("3.92-5.13");
        assertThat(detail.getAbnormalYn()).isEqualTo("N");
    }

    @Test
    @DisplayName("성별 미상(조회 실패 포함)이고 ALL 행이 있으면 ALL 참고범위를 적용한다")
    void referenceRangeFallsBackToAllWhenGenderUnknown() {
        cbcItem();
        readySpecimen();
        givenCbcRules();
        when(patientServiceBusinessDelegate.findGenderCode(PATIENT_ID)).thenReturn(null); // 미상/조회 실패
        List<LabReferenceRangeEntity> ranges = List.of(referenceRange("02", "ALL", "4.0-10.0"));
        when(labReferenceRangeRepository.findByResultItemCodeInAndUseYn(anyList(), anyString()))
                .thenReturn(ranges);

        service.createLabResult(cbcRequest(List.of(detailRequest("02", "6.2"))));

        ArgumentCaptor<LabResultEntity> captor = ArgumentCaptor.forClass(LabResultEntity.class);
        verify(labResultRepository).save(captor.capture());
        assertThat(captor.getValue().getDetails().get(0).getReferenceRange()).isEqualTo("4.0-10.0");
        assertThat(captor.getValue().getDetails().get(0).getAbnormalYn()).isEqualTo("N");
    }

    @Test
    @DisplayName("성별을 모르고 그 항목에 ALL 행도 없으면 판정하지 않는다 — abnormal=N, 참고범위=null")
    void noJudgmentWhenNoReferenceRangeAtAll() {
        cbcItem();
        readySpecimen();
        givenCbcRules();
        when(patientServiceBusinessDelegate.findGenderCode(PATIENT_ID)).thenReturn(null);
        when(labReferenceRangeRepository.findByResultItemCodeInAndUseYn(anyList(), anyString()))
                .thenReturn(List.of()); // 참고범위 자체가 없음

        service.createLabResult(cbcRequest(List.of(detailRequest("02", "999"))));

        ArgumentCaptor<LabResultEntity> captor = ArgumentCaptor.forClass(LabResultEntity.class);
        verify(labResultRepository).save(captor.capture());
        LabResultDetailEntity detail = captor.getValue().getDetails().get(0);
        assertThat(detail.getReferenceRange()).isNull();
        assertThat(detail.getAbnormalYn()).isEqualTo("N");
        assertThat(captor.getValue().getAbnormalYn()).isEqualTo("N"); // 헤더 집계도 N
    }

    @Test
    @DisplayName("정성 결과항목(요단백) — 참고범위에 없는 값(양성)이면 이상으로 판정한다")
    void qualitativeResultAbnormal() {
        LabOrderItemEntity uaItem = mock(LabOrderItemEntity.class);
        when(uaItem.getLabOrder()).thenReturn(order);
        when(uaItem.getLabItemCode()).thenReturn("04");
        when(labOrderItemRepository.findById(CBC_ITEM_ID)).thenReturn(Optional.of(uaItem));
        when(labResultRepository.existsByLabOrderItem_LabOrderItemId(CBC_ITEM_ID)).thenReturn(false);
        readySpecimen();
        List<LabResultItemRuleEntity> rules = List.of(itemRule("04", "08", 1, null));
        when(labResultItemRuleRepository.findByTestTypeCodeAndUseYnOrderByItemSeqAsc(eq("04"), anyString()))
                .thenReturn(rules);
        List<LabReferenceRangeEntity> ranges = List.of(referenceRange("08", "ALL", "음성,정상"));
        when(labReferenceRangeRepository.findByResultItemCodeInAndUseYn(anyList(), anyString()))
                .thenReturn(ranges);

        service.createLabResult(cbcRequest(List.of(detailRequest("08", "양성"))));

        ArgumentCaptor<LabResultEntity> captor = ArgumentCaptor.forClass(LabResultEntity.class);
        verify(labResultRepository).save(captor.capture());
        assertThat(captor.getValue().getDetails().get(0).getAbnormalYn()).isEqualTo("Y");
    }

    @Test
    @DisplayName("이 검사에 속하지 않는 결과항목이면 LAB099")
    void detailNotInRuleRejected() {
        cbcItem();
        readySpecimen();
        givenCbcRules();

        assertThatThrownBy(() -> service.createLabResult(cbcRequest(List.of(detailRequest("99", "1")))))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB099);
        verify(labResultRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 결과항목이 중복 입력되면 LAB100")
    void duplicateDetailRejected() {
        cbcItem();
        readySpecimen();
        givenCbcRules();

        assertThatThrownBy(() -> service.createLabResult(cbcRequest(List.of(
                detailRequest("02", "6.2"), detailRequest("02", "6.3")))))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB100);
    }

    @Test
    @DisplayName("결과항목이 상한(4개)을 넘으면 LAB101")
    void tooManyDetailsRejected() {
        LabOrderItemEntity item = cbcItem();
        readySpecimen();
        List<LabResultItemRuleEntity> rules = List.of(
                itemRule("02", "01", 1, null), itemRule("02", "02", 2, null),
                itemRule("02", "03", 3, null), itemRule("02", "04", 4, null),
                itemRule("02", "05", 5, null));
        when(labResultItemRuleRepository.findByTestTypeCodeAndUseYnOrderByItemSeqAsc(eq("02"), anyString()))
                .thenReturn(rules);

        assertThatThrownBy(() -> service.createLabResult(cbcRequest(List.of(
                detailRequest("01", "1"), detailRequest("02", "1"), detailRequest("03", "1"),
                detailRequest("04", "1"), detailRequest("05", "1")))))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB101);
        assertThat(item).isNotNull();
    }

    @Test
    @DisplayName("결과항목이 필요한 검사인데 details 가 없으면 LAB102")
    void missingDetailsRejectedWhenRuleExists() {
        cbcItem();
        readySpecimen();
        givenCbcRules();

        LabResultCreateRequestDto request = LabResultCreateRequestDto.builder()
                .labOrderItemId(CBC_ITEM_ID).recordedById("emp-1").build(); // details 없음, resultValue 도 없음

        assertThatThrownBy(() -> service.createLabResult(request))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB102);
    }

    @Test
    @DisplayName("결과항목을 지원하지 않는 검사(기존 방식)에 details 를 보내면 LAB099")
    void detailsSentToUnsupportedTestRejected() {
        readySpecimen(); // 기본 항목 "01" 은 setUp 에서 규칙 없음으로 stub 됨

        LabResultCreateRequestDto request = LabResultCreateRequestDto.builder()
                .labOrderItemId(ITEM_ID).recordedById("emp-1")
                .details(List.of(detailRequest("02", "1")))
                .build();

        assertThatThrownBy(() -> service.createLabResult(request))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB099);
    }

    @Test
    @DisplayName("기존 방식(규칙 없는 검사)은 resultValue 가 비어 있으면 LAB998로 거절한다 — 6차 이전과 같은 필수 규칙")
    void existingWayStillRequiresResultValue() {
        readySpecimen();

        LabResultCreateRequestDto request = LabResultCreateRequestDto.builder()
                .labOrderItemId(ITEM_ID).recordedById("emp-1").build();

        assertThatThrownBy(() -> service.createLabResult(request))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB998);
    }

    // ==================================================================
    // 결과값 숫자 형식 검증 (LAB105/106) — 04번 지시서 Phase 3-A
    // ==================================================================

    @Test
    @DisplayName("참고범위가 수치 범위인데 결과값이 숫자가 아니면 LAB105로 거절한다 (등록)")
    void createRejectsNonNumericResultValueAgainstNumericRange() {
        readySpecimen();

        LabResultCreateRequestDto request = request().toBuilder()
                .resultValue("4.2mg").referenceRange("3.5-5.5").build();

        assertThatThrownBy(() -> service.createLabResult(request))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB105);
        verify(labResultRepository, never()).save(any());
    }

    @Test
    @DisplayName("참고범위의 하한이 상한보다 크거나 같으면 LAB106으로 거절한다 (등록)")
    void createRejectsInvertedNumericRange() {
        readySpecimen();

        LabResultCreateRequestDto request = request().toBuilder()
                .resultValue("4.2").referenceRange("6.0-3.5").build();

        assertThatThrownBy(() -> service.createLabResult(request))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB106);
        verify(labResultRepository, never()).save(any());
    }

    @Test
    @DisplayName("참고범위가 정성 표기면 결과값 형식을 따지지 않는다 (회귀 — 기존 정성 판정과 공존)")
    void createDoesNotValidateFormatForQualitativeRange() {
        readySpecimen();

        LabResultCreateRequestDto request = request().toBuilder()
                .resultValue("양성").referenceRange("음성,정상").build();

        service.createLabResult(request);

        verify(labResultRepository).save(any(LabResultEntity.class));
    }

    @Test
    @DisplayName("참고범위가 수치 범위인데 결과값이 숫자가 아니면 LAB105로 거절한다 (수정)")
    void updateRejectsNonNumericResultValueAgainstNumericRange() {
        LabOrderItemEntity item = mock(LabOrderItemEntity.class);
        when(item.getLabOrder()).thenReturn(order);
        when(item.getLabItemCode()).thenReturn("01");

        LabResultEntity existing = LabResultEntity.builder()
                .resultValue("4.2").resultUnit(null).referenceRange("3.5-5.5")
                .abnormalYn("N").resultStatusCode("01")
                .recordedAt(java.time.LocalDateTime.now()).recordedById("emp-1")
                .build();
        existing.assignLabOrderItem(item);
        when(labResultRepository.findById("result-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateLabResult("result-1", LabResultUpdateRequestDto.builder()
                .resultValue("4.2mg").referenceRange("3.5-5.5").build()))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB105);
    }

    @Test
    @DisplayName("결과항목 방식도 상세 하나의 결과값이 수치 참고범위에 맞지 않는 형식이면 LAB105로 거절한다")
    void detailsRejectNonNumericResultValueAgainstNumericRange() {
        cbcItem();
        readySpecimen();
        givenCbcRules();
        List<LabReferenceRangeEntity> ranges = List.of(referenceRange("02", "ALL", "4.0-10.0"));
        when(labReferenceRangeRepository.findByResultItemCodeInAndUseYn(anyList(), anyString()))
                .thenReturn(ranges);

        assertThatThrownBy(() -> service.createLabResult(cbcRequest(List.of(detailRequest("02", "abc")))))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB105);
        verify(labResultRepository, never()).save(any());
    }

    @Test
    @DisplayName("확정 전 결과항목 수정 — 값 갱신·없어진 항목 삭제·새 항목 추가가 한 번에 반영되고 헤더 abnormal_yn 을 다시 계산한다")
    void updateDetailsReplacesAndRecalculatesHeader() {
        LabOrderItemEntity item = mock(LabOrderItemEntity.class);
        when(item.getLabOrder()).thenReturn(order);
        when(item.getLabItemCode()).thenReturn("02");

        LabResultEntity existing = LabResultEntity.builder()
                .resultValue(null).resultUnit(null).referenceRange(null)
                .abnormalYn("N").resultStatusCode("01")
                .recordedAt(java.time.LocalDateTime.now()).recordedById("emp-1")
                .build();
        existing.assignLabOrderItem(item);
        existing.replaceDetails(List.of(
                LabResultDetailEntity.builder().detailSeq(1).resultItemCode("02").resultValue("5.0")
                        .resultUnit("x10^3/uL").referenceRange("4.0-10.0").abnormalYn("N").build(),
                LabResultDetailEntity.builder().detailSeq(2).resultItemCode("03").resultValue("4.0")
                        .resultUnit("x10^6/uL").referenceRange("4.35-5.65").abnormalYn("Y").build()));

        when(labResultRepository.findById("result-1")).thenReturn(Optional.of(existing));
        givenCbcRules();
        List<LabReferenceRangeEntity> ranges = List.of(
                referenceRange("02", "ALL", "4.0-10.0"),
                referenceRange("04", "ALL", "150-400"));
        when(labReferenceRangeRepository.findByResultItemCodeInAndUseYn(anyList(), anyString()))
                .thenReturn(ranges);

        service.updateLabResult("result-1", LabResultUpdateRequestDto.builder()
                .details(List.of(
                        detailRequest("02", "7.0"), // 갱신(값만 바뀜)
                        detailRequest("04", "120")   // 신규, 150 미만이라 이상
                        // 03 은 요청에서 빠졌으므로 삭제된다
                )).build());

        assertThat(existing.getDetails()).extracting(LabResultDetailEntity::getResultItemCode)
                .containsExactly("02", "04");
        assertThat(existing.getDetails()).extracting(LabResultDetailEntity::getResultValue)
                .containsExactly("7.0", "120");
        assertThat(existing.getAbnormalYn()).isEqualTo("Y"); // 04 가 이상이라 헤더도 Y 로 재계산
    }

    @Test
    @DisplayName("결과항목 방식도 확정된 결과는 수정할 수 없다 (LAB040)")
    void confirmedDetailResultCannotBeUpdated() {
        LabOrderItemEntity item = mock(LabOrderItemEntity.class);
        when(item.getLabOrder()).thenReturn(order);
        when(item.getLabItemCode()).thenReturn("02");

        LabResultEntity confirmed = LabResultEntity.builder()
                .abnormalYn("N").resultStatusCode("02")
                .recordedAt(java.time.LocalDateTime.now()).recordedById("emp-1")
                .build();
        confirmed.assignLabOrderItem(item);
        when(labResultRepository.findById("result-1")).thenReturn(Optional.of(confirmed));

        assertThatThrownBy(() -> service.updateLabResult("result-1",
                LabResultUpdateRequestDto.builder().details(List.of(detailRequest("02", "1"))).build()))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB040);
    }
}
