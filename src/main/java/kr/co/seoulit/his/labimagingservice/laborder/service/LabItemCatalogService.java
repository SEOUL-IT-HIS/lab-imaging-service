package kr.co.seoulit.his.labimagingservice.laborder.service;

import kr.co.seoulit.his.labimagingservice.businessdelegate.admin.AdminCommonCodeBusinessDelegate;
import kr.co.seoulit.his.labimagingservice.businessdelegate.admin.dto.CommonCodeItemResponse;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabItemCatalogDto;
import kr.co.seoulit.his.labimagingservice.labresult.type.LabResultTypeResolver;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.LabTestSpecimenRuleEntity;
import kr.co.seoulit.his.labimagingservice.labspecimen.repository.LabTestSpecimenRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 검사항목 카탈로그 검색. 처방 화면의 "검사 항목 고르기" 용도. (처방코어 요청, 2026-10-02)
 *
 * ⚠ 요청마다 admin 에 직접 조회한다(캐시하지 않는다). CommonCodeCache 와는 목적이 다르다 —
 *   그쪽은 "값이 유효한가"만 10분 주기로 보면 되지만, 여기는 "지금 등록된 이름"을 그대로
 *   보여줘야 하는 화면 검색이라 매번 최신 값이어야 한다. 카탈로그가 8건 수준으로 작고
 *   호출 빈도도 낮아(처방 화면에서 검사 고를 때만) 매 요청 admin 호출의 비용이 작다.
 *
 * ⚠ admin 조회가 실패하면 예외를 그대로 전파한다(fail-closed, GlobalExceptionHandler 가 LAB999 로
 *   응답). 빈 목록으로 조용히 넘기면 "검사 항목이 하나도 없다"로 보여 더 혼란스럽다.
 */
@Service
@RequiredArgsConstructor
public class LabItemCatalogService {

    private static final String TEST_TYPE_CD = "TEST_TYPE_CD";
    private static final String USE_Y = "Y";

    private final AdminCommonCodeBusinessDelegate adminCommonCodeBusinessDelegate;
    private final LabResultTypeResolver labResultTypeResolver;
    private final LabTestSpecimenRuleRepository labTestSpecimenRuleRepository;

    /**
     * 검사항목을 코드/이름으로 검색한다.
     *
     * @param name 검색어(부분일치, 대소문자 무관). 비어 있으면 전체 목록을 돌려준다.
     */
    public List<LabItemCatalogDto> search(String name) {
        List<CommonCodeItemResponse> items = adminCommonCodeBusinessDelegate.getUsableCodeItems(TEST_TYPE_CD);
        if (items.isEmpty()) {
            return List.of();
        }

        List<String> itemCodes = items.stream().map(CommonCodeItemResponse::getCodeValue).toList();
        Map<String, List<String>> specimenTypesByItemCode = resolveSpecimenTypes(itemCodes);

        String query = (name == null) ? "" : name.trim().toLowerCase();

        return items.stream()
                .filter(item -> matches(item, query))
                .map(item -> toDto(item, specimenTypesByItemCode))
                .toList();
    }

    private boolean matches(CommonCodeItemResponse item, String query) {
        if (query.isEmpty()) {
            return true;
        }
        String code = item.getCodeValue() == null ? "" : item.getCodeValue().toLowerCase();
        String name = item.getCodeName() == null ? "" : item.getCodeName().toLowerCase();
        return code.contains(query) || name.contains(query);
    }

    private LabItemCatalogDto toDto(CommonCodeItemResponse item, Map<String, List<String>> specimenTypesByItemCode) {
        return LabItemCatalogDto.builder()
                .itemCode(item.getCodeValue())
                .itemName(item.getCodeName())
                .testClassification(labResultTypeResolver.resolve(item.getCodeValue()).name())
                .specimenTypes(specimenTypesByItemCode.getOrDefault(item.getCodeValue(), List.of()))
                .build();
    }

    /**
     * 검사항목코드별 허용 검체종류(중복 제거, 등록 순서 유지)를 한 번에 조회한다.
     * ⚠ 규칙이 없는 검사는 이 맵에 키 자체가 없다 — 호출한 쪽이 빈 배열로 받는다(getOrDefault).
     */
    private Map<String, List<String>> resolveSpecimenTypes(List<String> itemCodes) {
        return labTestSpecimenRuleRepository.findByTestTypeCodeInAndUseYn(itemCodes, USE_Y).stream()
                .collect(Collectors.groupingBy(
                        LabTestSpecimenRuleEntity::getTestTypeCode,
                        Collectors.mapping(
                                LabTestSpecimenRuleEntity::getSpecimenTypeCode,
                                Collectors.collectingAndThen(
                                        Collectors.toCollection(LinkedHashSet<String>::new),
                                        (Set<String> set) -> List.copyOf(set)))));
    }
}
