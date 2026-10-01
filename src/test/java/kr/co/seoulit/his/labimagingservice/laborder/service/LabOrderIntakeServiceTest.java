package kr.co.seoulit.his.labimagingservice.laborder.service;

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
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private final LabOrderIntakeService service =
            new LabOrderIntakeService(labOrderService, interfaceReceiveLogService, objectMapper);

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
}
