package kr.co.seoulit.his.labimagingservice.laborder.service;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.cache.StaffDirectoryCache;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.interfacelog.entity.InterfaceOrderType;
import kr.co.seoulit.his.labimagingservice.interfacelog.service.InterfaceReceiveLogService;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderIntakeRequestDto;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderSummaryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 검사오더 연계 수신 — 진료채널(encounterType) → 연계시스템코드/진료구분/응급여부 매핑. (2026-10-01)
 *
 * 병동 처방 연동 확인 과정에서, 코어가 채널 구분을 보내지 않아 모든 연계 수신 오더가
 * 무조건 외래(01/01/N)로 저장되던 것을 encounterType 기반 매핑으로 바꿨다. 이 테스트는
 * 그 매핑 함수(순수 함수, static)와 intake() 전체 흐름에 실제로 반영되는지를 함께 확인한다.
 */
class LabOrderIntakeServiceTest {

    private final LabOrderService labOrderService = mock(LabOrderService.class);
    private final InterfaceReceiveLogService interfaceReceiveLogService = mock(InterfaceReceiveLogService.class);
    private final StaffDirectoryCache staffDirectoryCache = mock(StaffDirectoryCache.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private final LabOrderIntakeService service =
            new LabOrderIntakeService(labOrderService, interfaceReceiveLogService, staffDirectoryCache, objectMapper);

    @BeforeEach
    void setUp() {
        when(interfaceReceiveLogService.logReceived(any(), anyString(), anyString())).thenReturn("log-1");
        when(labOrderService.createOrder(any())).thenReturn(
                LabOrderSummaryDto.builder().labOrderId("order-1").build());
    }

    private static LabOrderIntakeRequestDto request(String encounterType, String urgencyYn) {
        return new LabOrderIntakeRequestDto(
                "RX-1", "enc-1", encounterType, "adm-1", urgencyYn,
                "patient-1", "doctor-1",
                List.of(new LabOrderIntakeRequestDto.Item("01", "Blood Glucose Test")));
    }

    @Test
    @DisplayName("resolveSystemCode: OPD/null/모르는 값 → 01(외래), ER → 02(응급), IP → 03(병동)")
    void resolveSystemCode() {
        assertThat(LabOrderIntakeService.resolveSystemCode(null)).isEqualTo("01");
        assertThat(LabOrderIntakeService.resolveSystemCode("OPD")).isEqualTo("01");
        assertThat(LabOrderIntakeService.resolveSystemCode("unknown")).isEqualTo("01");
        assertThat(LabOrderIntakeService.resolveSystemCode("ER")).isEqualTo("02");
        assertThat(LabOrderIntakeService.resolveSystemCode("er")).isEqualTo("02"); // 대소문자 무관
        assertThat(LabOrderIntakeService.resolveSystemCode("IP")).isEqualTo("03");
    }

    @Test
    @DisplayName("resolveTreatTypeCode: OPD/null → 01, ER → 02, IP → 05(admin 신규 등록 요청 중인 값)")
    void resolveTreatTypeCode() {
        assertThat(LabOrderIntakeService.resolveTreatTypeCode(null)).isEqualTo("01");
        assertThat(LabOrderIntakeService.resolveTreatTypeCode("ER")).isEqualTo("02");
        assertThat(LabOrderIntakeService.resolveTreatTypeCode("IP")).isEqualTo("05");
    }

    @Test
    @DisplayName("resolveUrgencyYn: Y(대소문자 무관)만 Y, 그 외(null/빈값/오타)는 전부 N")
    void resolveUrgencyYn() {
        assertThat(LabOrderIntakeService.resolveUrgencyYn("Y")).isEqualTo("Y");
        assertThat(LabOrderIntakeService.resolveUrgencyYn("y")).isEqualTo("Y");
        assertThat(LabOrderIntakeService.resolveUrgencyYn(null)).isEqualTo("N");
        assertThat(LabOrderIntakeService.resolveUrgencyYn("")).isEqualTo("N");
        assertThat(LabOrderIntakeService.resolveUrgencyYn("yes")).isEqualTo("N");
    }

    /**
     * 마무리_최종현황 04번 지시서 Phase 0-1 — doctorId 가 physicianId 로 들어가고 physicianNo 는
     * 항상 null 로 비워지는지 확인한다(코어가 처방의 "번호"는 안 주고 ID만 준다, 코드 주석 참고).
     */
    @Test
    @DisplayName("doctorId는 physicianId로 들어가고 physicianNo는 항상 null이다")
    void doctorIdMapsToPhysicianIdAndPhysicianNoIsAlwaysNull() {
        service.intake(request("OPD", null));

        ArgumentCaptor<LabOrderCreateRequestDto> captor = ArgumentCaptor.forClass(LabOrderCreateRequestDto.class);
        verify(labOrderService).createOrder(captor.capture());
        LabOrderCreateRequestDto saved = captor.getValue();

        assertThat(saved.getPhysicianId()).isEqualTo("doctor-1");
        assertThat(saved.getPhysicianNo()).isNull();
    }

    @Test
    @DisplayName("encounterType 없음(과도기, 하위호환) — 지금까지와 똑같이 외래(01/01/N)로 접수된다")
    void intakeWithoutEncounterTypeStaysOutpatient() {
        service.intake(request(null, null));

        ArgumentCaptor<LabOrderCreateRequestDto> captor = ArgumentCaptor.forClass(LabOrderCreateRequestDto.class);
        verify(labOrderService).createOrder(captor.capture());
        LabOrderCreateRequestDto saved = captor.getValue();

        assertThat(saved.getSystemCode()).isEqualTo("01");
        assertThat(saved.getTreatTypeCode()).isEqualTo("01");
        assertThat(saved.getUrgencyYn()).isEqualTo("N");
    }

    @Test
    @DisplayName("encounterType=IP, urgencyYn=Y — 연계시스템코드 03(병동)·진료구분 05(입원)·응급 Y 로 접수된다")
    void intakeInpatientUrgent() {
        service.intake(request("IP", "Y"));

        ArgumentCaptor<LabOrderCreateRequestDto> captor = ArgumentCaptor.forClass(LabOrderCreateRequestDto.class);
        verify(labOrderService).createOrder(captor.capture());
        LabOrderCreateRequestDto saved = captor.getValue();

        assertThat(saved.getSystemCode()).isEqualTo("03");
        assertThat(saved.getTreatTypeCode()).isEqualTo("05");
        assertThat(saved.getUrgencyYn()).isEqualTo("Y");
    }

    @Test
    @DisplayName("encounterType=ER — 연계시스템코드 02(응급)·진료구분 02(응급)로 접수된다")
    void intakeEmergency() {
        service.intake(request("ER", null));

        ArgumentCaptor<LabOrderCreateRequestDto> captor = ArgumentCaptor.forClass(LabOrderCreateRequestDto.class);
        verify(labOrderService).createOrder(captor.capture());
        LabOrderCreateRequestDto saved = captor.getValue();

        assertThat(saved.getSystemCode()).isEqualTo("02");
        assertThat(saved.getTreatTypeCode()).isEqualTo("02");
    }

    @Test
    @DisplayName("수신 기록(logReceived)의 system_code 도 encounterType 기준으로 남는다 — IP는 03(병동)")
    void logReceivedUsesResolvedSystemCode() {
        service.intake(request("IP", null));

        verify(interfaceReceiveLogService).logReceived(eq(InterfaceOrderType.LAB), eq("03"), anyString());
    }

    /**
     * 마무리_최종현황 03번 지시서 C-1(I-09) — encounterType=IP 는 진료구분 05 로 매핑되는데,
     * admin 공통코드에 05 가 아직 없을 때(또는 캐시 갱신 전)는 LabOrderService.createOrder() 안의
     * validateCode(RCPT_TYPE_CD, ...)가 LAB017 로 거절한다. 이 클래스는 LabOrderService 를
     * 통째로 mock 하므로 CommonCodeCache 를 직접 거치지 않는다 — 그 대신 createOrder 가 던지는
     * 거절을 그대로 흉내 내, intake() 가 (a) 예외를 삼키지 않고 다시 던지는지 (b) 수신 기록에
     * LAB017 로 남기는지를 확인한다(둘 다 05 등록 전 상태에서 실제로 일어날 동작이다).
     */
    @Test
    @DisplayName("encounterType=IP인데 진료구분 05가 공통코드에 없으면 LAB017로 거절되고 수신 기록에 남는다")
    void intakeInpatientRejectedWhenTreatTypeCodeNotRegistered() {
        LabImagingBusinessException rejection = new LabImagingBusinessException(
                LabMessageCode.LAB017, "유효하지 않은 진료구분코드입니다. (RCPT_TYPE_CD=05)");
        when(labOrderService.createOrder(any())).thenThrow(rejection);

        assertThatThrownBy(() -> service.intake(request("IP", null)))
                .isSameAs(rejection);

        verify(interfaceReceiveLogService).markResult(
                eq("log-1"), eq(LabMessageCode.LAB017), anyString());
    }

    /**
     * 04번 지시서 §0-2/Phase 2-E "intake-never-rejects" — doctorId가 직원 디렉터리에서
     * 의사로 확인되지 않아도(또는 디렉터리 조회 자체가 실패해도) 접수는 그대로 진행된다.
     * StaffValidator 를 전혀 쓰지 않고 WARN 로그만 남기는 설계이므로, 모드 설정과 무관하게
     * 항상 통과해야 한다 — 이 테스트가 그 보장을 직접 확인한다.
     */
    @Test
    @DisplayName("doctorId가 의사로 확인되지 않아도 접수는 그대로 진행된다(거절하지 않는다)")
    void intakeProceedsEvenWhenDoctorIdNotRecognizedAsDoctor() {
        when(staffDirectoryCache.isAvailable()).thenReturn(true);
        when(staffDirectoryCache.isDoctor("doctor-1")).thenReturn(false);

        service.intake(request("OPD", null));

        verify(labOrderService).createOrder(any());
    }

    /**
     * 직원 디렉터리 조회 자체가 예외를 던져도(admin 장애 등) intake는 영향받지 않는다.
     * StaffDirectoryCache 내부도 실패를 삼키지만, 혹시 그 보장이 깨지는 경우까지
     * LabOrderIntakeService 자체의 try-catch 가 한 번 더 막아준다.
     */
    @Test
    @DisplayName("직원 디렉터리 조회가 예외를 던져도 접수는 그대로 진행된다")
    void intakeProceedsEvenWhenStaffDirectoryThrows() {
        when(staffDirectoryCache.isAvailable()).thenThrow(new RuntimeException("admin down"));

        service.intake(request("OPD", null));

        verify(labOrderService).createOrder(any());
    }
}
