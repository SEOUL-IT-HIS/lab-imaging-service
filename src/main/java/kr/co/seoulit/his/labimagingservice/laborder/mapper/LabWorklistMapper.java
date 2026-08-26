package kr.co.seoulit.his.labimagingservice.laborder.mapper;

import kr.co.seoulit.his.labimagingservice.laborder.dto.LabWorklistItemDto;
import kr.co.seoulit.his.labimagingservice.laborder.dto.WorklistStep;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabReceptionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;

/**
 * 검사 워크리스트 1행 매핑.
 *
 * ⚠ 진행 상태 값들을 엔티티에서 직접 꺼내지 않고 Service 가 넘긴다.
 *   reception.getSpecimens() 를 매퍼가 건드리면 행마다 지연로딩이 일어난다(N+1).
 *   Service 가 IN 절로 한 번에 조회해 계산한 값만 전달한다.
 *   (LabOrderMapper 의 scheduledAt, SpecimenMapper 의 fitnessStatus 와 같은 구조)
 *
 * ⚠ source 파라미터가 여러 개라 모든 대상 필드에 source 를 명시한다.
 *   생략하면 MapStruct 가 어느 파라미터에서 가져올지 몰라 컴파일 에러가 난다.
 */
@Mapper(componentModel = "spring")
public interface LabWorklistMapper {

    @Mapping(target = "labReceptionId", source = "reception.labReceptionId")
    @Mapping(target = "receptionNo", source = "reception.receptionNo")
    @Mapping(target = "labOrderNo", source = "reception.labOrder.labOrderNo")
    @Mapping(target = "patientId", source = "reception.patientId")
    @Mapping(target = "urgencyYn", source = "reception.urgencyYn")
    @Mapping(target = "receivedAt", source = "reception.createdAt")
    @Mapping(target = "receptionStatusCode", source = "reception.receptionStatusCode")
    @Mapping(target = "exclusionReason", source = "reception.exclusionReason")
    @Mapping(target = "excludedAt", source = "reception.excludedAt")
    @Mapping(target = "scheduledAt", source = "scheduledAt")
    @Mapping(target = "specimenCount", source = "specimenCount")
    @Mapping(target = "judgedCount", source = "judgedCount")
    @Mapping(target = "recollectionRequestedYn", source = "recollectionRequestedYn")
    @Mapping(target = "nextStep", source = "nextStep")
    LabWorklistItemDto toWorklistItem(LabReceptionEntity reception,
                                      LocalDateTime scheduledAt,
                                      int specimenCount,
                                      int judgedCount,
                                      String recollectionRequestedYn,
                                      WorklistStep nextStep);
}
