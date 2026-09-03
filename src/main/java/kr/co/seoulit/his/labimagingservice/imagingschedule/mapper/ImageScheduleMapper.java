package kr.co.seoulit.his.labimagingservice.imagingschedule.mapper;

import kr.co.seoulit.his.labimagingservice.imagingschedule.dto.ImageScheduleResponseDto;
import kr.co.seoulit.his.labimagingservice.imagingschedule.entity.ImageScheduleEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ImageScheduleMapper {
    /**
     * ⚠ imageOrderItem 은 @ManyToOne(LAZY) 라 매핑 시점에 지연로딩이 한 번 일어난다.
     *   단건 응답이라 부담이 없지만, 목록에서 재사용하면 행마다 SELECT 가 붙는다(N+1).
     */
    @Mapping(target = "imageReceptionId", source = "imageReception.imageReceptionId")
    @Mapping(target = "imageOrderItemId", source = "imageOrderItem.imageOrderItemId")
    @Mapping(target = "imageItemCode", source = "imageOrderItem.imageItemCode")
    ImageScheduleResponseDto toResponse(ImageScheduleEntity saved);
}
