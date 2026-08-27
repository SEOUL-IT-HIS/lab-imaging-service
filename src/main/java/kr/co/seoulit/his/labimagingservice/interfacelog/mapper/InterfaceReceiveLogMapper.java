package kr.co.seoulit.his.labimagingservice.interfacelog.mapper;

import kr.co.seoulit.his.labimagingservice.interfacelog.dto.InterfaceReceiveLogSummaryDto;
import kr.co.seoulit.his.labimagingservice.interfacelog.entity.InterfaceReceiveLogEntity;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 연계 수신 로그 Entity -> 응답 DTO 매핑.
 *
 * 필드명이 모두 같아 명시적 @Mapping 이 필요 없다.
 * (이름이 다른 필드가 생기면 그때 @Mapping 을 붙인다)
 */
@Mapper(componentModel = "spring")
public interface InterfaceReceiveLogMapper {

    InterfaceReceiveLogSummaryDto toResponse(InterfaceReceiveLogEntity log);

    List<InterfaceReceiveLogSummaryDto> toResponseList(List<InterfaceReceiveLogEntity> logs);
}
