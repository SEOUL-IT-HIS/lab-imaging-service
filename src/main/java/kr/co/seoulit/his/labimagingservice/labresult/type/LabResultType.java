package kr.co.seoulit.his.labimagingservice.labresult.type;

/**
 * 검사항목의 결과 유형. (5차 D1)
 *
 * ⚠ DB 에 저장하지 않는다. 검사항목코드(TEST_TYPE_CD)로 매번 판별한다(LabResultTypeResolver).
 *   결과가 어느 테이블에 들어가는지가 유형마다 다르다.
 *     GENERAL      → LAB_RESULT            (검사항목 1건 = 결과 1건)
 *     MICROBIOLOGY → MICROBIOLOGY_RESULT   (검체 단위 저장, 접수당 미생물 항목 1개 제약으로 항목과 1:1)
 *     PATHOLOGY    → PATHOLOGY_RESULT      (검사항목 1건 = 결과 1건)
 */
public enum LabResultType {
    GENERAL,
    MICROBIOLOGY,
    PATHOLOGY
}
