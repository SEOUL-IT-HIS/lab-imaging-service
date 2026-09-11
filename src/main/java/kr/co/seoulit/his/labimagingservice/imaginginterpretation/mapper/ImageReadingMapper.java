package kr.co.seoulit.his.labimagingservice.imaginginterpretation.mapper;

import kr.co.seoulit.his.labimagingservice.imaginginterpretation.dto.ImageReadingSummaryDto;
import kr.co.seoulit.his.labimagingservice.imaginginterpretation.entity.ImageReadingEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 영상판독 Entity → 응답 DTO 매핑.
 *
 * ⚠ imageOrderItem, imageOrderItem.imageOrder 모두 LAZY 라 매핑 시점에 지연로딩이 일어날 수 있다.
 *   워크리스트/findOrCreate 조회는 ImageReadingRepository 의 join fetch 메서드를 거치므로 문제가
 *   없지만, assign/updateFindings/confirm 은 findById 로 받은 엔티티라 이 매퍼가 imageOrder 쪽
 *   필드를 꺼내는 순간 지연로딩이 한 번(또는 두 번) 더 일어난다. 단건 처리라 부담은 없지만,
 *   이 매퍼를 목록에 재사용하는 코드가 생기면 join fetch 조회로 바꿔야 한다.
 *   (LabResultMapper 에 적어 둔 주의와 같은 내용)
 */
@Mapper(componentModel = "spring")
public interface ImageReadingMapper {

    @Mapping(target = "imageOrderItemId", source = "imageOrderItem.imageOrderItemId")
    @Mapping(target = "imageItemCode", source = "imageOrderItem.imageItemCode")
    @Mapping(target = "imageOrderId", source = "imageOrderItem.imageOrder.imageOrderId")
    @Mapping(target = "patientId", source = "imageOrderItem.imageOrder.patientId")
    @Mapping(target = "urgencyYn", source = "imageOrderItem.imageOrder.urgencyYn")
    ImageReadingSummaryDto toResponse(ImageReadingEntity reading);
}
