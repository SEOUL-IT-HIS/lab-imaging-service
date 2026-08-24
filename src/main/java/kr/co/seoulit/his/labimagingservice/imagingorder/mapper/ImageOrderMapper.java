package kr.co.seoulit.his.labimagingservice.imagingorder.mapper;

import kr.co.seoulit.his.labimagingservice.imagingorder.dto.ImageOrderSummaryDto;
import kr.co.seoulit.his.labimagingservice.imagingorder.dto.ImageReceptionDetailDto;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageOrderEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageReceptionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;

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
    @Mapping(target = "patientId", source = "reception.patientId")
    @Mapping(target = "scheduledAt", ignore = true)
    ImageOrderSummaryDto toResponse(ImageOrderEntity order, ImageReceptionEntity reception);

    /**
     * 예정일시까지 채우는 목록용 매핑.
     * (scheduledAt 을 Service 가 넘기는 이유는 LabOrderMapper 주석 참고 — N+1 회피)
     */
    @Mapping(target = "imageOrderId", source = "order.imageOrderId")
    @Mapping(target = "imageOrderNo", source = "order.imageOrderNo")
    @Mapping(target = "orderStatusCode", source = "order.orderStatusCode")
    @Mapping(target = "imageReceptionId", source = "reception.imageReceptionId")
    @Mapping(target = "receptionNo", source = "reception.receptionNo")
    @Mapping(target = "receptionStatusCode", source = "reception.receptionStatusCode")
    @Mapping(target = "patientNo", source = "reception.patientNo")
    @Mapping(target = "patientId", source = "reception.patientId")
    @Mapping(target = "scheduledAt", source = "scheduledAt")
    ImageOrderSummaryDto toResponse(ImageOrderEntity order, ImageReceptionEntity reception,
                                    LocalDateTime scheduledAt);

    /**
     * 상세 화면용 매핑. 목록과 달리 촬영항목(imageItemCodes)까지 담는다.
     * (지연로딩 주의사항은 LabOrderMapper.toDetailResponse 주석 참고)
     */
    @Mapping(target = "imageReceptionId", source = "reception.imageReceptionId")
    @Mapping(target = "receptionNo", source = "reception.receptionNo")
    @Mapping(target = "receptionStatusCode", source = "reception.receptionStatusCode")
    @Mapping(target = "receivedById", source = "reception.receivedById")
    @Mapping(target = "patientNo", source = "reception.patientNo")
    @Mapping(target = "imageOrderNo", source = "order.imageOrderNo")
    @Mapping(target = "treatTypeCode", source = "order.treatTypeCode")
    @Mapping(target = "urgencyYn", source = "order.urgencyYn")
    @Mapping(target = "physicianNo", source = "order.physicianNo")
    @Mapping(target = "orderStatusCode", source = "order.orderStatusCode")
    @Mapping(target = "receivedAt", source = "order.receivedAt")
    @Mapping(target = "imageItemCodes", source = "order.orderItems")
    @Mapping(target = "scheduledAt", source = "scheduledAt")
    ImageReceptionDetailDto toDetailResponse(ImageOrderEntity order, ImageReceptionEntity reception,
                                             LocalDateTime scheduledAt);

    /** List&lt;ImageOrderItemEntity&gt; → List&lt;String&gt; 변환에 MapStruct 가 요소별로 이 메서드를 쓴다. */
    default String toImageItemCode(ImageOrderItemEntity item) {
        return item.getImageItemCode();
    }
}
