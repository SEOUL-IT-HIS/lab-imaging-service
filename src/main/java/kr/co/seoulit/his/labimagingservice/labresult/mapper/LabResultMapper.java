package kr.co.seoulit.his.labimagingservice.labresult.mapper;

import kr.co.seoulit.his.labimagingservice.labresult.dto.LabResultDetailDto;
import kr.co.seoulit.his.labimagingservice.labresult.dto.LabResultSummaryDto;
import kr.co.seoulit.his.labimagingservice.labresult.entity.LabResultDetailEntity;
import kr.co.seoulit.his.labimagingservice.labresult.entity.LabResultEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * 일반검사 결과 Entity → 응답 DTO 매핑.
 *
 * ⚠ labOrderItem 은 @OneToOne(LAZY) 라 매핑 시점에 지연로딩이 한 번 일어난다.
 *   단건 응답에서는 부담이 없지만, 목록에서 재사용하면 행마다 SELECT 가 붙는다(N+1).
 *   목록 조회가 생기면 join fetch 로 미리 로딩해서 넘겨야 한다.
 *   (SpecimenAcceptanceMapper 에 적어 둔 주의와 같은 내용)
 *
 * ⚠ details(LabResultEntity.details, 6차)도 같은 문제가 있다 — @OneToMany(LAZY) 라
 *   목록 조회에서 결과마다 지연로딩이 붙는다. LabResultService.getResultItemsByReceptionNo 는
 *   그래서 이 매퍼로 상세를 자동 로딩시키지 않고 IN 절로 미리 조회한 상세를 엔티티에 붙여
 *   두지 않는 대신, 결과 DTO 조립 시 상세 목록을 별도 인자로 받는 오버로드를 쓴다.
 */
@Mapper(componentModel = "spring")
public interface LabResultMapper {

    /**
     * ⚠ details 는 무시한다(ignore). LabResultEntity.details 는 @OneToMany(LAZY) 라, 매퍼가
     *   자동으로 채우면 그 시점에 지연로딩이 발생해 목록 조회에서 N+1이 된다(클래스 주석 참고).
     *   details 는 항상 Service 가 toResponse(entity).toBuilder().details(...).build() 로 채운다.
     */
    @Mapping(target = "labOrderItemId", source = "labOrderItem.labOrderItemId")
    @Mapping(target = "labItemCode", source = "labOrderItem.labItemCode")
    @Mapping(target = "details", ignore = true)
    LabResultSummaryDto toResponse(LabResultEntity labResult);

    @Mapping(target = "seq", source = "detailSeq")
    LabResultDetailDto toDetailResponse(LabResultDetailEntity detail);

    List<LabResultDetailDto> toDetailResponseList(List<LabResultDetailEntity> details);
}
