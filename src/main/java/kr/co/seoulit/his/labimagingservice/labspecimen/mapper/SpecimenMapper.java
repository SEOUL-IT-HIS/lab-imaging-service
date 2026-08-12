package kr.co.seoulit.his.labimagingservice.labspecimen.mapper;


import kr.co.seoulit.his.labimagingservice.labspecimen.dto.SpecimenSummaryDto;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 검체 Entity → 응답 DTO 매핑.
 * ⚠ 메서드가 하나도 없으면 MapStruct 가 빈 구현체를 생성한다. 컴파일은 통과하지만
 *   실제로는 아무것도 못 하니, 매핑 메서드를 추가할 때까지는 Service 에서 주입하지 말 것.
 */
@Mapper(componentModel = "spring")
public interface SpecimenMapper {

    @Mapping(target = "labReceptionId", source = "labReception.labReceptionId")
    @Mapping(target = "specimenType", source = "specimenTypeCode")
    SpecimenSummaryDto toResponse(SpecimenEntity saved);


}
