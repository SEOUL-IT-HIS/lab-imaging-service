package kr.co.seoulit.his.labimagingservice.labschedule.mapper;

import kr.co.seoulit.his.labimagingservice.labschedule.dto.LabScheduleResponseDto;
import kr.co.seoulit.his.labimagingservice.labschedule.entity.LabScheduleEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LabScheduleMapper {
    @Mapping(target = "labReceptionId", source = "labReception.labReceptionId")
    LabScheduleResponseDto toResponse(LabScheduleEntity saved);
}
