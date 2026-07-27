package kr.co.seoulit.his.labimagingservice.imagingschedule.mapper;

import kr.co.seoulit.his.labimagingservice.imagingschedule.dto.ImageScheduleResponseDto;
import kr.co.seoulit.his.labimagingservice.imagingschedule.entity.ImageScheduleEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ImageScheduleMapper {
    @Mapping(target = "imageReceptionId", source = "imageReception.imageReceptionId")
    ImageScheduleResponseDto toResponse(ImageScheduleEntity saved);
}
