package kr.co.seoulit.his.labimagingservice.labspecimen.mapper;


import kr.co.seoulit.his.labimagingservice.labspecimen.dto.SpecimenSummaryDto;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.FitnessStatus;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 검체 Entity → 응답 DTO 매핑.
 */
@Mapper(componentModel = "spring")
public interface SpecimenMapper {

    /**
     * 검체 + 판정 결과 매핑.
     *
     * ⚠ fitnessStatus 를 엔티티에서 바로 꺼내지 않고 Service 가 넘긴다.
     *   specimen.specimenAcceptance 를 매퍼가 건드리면 행마다 지연로딩이 일어난다(N+1).
     *   Service 가 IN 절로 한 번에 조회해 값만 전달한다.
     *   (LabOrderMapper 의 scheduledAt 과 같은 구조)
     *
     * ⚠ 접수는 UUID 가 아니라 접수번호로 내려준다. labReception 은 조회 쿼리에서
     *   join fetch 로 이미 로딩돼 있어 추가 쿼리가 나가지 않는다.
     */
    @Mapping(target = "receptionNo", source = "specimen.labReception.receptionNo")
    @Mapping(target = "specimenType", source = "specimen.specimenTypeCode")
    @Mapping(target = "fitnessStatus", source = "fitnessStatus")
    SpecimenSummaryDto toResponse(SpecimenEntity specimen, FitnessStatus fitnessStatus);
}
