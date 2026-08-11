package kr.co.seoulit.his.labimagingservice.imagingacquisition.mapper;

import org.mapstruct.Mapper;

/**
 * 동의서 Entity → 응답 DTO 매핑.
 *
 * TODO: 매핑 메서드 추가 (ImageScheduleMapper 참고)
 *   ConsentSummaryDto toResponse(ConsentEntity saved);
 *   @Mapping(target = "imageOrderId", source = "imageOrder.imageOrderId")
 */
@Mapper(componentModel = "spring")
public interface ConsentMapper {
}
