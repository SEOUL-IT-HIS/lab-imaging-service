package kr.co.seoulit.his.labimagingservice.imagingacquisition.mapper;

import kr.co.seoulit.his.labimagingservice.imagingacquisition.dto.ImageFileSummaryDto;
import kr.co.seoulit.his.labimagingservice.imagingacquisition.entity.ImageFileEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * 영상파일 Entity → 응답 DTO 매핑.
 *
 * ⚠ imageOrderItemId 는 자동 매핑되지 않는다. 엔티티가 가진 것은 ImageOrderItemEntity 참조
 *   (imageOrderItem)이고, MapStruct 는 imageOrderItem.getImageOrderItemId() 까지 스스로
 *   찾아 들어가지 않는다. 명시하지 않으면 컴파일은 통과하는데 응답의 imageOrderItemId 만
 *   null 로 나간다. (ConsentMapper.toResponse 의 imageOrderId 와 같은 함정)
 */
@Mapper(componentModel = "spring")
public interface ImageFileMapper {

    @Mapping(target = "imageOrderItemId", source = "imageOrderItem.imageOrderItemId")
    ImageFileSummaryDto toResponse(ImageFileEntity saved);

    /** 목록 매핑. MapStruct 가 위 toResponse 를 요소별로 재사용한다. */
    List<ImageFileSummaryDto> toResponseList(List<ImageFileEntity> files);
}
