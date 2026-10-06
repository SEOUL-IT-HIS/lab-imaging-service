package kr.co.seoulit.his.labimagingservice.labspecimen.service;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.common.validation.DateTimeValidator;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabReceptionEntity;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabReceptionRepository;
import kr.co.seoulit.his.labimagingservice.labspecimen.dto.SpecimenCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.labspecimen.dto.SpecimenRuleDto;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.LabTestSpecimenRuleEntity;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenEntity;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenType;
import kr.co.seoulit.his.labimagingservice.labspecimen.mapper.SpecimenMapper;
import kr.co.seoulit.his.labimagingservice.labspecimen.repository.LabTestSpecimenRuleRepository;
import kr.co.seoulit.his.labimagingservice.labspecimen.repository.SpecimenAcceptanceRepository;
import kr.co.seoulit.his.labimagingservice.labspecimen.repository.SpecimenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
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
 * 검체 등록 시 환자 대조 (후속조치 #2, UC-SPC-03) + 검사별 검체·검체용기 매핑 (6차, 2-1).
 *
 * ⚠ mock 을 만드는 헬퍼(orderItem/rule)는 절대로 다른 when(...).thenReturn(...) 의 인자 자리에서
 *   직접 호출하지 않는다. 헬퍼 내부에서 when()을 쓰는데, 바깥 when()이 아직 thenReturn()을 못 받은
 *   상태에서 안쪽 when()이 실행되면 Mockito 가 "UnfinishedStubbingException"을 던진다. 항상 먼저
 *   지역변수로 받은 뒤 그 변수를 전달한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SpecimenServiceTest {

    private static final String RECEPTION_ID = "rec-1";
    private static final String ORDER_PATIENT_ID = "patient-A";

    @Mock SpecimenRepository specimenRepository;
    @Mock SpecimenAcceptanceRepository specimenAcceptanceRepository;
    @Mock CommonCodeCache commonCodeCache;
    @Mock SpecimenMapper specimenMapper;
    @Mock LabReceptionRepository labReceptionRepository;
    @Mock LabTestSpecimenRuleRepository labTestSpecimenRuleRepository;
    @Mock DateTimeValidator dateTimeValidator;

    @InjectMocks SpecimenService specimenService;

    private LabOrderEntity order;
    private LabReceptionEntity reception;

    @BeforeEach
    void setUp() {
        order = mock(LabOrderEntity.class);
        when(order.getPatientId()).thenReturn(ORDER_PATIENT_ID);
        List<LabOrderItemEntity> defaultItems = List.of(orderItem("01"));
        when(order.getOrderItems()).thenReturn(defaultItems);

        reception = mock(LabReceptionEntity.class);
        when(reception.getLabOrder()).thenReturn(order);
        when(reception.getLabReceptionId()).thenReturn(RECEPTION_ID);
        when(reception.getReceptionNo()).thenReturn("LR-1");

        when(labReceptionRepository.findById(RECEPTION_ID)).thenReturn(Optional.of(reception));
        when(labReceptionRepository.findByReceptionNo("LR-1")).thenReturn(Optional.of(reception));
        when(commonCodeCache.isValid(anyString(), anyString())).thenReturn(true);
        when(specimenRepository.existsBySpecimenBarcode(anyString())).thenReturn(false);
        when(specimenRepository.save(any(SpecimenEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        // 규칙이 하나도 없는 검사만 있는 접수(허용 + WARN)가 검증 테스트의 기본값이 되지 않도록,
        // 검체 등록 대상 테스트(mismatch/missing/match)는 검사코드 01 + 규칙 BLOOD/01 하나로 통일해 둔다.
        List<LabTestSpecimenRuleEntity> defaultRules = List.of(rule("01", "BLOOD", "01", "Y"));
        when(labTestSpecimenRuleRepository.findByTestTypeCodeInAndUseYn(eq(List.of("01")), eq("Y")))
                .thenReturn(defaultRules);
    }

    private static LabOrderItemEntity orderItem(String labItemCode) {
        LabOrderItemEntity item = mock(LabOrderItemEntity.class);
        when(item.getLabItemCode()).thenReturn(labItemCode);
        return item;
    }

    /** LabTestSpecimenRuleEntity 는 읽기 전용(생성자 없음)이라 mock 으로 getter 만 흉내낸다. */
    private static LabTestSpecimenRuleEntity rule(String testTypeCode, String specimenType,
                                                   String containerCode, String defaultYn) {
        LabTestSpecimenRuleEntity rule = mock(LabTestSpecimenRuleEntity.class);
        when(rule.getTestTypeCode()).thenReturn(testTypeCode);
        when(rule.getSpecimenTypeCode()).thenReturn(specimenType);
        when(rule.getSpecimenContainerCode()).thenReturn(containerCode);
        when(rule.getDefaultYn()).thenReturn(defaultYn);
        when(rule.getUseYn()).thenReturn("Y");
        return rule;
    }

    private SpecimenCreateRequestDto request(String patientId) {
        return request(patientId, SpecimenType.BLOOD, "01");
    }

    private SpecimenCreateRequestDto request(String patientId, SpecimenType specimenType, String containerCode) {
        return SpecimenCreateRequestDto.builder()
                .labReceptionId(RECEPTION_ID)
                .specimenContainerCode(containerCode)
                .specimenType(specimenType)
                .patientId(patientId)
                .collectedAt(LocalDateTime.now())
                .collectedById("emp-1")
                .build();
    }

    @Test
    @DisplayName("취소된 접수면 LAB121 (05번 지시서 Phase 3)")
    void rejectsCancelledReception() {
        org.mockito.Mockito.doThrow(new LabImagingBusinessException(LabMessageCode.LAB121, "취소된 접수입니다."))
                .when(reception).requireNotCancelled();

        assertThatThrownBy(() -> specimenService.createSpecimen(request(ORDER_PATIENT_ID)))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB121);
        verify(specimenRepository, never()).save(any());
    }

    @Test
    @DisplayName("요청 환자ID 가 접수의 환자와 다르면 LAB051 로 거절하고 저장하지 않는다")
    void mismatchRejected() {
        assertThatThrownBy(() -> specimenService.createSpecimen(request("patient-B")))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB051);

        verify(specimenRepository, never()).save(any());
    }

    @Test
    @DisplayName("요청에 환자ID 가 없으면 접수의 환자ID 로 채워 저장한다")
    void missingFilledFromReception() {
        specimenService.createSpecimen(request(null));

        ArgumentCaptor<SpecimenEntity> captor = ArgumentCaptor.forClass(SpecimenEntity.class);
        verify(specimenRepository).save(captor.capture());
        assertThat(captor.getValue().getPatientId()).isEqualTo(ORDER_PATIENT_ID);
    }

    @Test
    @DisplayName("요청 환자ID 가 접수의 환자와 같으면 그대로 저장한다")
    void matchSaved() {
        specimenService.createSpecimen(request(ORDER_PATIENT_ID));

        ArgumentCaptor<SpecimenEntity> captor = ArgumentCaptor.forClass(SpecimenEntity.class);
        verify(specimenRepository).save(captor.capture());
        assertThat(captor.getValue().getPatientId()).isEqualTo(ORDER_PATIENT_ID);
    }

    // ==================================================================
    // 검사별 검체·검체용기 매핑 (6차, 2-1)
    // ==================================================================

    @Test
    @DisplayName("허용된 조합이면 통과한다")
    void allowedCombinationPasses() {
        specimenService.createSpecimen(request(ORDER_PATIENT_ID, SpecimenType.BLOOD, "01"));

        verify(specimenRepository).save(any());
    }

    @Test
    @DisplayName("허용되지 않은 조합이면 LAB098 로 거절하고 저장하지 않는다")
    void disallowedCombinationRejected() {
        assertThatThrownBy(() -> specimenService.createSpecimen(request(ORDER_PATIENT_ID, SpecimenType.URINE, "04")))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB098);

        verify(specimenRepository, never()).save(any());
    }

    @Test
    @DisplayName("접수에 검사항목이 여러 개면 각 검사의 허용 규칙을 합쳐(합집합) 검증한다")
    void unionOfMultipleTestTypes() {
        List<LabOrderItemEntity> items = List.of(orderItem("01"), orderItem("04"));
        when(order.getOrderItems()).thenReturn(items);
        List<LabTestSpecimenRuleEntity> rules = List.of(
                rule("01", "BLOOD", "01", "Y"),
                rule("04", "URINE", "04", "Y"));
        when(labTestSpecimenRuleRepository.findByTestTypeCodeInAndUseYn(eq(List.of("01", "04")), eq("Y")))
                .thenReturn(rules);

        // 04 검사만 허용하는 URINE/04 조합도, 합집합 덕분에 이 접수에서는 허용된다.
        specimenService.createSpecimen(request(ORDER_PATIENT_ID, SpecimenType.URINE, "04"));

        verify(specimenRepository).save(any());
    }

    @Test
    @DisplayName("규칙이 하나도 없는 검사만 있는 접수는 조합을 막지 않는다(WARN 로그만 남긴다)")
    void noRuleAllowsAndWarns() {
        List<LabOrderItemEntity> items = List.of(orderItem("99"));
        when(order.getOrderItems()).thenReturn(items);
        when(labTestSpecimenRuleRepository.findByTestTypeCodeInAndUseYn(eq(List.of("99")), eq("Y")))
                .thenReturn(List.of());

        specimenService.createSpecimen(request(ORDER_PATIENT_ID, SpecimenType.FLUID, "09"));

        verify(specimenRepository).save(any());
    }

    @Test
    @DisplayName("사용 중지(use_yn=N) 규칙은 애초에 조회되지 않으므로, 그 조합만 요청하면 거절된다")
    void inactiveRuleExcluded() {
        // 리포지토리가 use_yn='Y' 로만 걸러 준다는 계약이라, 예전엔 허용됐던 BLOOD/03(SST) 규칙이
        // 지금은 사용중지(N)라서 이 메서드가 돌려주지 않는다고 가정한다 — 남은 사용중 규칙은 BLOOD/05뿐이다.
        List<LabTestSpecimenRuleEntity> rules = List.of(rule("01", "BLOOD", "05", "Y"));
        when(labTestSpecimenRuleRepository.findByTestTypeCodeInAndUseYn(eq(List.of("01")), eq("Y")))
                .thenReturn(rules);

        assertThatThrownBy(() -> specimenService.createSpecimen(request(ORDER_PATIENT_ID, SpecimenType.BLOOD, "03")))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB098);
    }

    @ParameterizedTest(name = "검사 {0} → 검체 {1} / 용기 {2}")
    @DisplayName("검사 종류별 대표 조합 8개 — DDL 초기 데이터(3-3)와 같은 조합이면 통과한다")
    @CsvSource({
            "01, BLOOD, 05",
            "02, BLOOD, 02",
            "03, BLOOD, 03",
            "04, URINE, 04",
            "05, BLOOD, 06",
            "06, URINE, 07",
            "07, TISSUE, 08",
            "08, FLUID, 09",
    })
    void representativeCombinationsPerTestType(String testTypeCode, String specimenType, String containerCode) {
        List<LabOrderItemEntity> items = List.of(orderItem(testTypeCode));
        when(order.getOrderItems()).thenReturn(items);
        List<LabTestSpecimenRuleEntity> rules = List.of(rule(testTypeCode, specimenType, containerCode, "Y"));
        when(labTestSpecimenRuleRepository.findByTestTypeCodeInAndUseYn(eq(List.of(testTypeCode)), eq("Y")))
                .thenReturn(rules);

        specimenService.createSpecimen(request(ORDER_PATIENT_ID, SpecimenType.valueOf(specimenType), containerCode));

        verify(specimenRepository).save(any());
    }

    @Test
    @DisplayName("허용 조합 조회 API — 서로 다른 검사가 같은 조합을 허용해도 결과는 조합당 1건이다")
    void getAllowedSpecimenRulesDeduplicates() {
        List<LabOrderItemEntity> items = List.of(orderItem("01"), orderItem("03"));
        when(order.getOrderItems()).thenReturn(items);
        // 01/03 검사 둘 다 BLOOD-03(기본선택 Y)을 허용 — 조합·기본선택이 같으므로 중복이다.
        List<LabTestSpecimenRuleEntity> rules = List.of(
                rule("01", "BLOOD", "05", "Y"),
                rule("01", "BLOOD", "03", "Y"),
                rule("03", "BLOOD", "03", "Y"));
        when(labTestSpecimenRuleRepository.findByTestTypeCodeInAndUseYn(eq(List.of("01", "03")), eq("Y")))
                .thenReturn(rules);

        List<SpecimenRuleDto> result = specimenService.getAllowedSpecimenRules("LR-1");

        assertThat(result).hasSize(2)
                .extracting(SpecimenRuleDto::getSpecimenContainerCode)
                .containsExactlyInAnyOrder("05", "03");
    }

    @Test
    @DisplayName("허용 조합 조회 API — 규칙이 없는 검사만 있는 접수는 빈 목록을 반환한다")
    void getAllowedSpecimenRulesEmptyWhenNoRule() {
        List<LabOrderItemEntity> items = List.of(orderItem("99"));
        when(order.getOrderItems()).thenReturn(items);
        when(labTestSpecimenRuleRepository.findByTestTypeCodeInAndUseYn(eq(List.of("99")), eq("Y")))
                .thenReturn(List.of());

        assertThat(specimenService.getAllowedSpecimenRules("LR-1")).isEmpty();
    }

    /** 04번 지시서 Phase 3-B — DateTimeValidator 연동. 모드별 동작 자체는 DateTimeValidatorTest 가 담당한다. */
    @Test
    @DisplayName("채취일시가 미래면(DateTimeValidator 가 LAB107을 던지면) 검체를 저장하지 않는다")
    void rejectsFutureCollectedAt() {
        org.mockito.Mockito.doThrow(new LabImagingBusinessException(LabMessageCode.LAB107, "미래 시각/일자는 입력할 수 없습니다."))
                .when(dateTimeValidator).rejectIfFuture(any(LocalDateTime.class), eq("collectedAt"));

        assertThatThrownBy(() -> specimenService.createSpecimen(request(ORDER_PATIENT_ID)))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB107);
        verify(specimenRepository, never()).save(any());
    }
}
