package kr.co.seoulit.his.labimagingservice.labspecimen.mapper;

import kr.co.seoulit.his.labimagingservice.labspecimen.dto.SpecimenAcceptanceSummaryDto;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenAcceptanceEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 검체 인수/적합성 판정 Entity → 응답 DTO 매핑.
 *
 * ⚠ 판정 결과만으로는 "무슨 검체를 판정했는지" 알 수 없어 검체 정보를 함께 꺼낸다.
 *   specimen 은 @OneToOne(LAZY) 라 조회 시 join fetch 로 함께 로딩해야 한다.
 *   (단건 응답이라 N+1 부담은 없지만, 목록에서 재사용할 때 주의)
 */
@Mapper(componentModel = "spring")
public interface SpecimenAcceptanceMapper {

    @Mapping(target = "specimenId", source = "specimen.specimenId")
    @Mapping(target = "receptionNo", source = "specimen.labReception.receptionNo")
    @Mapping(target = "specimenBarcode", source = "specimen.specimenBarcode")
    @Mapping(target = "specimenType", source = "specimen.specimenTypeCode")
    @Mapping(target = "patientNo", source = "specimen.patientNo")
    @Mapping(target = "fitnessStatus", source = "fitnessStatusCode")
    SpecimenAcceptanceSummaryDto toResponse(SpecimenAcceptanceEntity saved);
}
