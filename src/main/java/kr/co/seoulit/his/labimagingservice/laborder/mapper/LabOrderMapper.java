package kr.co.seoulit.his.labimagingservice.laborder.mapper;

import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderCreateResponseDto;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabReceptionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

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
    LabOrderCreateResponseDto toResponse(LabOrderEntity order, LabReceptionEntity reception);
}
