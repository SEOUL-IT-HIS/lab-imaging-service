package kr.co.seoulit.his.labimagingservice.laborder.service;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.exception.DuplicateOrderException;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.interfacelog.entity.InterfaceOrderType;
import kr.co.seoulit.his.labimagingservice.interfacelog.service.InterfaceReceiveLogService;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderIntakeRequestDto;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderIntakeResultDto;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderItemRequestDto;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 검사오더 연계 수신 서비스 (처방코어 → 검사영상서비스)
 *
 * ⚠ Service 인터페이스 없이 클래스로 바로 구현한다. 사유는 LabOrderService 주석 참고.
 *
 * ── 이 클래스가 하는 일은 세 가지뿐이다
 *   1) 수신 원문을 남긴다
 *   2) 코어 계약(LabOrderIntakeRequestDto) → 검사 도메인 계약(LabOrderCreateRequestDto) 변환
 *   3) 기존 LabOrderService.createOrder 호출 후 결과를 로그에 기록
 *
 *   저장 로직을 여기서 다시 만들지 않는다. createOrder 가 이미 환자 검증·공통코드 검증·중복 확인·
 *   LAB_ORDER/LAB_ORDER_ITEM/LAB_RECEPTION 생성을 한 트랜잭션으로 처리한다.
 *   수동 등록 폼과 연계 수신이 같은 경로를 타야 결과가 갈리지 않는다.
 *
 * ⚠ Kafka 로 바뀌어도 이 클래스는 그대로 재사용한다.
 *   지금은 Controller 가 부르고 나중에는 Consumer 가 부를 뿐, 변환·저장·로그는 동일하다.
 *   그래서 이 클래스에 HTTP 관련 타입(ResponseEntity 등)을 두지 않았다.
 */
@Service
@RequiredArgsConstructor
public class LabOrderIntakeService {

    /**
     * 수신 출처 (공통코드 SYSTEM_SOURCE_CD) — 외래 기본값.
     *
     * ⚠ 2026-09-16 admin에 실제로 등록된 값으로 정정 — "OP"가 아니라 "01"이다.
     *   admin SYSTEM_SOURCE_CD 그룹은 WARD/ER/OP 같은 영문 약어가 아니라 01~05 숫자코드로
     *   되어 있다(01=Outpatient, 02=Emergency, 03=Ward, 04=Lab, 05=Surgery — admin에
     *   직접 조회해서 확인함). "OP"로 나가면 CommonCodeCache에 없는 값이라 매 건 REJECTED된다.
     */
    public static final String SYSTEM_CODE_OUTPATIENT = "01";
    private static final String SYSTEM_CODE_EMERGENCY = "02";
    private static final String SYSTEM_CODE_WARD = "03";

    /**
     * 진료구분 (공통코드 RCPT_TYPE_CD) — 외래/응급 기본값.
     * ⚠ 2026-09-16 admin 실측값: 01=Outpatient, 02=Emergency, 03=Pre-admission, 04=Health Checkup.
     */
    private static final String TREAT_TYPE_OUTPATIENT = "01";
    private static final String TREAT_TYPE_EMERGENCY = "02";
    /**
     * ⚠ 2026-10-01 병동 처방 연동 확인 과정에서 admin에 "05=Inpatient" 신규 등록을 요청했다
     *   (RCPT_TYPE_CD 에는 입원에 해당하는 값이 없었다 — 03=Pre-admission 은 "입원 예정"이라 의미가 다르다).
     *   등록 전까지 encounterType=IP 로 들어오는 건은 validateCode 가 LAB017 로 거절한다 —
     *   틀린 값으로 조용히 저장하는 것보다 안전하다(fail-closed). 등록되면 그 즉시 정상 동작한다.
     */
    private static final String TREAT_TYPE_INPATIENT = "05";

    /** 진료 채널 — 코어 payload 의 encounterType 값(2026-10-01 요청, 아직 미확정). */
    private static final String ENCOUNTER_TYPE_EMERGENCY = "ER";
    private static final String ENCOUNTER_TYPE_INPATIENT = "IP";

    private static final String URGENCY_YES = "Y";
    private static final String URGENCY_NO = "N";

    /**
     * 접수담당자.
     * ⚠ 사람이 접수한 것이 아니라 시스템이 받은 것이라 "SYSTEM" 으로 남긴다.
     *   doctorId 를 넣지 않는 이유는 두 가지다.
     *   - 의미가 다르다. 처방을 낸 의사는 접수 담당자가 아니다.
     *   - 컬럼이 안 맞는다. received_by_id 는 VARCHAR2(20) 인데 doctorId 는 36자까지 온다.
     *   처방의는 physicianId(36자) 자리에 넣는다.
     */
    private static final String RECEIVED_BY_SYSTEM = "SYSTEM";

    private final LabOrderService labOrderService;
    private final InterfaceReceiveLogService interfaceReceiveLogService;

    /**
     * ⚠ import 가 tools.jackson.databind.ObjectMapper 다. com.fasterxml 이 아니다.
     *   Spring Boot 4 는 Jackson 3 을 쓰고, 패키지가 com.fasterxml.jackson → tools.jackson 으로 바뀌었다.
     *   클래스패스에 com.fasterxml 쪽 Jackson 2 도 (springdoc 등을 통해) 딸려 오지만
     *   Boot 가 빈으로 등록하는 것은 Jackson 3 쪽뿐이라, com.fasterxml 의 ObjectMapper 를 주입받으려 하면
     *   "required a bean of type ObjectMapper that could not be found" 로 기동이 실패한다.
     *   (RestTemplateBuilder 패키지가 옮겨간 것과 같은 계열의 Boot 4 변경)
     */
    private final ObjectMapper objectMapper;

    /**
     * 코어가 보낸 검사오더를 받아 접수를 생성한다.
     *
     * ⚠ 이 메서드에는 @Transactional 을 걸지 않는다. 빠뜨린 게 아니라 그래야 한다.
     *   걸면 createOrder() 의 트랜잭션이 여기에 합류해서, 업무가 실패해 롤백될 때
     *   수신 로그까지 같이 사라진다. 실패 원인을 보려고 만든 로그가 실패할 때만 없어지는 셈이다.
     *   트랜잭션 밖에 두면 createOrder() 는 자기 트랜잭션으로 독립 동작하고,
     *   로그는 REQUIRES_NEW 로 따로 커밋된다.
     *   (InterfaceReceiveLogService 주석과 짝을 이루는 규칙 — 한쪽만 지키면 분리가 깨진다)
     *
     * ⚠ 실패해도 예외를 삼키지 않고 그대로 다시 던진다.
     *   결과 변환은 Controller 의 지역 @ExceptionHandler 가 맡는다.
     *   여기서 결과 객체로 바꿔버리면 Kafka Consumer 로 옮겼을 때 재처리 판단을 할 수 없다.
     */
    public LabOrderIntakeResultDto intake(LabOrderIntakeRequestDto request) {

        // 1) 업무 처리 "전에" 원문을 남긴다. 처리 중 무슨 일이 나도 들어온 내용은 남아 있어야 한다.
        String logId = interfaceReceiveLogService.logReceived(
                InterfaceOrderType.LAB, resolveSystemCode(request.getEncounterType()), toRawMessage(request));

        try {
            LabOrderSummaryDto saved = labOrderService.createOrder(toCreateRequest(request));

            interfaceReceiveLogService.markResult(logId, LabMessageCode.LAB001, null);
            return LabOrderIntakeResultDto.success(saved.getLabOrderId());

        } catch (DuplicateOrderException e) {
            // 이미 접수된 처방이다(LAB004). 코어가 같은 처방을 두 번 보낸 경우.
            interfaceReceiveLogService.markResult(logId, e.getMessageCode(), e.getMessage());
            throw e;

        } catch (LabImagingBusinessException e) {
            // 업무 규칙에 걸린 실패(환자ID 오류, 공통코드 오류 등). 코드가 이미 의미를 담고 있다.
            interfaceReceiveLogService.markResult(logId, e.getMessageCode(), e.getMessage());
            throw e;

        } catch (RuntimeException e) {
            // 예상 못 한 실패. 코드로 구분할 수 없으니 LAB999 로 남기고 원인만 기록한다.
            interfaceReceiveLogService.markResult(logId, LabMessageCode.LAB999, e.getMessage());
            throw e;
        }
    }

    /**
     * 수신 원문을 문자열로 만든다.
     *
     * ⚠ 직렬화가 실패해도 예외를 던지지 않는다. 원문을 못 남긴다고 해서 정상적인 오더 접수를
     *   막을 이유가 없다. 대신 "왜 원문이 없는지"를 그 자리에 남긴다.
     */
    private String toRawMessage(LabOrderIntakeRequestDto request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JacksonException e) {
            // ⚠ Jackson 3 부터 직렬화 예외가 unchecked 라 컴파일러가 강제하지 않는다.
            //   그래도 잡아 둔다. 안 잡으면 로그 남기다 실패해서 정상 접수가 막힌다.
            return "원문 직렬화 실패: " + e.getMessage();
        }
    }

    /**
     * 코어 계약 → 검사 도메인 계약 변환.
     *
     * 코어에 없는 값은 여기서 채운다. 각 값을 왜 그렇게 정했는지는 위 상수 주석에 있다.
     *
     * ⚠ encounterId / admissionId 는 옮기지 않는다. 저장할 컬럼이 없기 때문이고, 수신 원문
     *   (raw_message)에는 남아 있어 추적은 가능하다. 진료건·입원 단위 추적이 실제로 필요해지면
     *   LAB_ORDER 에 컬럼 추가를 검토한다. (1차 배포 이후 과제)
     *
     * ⚠ itemName 도 옮기지 않는다. 표시명을 우리 DB 에 복사해두면 admin 에서 이름을 고쳤을 때
     *   화면마다 다른 이름이 보인다. 이름은 항상 공통코드에서 읽는다.
     */
    private LabOrderCreateRequestDto toCreateRequest(LabOrderIntakeRequestDto request) {
        List<LabOrderItemRequestDto> orderItems = request.getOrderItems().stream()
                .map(item -> LabOrderItemRequestDto.builder()
                        .labItemCode(item.getItemCode())
                        .build())
                .toList();

        return LabOrderCreateRequestDto.builder()
                // 처방ID를 오더번호로 쓴다. 코어가 재시도하지 않으므로 이 값이 중복 판정의 기준이 된다.
                .labOrderNo(request.getPrescriptionId())
                .systemCode(resolveSystemCode(request.getEncounterType()))
                .patientId(request.getPatientId())
                // 코어가 처방의 "번호"를 주지 않는다. ID만 온다.
                .physicianNo(null)
                .physicianId(request.getDoctorId())
                .treatTypeCode(resolveTreatTypeCode(request.getEncounterType()))
                .urgencyYn(resolveUrgencyYn(request.getUrgencyYn()))
                .receivedById(RECEIVED_BY_SYSTEM)
                .orderItems(orderItems)
                .build();
    }

    /**
     * 진료 채널(encounterType) → 연계시스템코드(SYSTEM_SOURCE_CD). (2026-10-01)
     * ⚠ null 이거나 ER/IP 가 아니면 전부 외래로 간주한다 — 코어가 아직 이 필드를 안 보내는
     *   과도기에도 지금까지와 완전히 같게 동작해야 한다(하위호환).
     * ⚠ public이다 — LabOrderRequestedConsumer 가 수신 기록(logReceived)의 system_code 를
     *   같은 기준으로 남기려고 그대로 재사용한다(로직 중복을 피한다).
     */
    public static String resolveSystemCode(String encounterType) {
        if (ENCOUNTER_TYPE_EMERGENCY.equalsIgnoreCase(encounterType)) {
            return SYSTEM_CODE_EMERGENCY;
        }
        if (ENCOUNTER_TYPE_INPATIENT.equalsIgnoreCase(encounterType)) {
            return SYSTEM_CODE_WARD;
        }
        return SYSTEM_CODE_OUTPATIENT;
    }

    /**
     * 진료 채널(encounterType) → 진료구분(RCPT_TYPE_CD). (2026-10-01)
     * ⚠ IP 매핑값("05")은 admin 신규 등록 요청 중이다 — 위 TREAT_TYPE_INPATIENT 주석 참고.
     *   등록 전에 IP 건이 오면 이 메서드는 "05"를 그대로 돌려주고, 그 다음 공통코드 검증
     *   (LabOrderService.validateCode)이 LAB017 로 거절한다. 틀린 값(01)으로 조용히 저장하는 것보다
     *   그 편이 안전하다 — fail-closed 는 이 서비스 전반의 원칙이다(PatientServiceHttpBusinessDelegate 참고).
     */
    static String resolveTreatTypeCode(String encounterType) {
        if (ENCOUNTER_TYPE_EMERGENCY.equalsIgnoreCase(encounterType)) {
            return TREAT_TYPE_EMERGENCY;
        }
        if (ENCOUNTER_TYPE_INPATIENT.equalsIgnoreCase(encounterType)) {
            return TREAT_TYPE_INPATIENT;
        }
        return TREAT_TYPE_OUTPATIENT;
    }

    /** 응급여부 — "Y"만 Y 로 인정한다. null·빈 값·그 외 전부 N(2026-10-01 이전과 동일). */
    static String resolveUrgencyYn(String urgencyYn) {
        return URGENCY_YES.equalsIgnoreCase(urgencyYn) ? URGENCY_YES : URGENCY_NO;
    }
}
