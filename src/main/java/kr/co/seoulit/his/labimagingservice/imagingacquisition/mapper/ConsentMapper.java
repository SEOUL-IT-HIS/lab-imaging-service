package kr.co.seoulit.his.labimagingservice.imagingacquisition.mapper;

import kr.co.seoulit.his.labimagingservice.imagingacquisition.dto.ConsentSummaryDto;
import kr.co.seoulit.his.labimagingservice.imagingacquisition.entity.ConsentEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * 동의서 Entity → 응답 DTO 매핑.
 *
 * ⚠ patientId / documentTemplateId 는 ConsentSummaryDto 에 없다. 빠뜨린 게 아니라
 *   화면 응답에 넣지 않기로 한 것이다. (DTO 주석 참고)
 */
@Mapper(componentModel = "spring")
public interface ConsentMapper {

    /**
     * ⚠ imageOrderId 는 자동 매핑되지 않는다. 엔티티가 가진 것은 ImageOrderEntity 참조(imageOrder)이고,
     *   MapStruct 는 imageOrder.getImageOrderId() 까지 스스로 찾아 들어가지 않는다.
     *   명시하지 않으면 컴파일은 통과하는데 응답의 imageOrderId 만 null 로 나간다.
     */
    @Mapping(target = "imageOrderId", source = "imageOrder.imageOrderId")
    ConsentSummaryDto toResponse(ConsentEntity saved);

    /** 목록 매핑. MapStruct 가 위 toResponse 를 요소별로 재사용한다. */
    List<ConsentSummaryDto> toResponseList(List<ConsentEntity> consents);
}
