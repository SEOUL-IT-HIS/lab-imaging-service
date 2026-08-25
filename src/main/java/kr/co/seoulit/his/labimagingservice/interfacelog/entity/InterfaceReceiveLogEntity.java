package kr.co.seoulit.his.labimagingservice.interfacelog.entity;

import jakarta.persistence.*;
import kr.co.seoulit.his.labimagingservice.common.entity.BaseAuditEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 연계 수신 로그 (INTERFACE_RECEIVE_LOG)
 *
 * 외부 시스템(현재는 처방코어)이 보낸 요청의 원문과 처리 결과를 남긴다.
 *
 * ⚠ 이 로그의 존재 이유는 "실패했을 때 무엇이 들어왔는지 보는 것"이다.
 *   그래서 업무 처리와 트랜잭션을 분리한다. 자세한 사유는
 *   InterfaceReceiveLogService 의 REQUIRES_NEW 주석 참고.
 *
 * ⚠ 코어는 커밋 후 1회만 보내고 자동 재시도가 없다. 수신 기록이 유일한 추적 수단이다.
 */
@Entity
@Table(name = "INTERFACE_RECEIVE_LOG")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterfaceReceiveLogEntity extends BaseAuditEntity {

    @Id
    @Column(name = "interface_receive_log_id", length = 36, nullable = false, updatable = false)
    private String interfaceReceiveLogId;

    /**
     * ⚠ @Enumerated(EnumType.STRING) 을 빠뜨리면 안 된다.
     *   기본값 ORDINAL 은 "LAB" 대신 선언 순서 숫자(0,1)로 저장해, 나중에 상수 순서를 바꾸면
     *   이미 쌓인 로그의 의미가 뒤집힌다. (SpecimenAcceptanceEntity.fitnessStatusCode 와 같은 이유)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "order_type_code", length = 10, nullable = false)
    private InterfaceOrderType orderTypeCode;

    /** 수신 출처 (공통코드 SYSTEM_SOURCE_CD). 처방코어는 "OP". */
    @Column(name = "system_code", length = 10, nullable = false)
    private String systemCode;

    /**
     * 수신 원문(JSON).
     *
     * ⚠ CLOB 이라 @Lob 이 필요하다. 붙이지 않으면 Hibernate 가 VARCHAR 로 보고
     *   ddl-auto=validate 단계에서 타입 불일치로 기동이 막힌다.
     */
    @Lob
    @Column(name = "raw_message")
    private String rawMessage;

    /** 처리 결과 코드. 수신 직후에는 "RECEIVED", 처리 후 LabMessageCode 값으로 덮어쓴다. */
    @Column(name = "result_code", length = 10, nullable = false)
    private String resultCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    /**
     * ⚠ PK(interfaceReceiveLogId)는 빌더에서 제외한다. @PrePersist 에서 채운다.
     *   errorMessage 도 제외한다. 수신 시점에는 아직 결과를 모르고, markResult 로만 채워야
     *   "결과 없이 오류메시지만 있는" 행이 생기지 않는다.
     */
    @Builder
    public InterfaceReceiveLogEntity(InterfaceOrderType orderTypeCode, String systemCode,
                                     String rawMessage, String resultCode, LocalDateTime receivedAt) {
        this.orderTypeCode = orderTypeCode;
        this.systemCode = systemCode;
        this.rawMessage = rawMessage;
        this.resultCode = resultCode;
        this.receivedAt = receivedAt;
    }

    @PrePersist
    private void generateId() {
        if (this.interfaceReceiveLogId == null) {
            this.interfaceReceiveLogId = UUID.randomUUID().toString();
        }
    }

    /**
     * 처리 결과를 기록한다.
     *
     * ⚠ setter 대신 의미 있는 메서드로 연다. 결과코드와 오류메시지는 항상 함께 정해지는 값이라,
     *   따로 바꿀 수 있게 두면 "성공인데 오류메시지가 남아 있는" 행이 생긴다.
     *   errorMessage 는 컬럼이 500자라 넘치면 잘라 담는다. 로그 한 줄 때문에 기록 자체가
     *   실패하면 안 되기 때문이다.
     */
    public void markResult(String resultCode, String errorMessage) {
        this.resultCode = resultCode;
        this.errorMessage = truncate(errorMessage, 500);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
