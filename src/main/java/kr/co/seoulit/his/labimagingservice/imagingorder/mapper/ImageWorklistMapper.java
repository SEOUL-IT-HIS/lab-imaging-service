package kr.co.seoulit.his.labimagingservice.imagingorder.mapper;

import kr.co.seoulit.his.labimagingservice.imagingorder.dto.ImageWorklistItemDto;
import kr.co.seoulit.his.labimagingservice.imagingorder.dto.ImageWorklistStep;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageReceptionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;

/**
 * 영상 워크리스트 1행 매핑.
 *
 * ⚠ 진행 상태 값들을 엔티티에서 직접 꺼내지 않고 Service 가 넘긴다.
 *   reception.getSchedules() 나 오더의 동의를 매퍼가 건드리면 행마다 지연로딩이 일어난다(N+1).
 *   Service 가 IN 절로 한 번에 조회해 계산한 값만 전달한다.
 *   (LabWorklistMapper 와 같은 구조)
 *
 * ⚠ source 파라미터가 여러 개라 모든 대상 필드에 source 를 명시한다.
 *   생략하면 MapStruct 가 어느 파라미터에서 가져올지 몰라 컴파일 에러가 난다.
 */
@Mapper(componentModel = "spring")
public interface ImageWorklistMapper {

    @Mapping(target = "imageReceptionId", source = "reception.imageReceptionId")
    @Mapping(target = "receptionNo", source = "reception.receptionNo")
    @Mapping(target = "imageOrderNo", source = "reception.imageOrder.imageOrderNo")
    @Mapping(target = "imageOrderId", source = "reception.imageOrder.imageOrderId")
    @Mapping(target = "patientId", source = "reception.patientId")
    @Mapping(target = "urgencyYn", source = "reception.urgencyYn")
    // ⚠ 접수일시는 별도 컬럼이 아니라 생성일시다. (검사 쪽과 동일)
    @Mapping(target = "receivedAt", source = "reception.createdAt")
    @Mapping(target = "receptionStatusCode", source = "reception.receptionStatusCode")
    @Mapping(target = "exclusionReason", source = "reception.exclusionReason")
    @Mapping(target = "excludedAt", source = "reception.excludedAt")
    @Mapping(target = "scheduledAt", source = "scheduledAt")
    @Mapping(target = "consentYn", source = "consentYn")
    @Mapping(target = "imageFileCount", source = "imageFileCount")
    @Mapping(target = "nextStep", source = "nextStep")
    ImageWorklistItemDto toWorklistItem(ImageReceptionEntity reception,
                                        LocalDateTime scheduledAt,
                                        String consentYn,
                                        int imageFileCount,
                                        ImageWorklistStep nextStep);
}
