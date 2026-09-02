package kr.co.seoulit.his.labimagingservice.labresult.mapper;

import kr.co.seoulit.his.labimagingservice.labresult.dto.LabResultSummaryDto;
import kr.co.seoulit.his.labimagingservice.labresult.entity.LabResultEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 일반검사 결과 Entity → 응답 DTO 매핑.
 *
 * ⚠ labOrderItem 은 @OneToOne(LAZY) 라 매핑 시점에 지연로딩이 한 번 일어난다.
 *   단건 응답에서는 부담이 없지만, 목록에서 재사용하면 행마다 SELECT 가 붙는다(N+1).
 *   목록 조회가 생기면 join fetch 로 미리 로딩해서 넘겨야 한다.
 *   (SpecimenAcceptanceMapper 에 적어 둔 주의와 같은 내용)
 */
@Mapper(componentModel = "spring")
public interface LabResultMapper {

    @Mapping(target = "labOrderItemId", source = "labOrderItem.labOrderItemId")
    @Mapping(target = "labItemCode", source = "labOrderItem.labItemCode")
    LabResultSummaryDto toResponse(LabResultEntity labResult);
}
