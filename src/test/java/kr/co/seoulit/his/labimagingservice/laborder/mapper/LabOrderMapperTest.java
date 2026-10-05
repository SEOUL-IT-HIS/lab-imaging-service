package kr.co.seoulit.his.labimagingservice.laborder.mapper;

import kr.co.seoulit.his.labimagingservice.laborder.dto.LabReceptionDetailDto;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabReceptionEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 처방의사명 표시(마무리_최종현황 04번 지시서 Phase 1-B) — 상세 응답에 physicianId 가 실리는지 확인한다.
 */
class LabOrderMapperTest {

    private final LabOrderMapper mapper = new LabOrderMapperImpl();

    @Test
    @DisplayName("toDetailResponse: order.physicianId 가 응답의 physicianId 로 그대로 실린다")
    void toDetailResponseCarriesPhysicianId() {
        LabOrderEntity order = mock(LabOrderEntity.class);
        when(order.getPhysicianId()).thenReturn("doc-1");
        when(order.getPhysicianNo()).thenReturn(null);
        LabReceptionEntity reception = mock(LabReceptionEntity.class);

        LabReceptionDetailDto dto = mapper.toDetailResponse(order, reception, null);

        assertThat(dto.getPhysicianId()).isEqualTo("doc-1");
        assertThat(dto.getPhysicianNo()).isNull();
    }
}
