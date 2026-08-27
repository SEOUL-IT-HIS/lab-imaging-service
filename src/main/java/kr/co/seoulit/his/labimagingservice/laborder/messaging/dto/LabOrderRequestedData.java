package kr.co.seoulit.his.labimagingservice.laborder.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 검사오더 요청 payload (OPD → LAB)
 * 토픽: opd.lab-order.requested.v1
 *
 * ⚠ patientNo 가 없다. 이건 우리 결정과 맞다 — 2026-08-25 에 환자번호를 화면·DTO 에서 전부 뺐다.
 *   환자 식별은 patientId 로 하고, 화면에 필요한 환자명은 그 ID 로 patient-service 에 물어본다.
 *
 * ⚠ REST 수신 DTO(LabOrderIntakeRequestDto)와 필드가 같지만 별도 클래스로 둔다.
 *   이쪽은 "Kafka 계약", 저쪽은 "REST 계약"이다. 한쪽이 바뀌어도 다른 쪽이 흔들리지 않아야 한다.
 *   두 계약을 잇는 변환은 LabOrderRequestedConsumer.toIntakeRequest 가 담당한다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LabOrderRequestedData {

    /** 처방ID. 검사오더번호(lab_order_no)로 사용하며 중복 판정 기준이다. */
    private String prescriptionId;

    /** 진료건ID. 저장할 컬럼이 없어 수신 원문(raw_message)에만 남는다. */
    private String encounterId;

    private String patientId;

    /** 처방의ID */
    private String doctorId;

    private List<Item> orderItems;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {

        /** 검사항목코드 (공통코드 TEST_TYPE_CD — LFT / UA / CBC / BST) */
        private String itemCode;

        /**
         * ⚠ 받되 저장하지 않는다. 표시명은 admin 공통코드에서 읽는다.
         *   복사해두면 admin 에서 이름을 고쳤을 때 화면마다 다른 이름이 보인다.
         *   (개발표준가이드 14.1 스냅샷 금지 — LabOrderIntakeService.toCreateRequest 와 같은 이유)
         */
        private String itemName;
    }
}
