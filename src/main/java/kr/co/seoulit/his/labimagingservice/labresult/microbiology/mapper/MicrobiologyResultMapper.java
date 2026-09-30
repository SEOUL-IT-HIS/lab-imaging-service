package kr.co.seoulit.his.labimagingservice.labresult.microbiology.mapper;

import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.labresult.microbiology.dto.MicrobiologyResultSummaryDto;
import kr.co.seoulit.his.labimagingservice.labresult.microbiology.dto.MicrobiologySusceptibilityDto;
import kr.co.seoulit.his.labimagingservice.labresult.microbiology.entity.MicrobiologyResultEntity;
import org.springframework.stereotype.Component;

/**
 * 미생물 결과 → 응답 DTO.
 *
 * ⚠ 다른 도메인처럼 MapStruct 인터페이스로 만들지 않고 직접 매핑했다.
 *   응답의 labOrderItemId/labItemCode 가 엔티티에 없는 값(서비스가 찾아 넘기는 항목)이고,
 *   감수성 목록까지 섞이면 @Mapping 표현식이 오히려 읽기 어려워진다. 필드가 적어 손 매핑이 더 명확하다.
 */
@Component
public class MicrobiologyResultMapper {

    /**
     * @param item 이 결과가 대응하는 검사항목 (찾지 못하면 null — 응답의 항목 필드만 비워 둔다)
     */
    public MicrobiologyResultSummaryDto toResponse(MicrobiologyResultEntity result, LabOrderItemEntity item) {
        return MicrobiologyResultSummaryDto.builder()
                .microbiologyResultId(result.getMicrobiologyResultId())
                .specimenId(result.getSpecimen().getSpecimenId())
                .specimenBarcode(result.getSpecimen().getSpecimenBarcode())
                .receptionNo(result.getSpecimen().getLabReception().getReceptionNo())
                .labOrderItemId(item == null ? null : item.getLabOrderItemId())
                .labItemCode(item == null ? null : item.getLabItemCode())
                .cultureStatusCode(result.getCultureStatusCode())
                .organismCode(result.getOrganismCode())
                .causativeYn(result.getCausativeYn())
                .observationNote(result.getObservationNote())
                .resultStatusCode(result.getResultStatusCode())
                .recordedAt(result.getRecordedAt())
                .recordedById(result.getRecordedById())
                .confirmedAt(result.getConfirmedAt())
                .confirmedById(result.getConfirmedById())
                .updatedAt(result.getUpdatedAt())
                .susceptibilities(result.getSusceptibilities().stream()
                        .map(s -> new MicrobiologySusceptibilityDto(
                                s.getAntibioticCode(), s.getSusceptibilityResultCode()))
                        .toList())
                .build();
    }
}
