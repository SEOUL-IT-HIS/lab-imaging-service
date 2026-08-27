package kr.co.seoulit.his.labimagingservice.laborder.messaging.dto;

/**
 * 검사오더 접수 결과 (LAB → OPD)
 *
 * ⚠ 업무 거절(REJECTED)은 "실패"가 아니라 정상 응답이다.
 *   환자ID 오류·공통코드 오류·중복 처방은 재시도해도 결과가 같으므로,
 *   Consumer 가 이 상태로 결과를 발행하고 정상 종료한다. (요청서 6장)
 */
public enum LabOrderResultStatus {

    /** 접수 생성됨. labOrderId 가 함께 간다. */
    ACCEPTED,

    /** 업무 규칙에 걸려 거절됨. reason 이 함께 간다. */
    REJECTED
}
