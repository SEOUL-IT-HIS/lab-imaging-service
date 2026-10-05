package kr.co.seoulit.his.labimagingservice.common.validation;

import java.util.regex.Pattern;

/**
 * 식별자 형식 검증. (04번 지시서 Phase 3-C, 2026-10-05)
 *
 * ⚠ 존재 확인이 아니라 형식 확인이다. documentTemplateId 는 admin-service DOCUMENT_TEMPLATE 의
 *   논리 참조인데, admin 에 아직 조회 API 가 없어(지시서 §3-C) 존재 여부는 확인할 수 없다.
 *   그래도 형식이 UUID 가 아닌 값은 명백한 오입력이라 이 수준에서라도 막는다.
 */
public final class InputFormatValidator {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private InputFormatValidator() {
    }

    /** 문자열이 UUID 형식(8-4-4-4-12, 하이픈 포함 36자)인지. */
    public static boolean isUuid(String value) {
        return value != null && UUID_PATTERN.matcher(value).matches();
    }
}
