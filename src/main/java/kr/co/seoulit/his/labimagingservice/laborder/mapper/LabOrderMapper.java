package kr.co.seoulit.his.labimagingservice.laborder.mapper;

import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderSummaryDto;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabReceptionDetailDto;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabReceptionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;

/**
 * LabOrder(+LabReception) 엔티티 -> 응답 DTO 변환.
 * 오더번호 중복확인, 상태코드 기본값, 접수번호 채번 등 업무 로직은 LabOrderService에 남겨두고,
 * 이 매퍼는 순수 필드 매핑만 담당한다.
 */
@Mapper(componentModel = "spring")
public interface LabOrderMapper {

    @Mapping(target = "labOrderId", source = "order.labOrderId")
    @Mapping(target = "labOrderNo", source = "order.labOrderNo")
    @Mapping(target = "orderStatusCode", source = "order.orderStatusCode")
    @Mapping(target = "labReceptionId", source = "reception.labReceptionId")
    @Mapping(target = "receptionNo", source = "reception.receptionNo")
    @Mapping(target = "receptionStatusCode", source = "reception.receptionStatusCode")
    @Mapping(target = "patientNo", source = "reception.patientNo")
    @Mapping(target = "scheduledAt", ignore = true)
    LabOrderSummaryDto toResponse(LabOrderEntity order, LabReceptionEntity reception);

    /**
     * 예정일시까지 채우는 목록용 매핑.
     *
     * ⚠ scheduledAt 은 엔티티에서 바로 꺼낼 수 없다. LAB_SCHEDULE 은 접수 1건에 이력이 여러 건 쌓이고
     *   그중 latest_yn='Y' 인 것만 유효한데, reception.schedules 를 매퍼가 훑으면 행마다 지연로딩이
     *   일어난다(N+1). 그래서 Service 가 IN 절로 한 번에 조회해 값만 넘긴다.
     */
    @Mapping(target = "labOrderId", source = "order.labOrderId")
    @Mapping(target = "labOrderNo", source = "order.labOrderNo")
    @Mapping(target = "orderStatusCode", source = "order.orderStatusCode")
    @Mapping(target = "labReceptionId", source = "reception.labReceptionId")
    @Mapping(target = "receptionNo", source = "reception.receptionNo")
    @Mapping(target = "receptionStatusCode", source = "reception.receptionStatusCode")
    @Mapping(target = "patientNo", source = "reception.patientNo")
    @Mapping(target = "scheduledAt", source = "scheduledAt")
    LabOrderSummaryDto toResponse(LabOrderEntity order, LabReceptionEntity reception,
                                  LocalDateTime scheduledAt);

    /**
     * 상세 화면용 매핑. 목록과 달리 검사항목(labItemCodes)까지 담는다.
     *
     * ⚠ order.orderItems 는 지연로딩이라 반드시 트랜잭션 안에서 호출해야 한다.
     *   단건 조회라 추가 쿼리 1회로 끝나므로 목록에서와 달리 문제되지 않는다.
     */
    @Mapping(target = "labReceptionId", source = "reception.labReceptionId")
    @Mapping(target = "receptionNo", source = "reception.receptionNo")
    @Mapping(target = "receptionStatusCode", source = "reception.receptionStatusCode")
    @Mapping(target = "receivedById", source = "reception.receivedById")
    @Mapping(target = "patientNo", source = "reception.patientNo")
    @Mapping(target = "labOrderNo", source = "order.labOrderNo")
    @Mapping(target = "treatTypeCode", source = "order.treatTypeCode")
    @Mapping(target = "urgencyYn", source = "order.urgencyYn")
    @Mapping(target = "physicianNo", source = "order.physicianNo")
    @Mapping(target = "orderStatusCode", source = "order.orderStatusCode")
    @Mapping(target = "receivedAt", source = "order.receivedAt")
    @Mapping(target = "labItemCodes", source = "order.orderItems")
    @Mapping(target = "scheduledAt", source = "scheduledAt")
    LabReceptionDetailDto toDetailResponse(LabOrderEntity order, LabReceptionEntity reception,
                                           LocalDateTime scheduledAt);

    /** List&lt;LabOrderItemEntity&gt; → List&lt;String&gt; 변환에 MapStruct 가 요소별로 이 메서드를 쓴다. */
    default String toLabItemCode(LabOrderItemEntity item) {
        return item.getLabItemCode();
    }
}
