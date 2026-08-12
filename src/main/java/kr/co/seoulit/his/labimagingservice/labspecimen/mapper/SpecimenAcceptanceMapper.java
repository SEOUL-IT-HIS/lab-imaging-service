package kr.co.seoulit.his.labimagingservice.labspecimen.mapper;

import kr.co.seoulit.his.labimagingservice.labspecimen.dto.SpecimenAcceptanceSummaryDto;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenAcceptanceEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 검체 인수/적합성 판정 Entity → 응답 DTO 매핑.
 */
@Mapper(componentModel = "spring")
public interface SpecimenAcceptanceMapper {
    @Mapping(target = "specimenId", source = "specimen.specimenId")
    SpecimenAcceptanceSummaryDto toResponse(SpecimenAcceptanceEntity saved);
}
