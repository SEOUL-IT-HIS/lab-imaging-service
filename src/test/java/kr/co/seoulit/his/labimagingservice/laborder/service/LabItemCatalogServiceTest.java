package kr.co.seoulit.his.labimagingservice.laborder.service;

import kr.co.seoulit.his.labimagingservice.businessdelegate.admin.AdminCommonCodeBusinessDelegate;
import kr.co.seoulit.his.labimagingservice.businessdelegate.admin.dto.CommonCodeItemResponse;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabItemCatalogDto;
import kr.co.seoulit.his.labimagingservice.labresult.type.LabResultType;
import kr.co.seoulit.his.labimagingservice.labresult.type.LabResultTypeResolver;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.LabTestSpecimenRuleEntity;
import kr.co.seoulit.his.labimagingservice.labspecimen.repository.LabTestSpecimenRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 검사항목 카탈로그 검색. (처방코어 요청, 2026-10-02)
 */
class LabItemCatalogServiceTest {

    private final AdminCommonCodeBusinessDelegate adminCommonCodeBusinessDelegate =
            mock(AdminCommonCodeBusinessDelegate.class);
    private final LabTestSpecimenRuleRepository labTestSpecimenRuleRepository =
            mock(LabTestSpecimenRuleRepository.class);

    // 05=MICROBIOLOGY, 07=PATHOLOGY. 나머지는 기본값(GENERAL) — LabResultTypeResolver 와 같은 설정.
    private final LabResultTypeResolver labResultTypeResolver =
            new LabResultTypeResolver(Map.of("05", LabResultType.MICROBIOLOGY, "07", LabResultType.PATHOLOGY));

    private final LabItemCatalogService service = new LabItemCatalogService(
            adminCommonCodeBusinessDelegate, labResultTypeResolver, labTestSpecimenRuleRepository);

    private static CommonCodeItemResponse item(String value, String name) {
        CommonCodeItemResponse item = new CommonCodeItemResponse();
        item.setCodeValue(value);
        item.setCodeName(name);
        item.setUseYn("Y");
        return item;
    }

    private static LabTestSpecimenRuleEntity rule(String testTypeCode, String specimenTypeCode) {
        LabTestSpecimenRuleEntity rule = mock(LabTestSpecimenRuleEntity.class);
        when(rule.getTestTypeCode()).thenReturn(testTypeCode);
        when(rule.getSpecimenTypeCode()).thenReturn(specimenTypeCode);
        return rule;
    }

    @BeforeEach
    void setUp() {
        List<CommonCodeItemResponse> items = List.of(
                item("01", "Blood Glucose Test"),
                item("02", "CBC"),
                item("05", "Blood Culture"));
        when(adminCommonCodeBusinessDelegate.getUsableCodeItems(eq("TEST_TYPE_CD"))).thenReturn(items);

        List<LabTestSpecimenRuleEntity> rules = List.of(
                rule("01", "BLOOD"),
                rule("02", "BLOOD"),
                rule("05", "BLOOD"));
        when(labTestSpecimenRuleRepository.findByTestTypeCodeInAndUseYn(anyList(), anyString()))
                .thenReturn(rules);
    }

    @Test
    @DisplayName("검색어 없음 — admin에 등록된 전체 목록을 돌려준다")
    void searchWithoutQueryReturnsAll() {
        List<LabItemCatalogDto> result = service.search(null);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(LabItemCatalogDto::getItemCode)
                .containsExactly("01", "02", "05");
    }

    @Test
    @DisplayName("이름으로 부분일치 검색 (대소문자 무관)")
    void searchByNameCaseInsensitive() {
        List<LabItemCatalogDto> result = service.search("cbc");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getItemCode()).isEqualTo("02");
        assertThat(result.get(0).getItemName()).isEqualTo("CBC");
    }

    @Test
    @DisplayName("코드로도 검색된다")
    void searchByCode() {
        List<LabItemCatalogDto> result = service.search("05");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getItemCode()).isEqualTo("05");
    }

    @Test
    @DisplayName("일치 결과 없음 — 빈 목록")
    void searchNoMatch() {
        assertThat(service.search("no-such-item")).isEmpty();
    }

    @Test
    @DisplayName("검사 분류(testClassification)는 LabResultTypeResolver 기준으로 채워진다")
    void testClassificationResolved() {
        List<LabItemCatalogDto> result = service.search(null);

        assertThat(result).filteredOn(i -> i.getItemCode().equals("01"))
                .extracting(LabItemCatalogDto::getTestClassification).containsExactly("GENERAL");
        assertThat(result).filteredOn(i -> i.getItemCode().equals("05"))
                .extracting(LabItemCatalogDto::getTestClassification).containsExactly("MICROBIOLOGY");
    }

    @Test
    @DisplayName("허용 검체종류가 채워지고, 규칙 없는 검사는 빈 배열이다")
    void specimenTypesResolved() {
        List<CommonCodeItemResponse> items = List.of(item("01", "Blood Glucose Test"), item("99", "No Rule Item"));
        when(adminCommonCodeBusinessDelegate.getUsableCodeItems(eq("TEST_TYPE_CD"))).thenReturn(items);
        LabTestSpecimenRuleEntity bloodRule = rule("01", "BLOOD");
        when(labTestSpecimenRuleRepository.findByTestTypeCodeInAndUseYn(anyList(), anyString()))
                .thenReturn(List.of(bloodRule));

        List<LabItemCatalogDto> result = service.search(null);

        assertThat(result).filteredOn(i -> i.getItemCode().equals("01"))
                .extracting(LabItemCatalogDto::getSpecimenTypes)
                .containsExactly(List.of("BLOOD"));
        assertThat(result).filteredOn(i -> i.getItemCode().equals("99"))
                .extracting(LabItemCatalogDto::getSpecimenTypes)
                .containsExactly(List.of());
    }

    @Test
    @DisplayName("admin 쪽에 등록된 코드가 하나도 없으면 빈 목록 — admin 재조회(resolveSpecimenTypes) 호출 안 함")
    void emptyCatalogReturnsEmptyList() {
        when(adminCommonCodeBusinessDelegate.getUsableCodeItems(eq("TEST_TYPE_CD"))).thenReturn(List.of());

        assertThat(service.search(null)).isEmpty();
    }
}
