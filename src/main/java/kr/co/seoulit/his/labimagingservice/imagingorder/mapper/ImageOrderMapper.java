package kr.co.seoulit.his.labimagingservice.imagingorder.mapper;

import kr.co.seoulit.his.labimagingservice.imagingorder.dto.ImageOrderSummaryDto;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageOrderEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageReceptionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * ImageOrder(+ImageReception) 엔티티 -> 응답 DTO 변환.
 * 오더번호 중복확인, 상태코드 기본값, 접수번호 채번 등 업무 로직은 ImageOrderService에 남겨두고,
 * 이 매퍼는 순수 필드 매핑만 담당한다.
 */
@Mapper(componentModel = "spring")
public interface ImageOrderMapper {

    @Mapping(target = "imageOrderId", source = "order.imageOrderId")
    @Mapping(target = "imageOrderNo", source = "order.imageOrderNo")
    @Mapping(target = "orderStatusCode", source = "order.orderStatusCode")
    @Mapping(target = "imageReceptionId", source = "reception.imageReceptionId")
    @Mapping(target = "receptionNo", source = "reception.receptionNo")
    @Mapping(target = "receptionStatusCode", source = "reception.receptionStatusCode")
    @Mapping(target = "patientNo", source = "reception.patientNo")
    ImageOrderSummaryDto toResponse(ImageOrderEntity order, ImageReceptionEntity reception);
}
