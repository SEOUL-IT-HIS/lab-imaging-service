package kr.co.seoulit.his.labimagingservice.labresult.service;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * 참고범위와 결과값을 비교해 비정상 여부를 판정한다. (ZP2-99)
 *
 * 6차(2026-09-30)에서 LabResultService.decideAbnormalYn 을 그대로 옮겨 왔다. 결과항목(상세)이
 * 생기면서 이 판정이 필요한 곳이 둘이 됐다(기존 방식 LAB_RESULT 헤더 / 결과항목 LAB_RESULT_DETAIL
 * 각 항목) — 두 곳이 각자 판정 로직을 베끼면 나중에 규칙이 갈라진다. 로직·주석은 옮기기 전과 동일하다.
 *
 * ── 설계: 참고범위는 "정상으로 보는 값"이다
 *   정량("3.5-5.5")과 정성("음성")을 한 컬럼으로 다루기 위해 이렇게 정의했다.
 *   컬럼을 나누면 검사항목마다 어느 쪽을 쓰는지 판단해야 하는데, 그 구분 정보가 아직 없다.
 *
 * ── 판정 규칙
 *   1) 참고범위가 없으면          → N. 비교할 기준이 없다.
 *   2) "min-max" 이고 결과가 숫자 → 범위를 벗어나면 Y.
 *   3) 그 밖(정성값)             → 참고범위에 적힌 값과 다르면 Y.
 *                                  쉼표로 여러 정상값을 줄 수 있다. ("음성,정상")
 *
 * ⚠ 1번을 Y 가 아니라 N 으로 두는 이유 —
 *   기준이 없는 것과 비정상인 것은 다르다. Y 로 두면 참고범위를 안 적은 결과가 전부
 *   비정상으로 쌓여, 정작 진짜 비정상 건이 묻힌다.
 *   대신 판정하지 않았다는 사실이 화면에서 드러나야 한다 — 참고범위 칸이 비어 있는 것이 그 신호다.
 *
 * ⚠ 3번 덕분에 "양성"은 참고범위가 "음성"이면 자동으로 Y 가 된다.
 *   정성 결과를 무조건 N 으로 두면 양성 결과가 정상으로 분류되는데, 그건 위험하다.
 *
 * ⚠ 한계 — "≤5", "5 이하", "3.5~5.5" 같은 표기는 2번으로 인식하지 못해 3번(문자열 비교)으로
 *   내려가고, 그러면 대부분 Y 가 된다. 검사항목별 기준값 마스터가 생기면
 *   이 문자열 파싱 자체가 없어져야 한다. 그때까지의 임시 규칙이다.
 */
public final class AbnormalYnDecider {

    private static final String YES = "Y";
    private static final String NO = "N";

    /**
     * 결과값이 "엄격한 십진수"인지 판별하는 정규식. (LAB105, 04번 지시서 §3-A)
     * 지수(1e3)·접미사(4.2f)·NaN·Infinity·쉼표(4,2)·앞뒤 공백 포함 입력을 모두 거부한다.
     * parseNumber()(decide/decideDirection 전용, Double.valueOf 기반)보다 엄격하다 — 그 둘은
     * NaN/Infinity/지수 표기까지 숫자로 읽어버려 "비정상 아님"으로 잘못 판정될 수 있기 때문이다.
     */
    private static final Pattern STRICT_DECIMAL = Pattern.compile("^[+-]?\\d+(\\.\\d+)?$");

    private AbnormalYnDecider() {
    }

    /**
     * 판정 방향(고/저)까지 포함해 반환한다. 처방코어 결과 이벤트 abnormalFlag 전용(2026-09-30 회신 반영).
     * N=정상 / H=상한 초과 / L=하한 미만 / null=판정 불가(참고범위가 없거나, 정성 비교라 방향을 알 수 없음).
     *
     * ⚠ decide()와 별개 메서드다. decide()는 내부 저장용 이상여부(Y/N, LAB_RESULT.abnormal_yn 등)이고
     *   이건 외부 전송 전용 표현이다 — 내부 컬럼의 의미(decide() 주석 참고)는 이 메서드로 바뀌지 않는다.
     *   정성 판정이 "비정상"으로 나와도 고/저 개념이 없어 null 로 돌려준다(판정 불가와 같은 취급).
     */
    public static String decideDirection(String resultValue, String referenceRange) {
        if (referenceRange == null || referenceRange.isBlank()) {
            return null;
        }

        Double min = parseRangeBound(referenceRange, 0);
        Double max = parseRangeBound(referenceRange, 1);
        Double value = parseNumber(resultValue);

        if (min != null && max != null && value != null) {
            if (value < min) {
                return "L";
            }
            if (value > max) {
                return "H";
            }
            return "N";
        }

        // ⚠ 정성 비교로 내려오기 전에 resultValue 를 확인한다 — 결과값이 없으면(null/blank)
        //   비교할 대상 자체가 없다. 여기서 거르지 않으면 바로 아래 trim() 에서 NPE 가 나고,
        //   호출 지점이 결과 확정 트랜잭션이라(LabResultTransmissionService.transmitGeneral)
        //   확정 자체가 롤백된다(2026-10-01 마무리 점검, T1).
        if (resultValue == null || resultValue.isBlank()) {
            return null;
        }

        boolean normal = Arrays.stream(referenceRange.split(","))
                .map(String::trim)
                .anyMatch(normalValue -> normalValue.equalsIgnoreCase(resultValue.trim()));
        return normal ? "N" : null;
    }

    public static String decide(String resultValue, String referenceRange) {

        if (referenceRange == null || referenceRange.isBlank()) {
            return NO;
        }

        Double min = parseRangeBound(referenceRange, 0);
        Double max = parseRangeBound(referenceRange, 1);
        Double value = parseNumber(resultValue);

        // 2) 정량 판정 — 범위와 결과값이 모두 숫자로 읽힐 때만 성립한다.
        if (min != null && max != null && value != null) {
            return (value < min || value > max) ? YES : NO;
        }

        // 3) 정성 판정 — 참고범위에 적힌 정상값 중 하나와 같으면 정상.
        return Arrays.stream(referenceRange.split(","))
                .map(String::trim)
                .anyMatch(normal -> normal.equalsIgnoreCase(resultValue.trim()))
                ? NO : YES;
    }

    /**
     * 참고범위가 "min-max" 형태로 숫자 두 개로 읽히는지. (LAB105/106, Phase 3-A)
     *
     * ⚠ decide()/decideDirection() 은 숫자로 못 읽으면 조용히 정성 비교로 내려간다(클래스 주석
     *   참고) — 참고범위가 명백히 수치 범위일 때만 결과값도 숫자여야 한다고 호출부가 미리
     *   막을 수 있도록, 그 판단 기준을 그대로 공개한다(parseRangeBound 를 또 만들지 않는다).
     */
    public static boolean isNumericRange(String referenceRange) {
        if (referenceRange == null || referenceRange.isBlank()) {
            return false;
        }
        return parseRangeBound(referenceRange, 0) != null && parseRangeBound(referenceRange, 1) != null;
    }

    /**
     * 문자열이 "엄격한 십진수"로 읽히는지. (LAB105, Phase 3-A)
     *
     * ⚠ decide()/decideDirection() 이 쓰는 parseNumber() 와 다르다 — 그쪽은 결과값 저장을
     *   바꾸지 않기 위해 건드리지 않는다(§3-A: "기존 동작은 바꾸지 않는다"). 이 메서드는 그
     *   느슨한 파싱으로는 숫자로 읽혀 조용히 통과했을 "1e3", "NaN", "Infinity", "4.2f", "4,2"
     *   같은 입력을 전부 거부한다.
     */
    public static boolean isNumeric(String text) {
        return text != null && STRICT_DECIMAL.matcher(text.trim()).matches();
    }

    /**
     * 수치 범위의 하한이 상한보다 큰지 않은지(하한 ≤ 상한). (LAB106, Phase 3-A)
     *
     * ⚠ 하한 == 상한은 허용한다(지시서 §3-A: "하한 ≤ 상한이어야 한다"). 거절 대상은 하한 &gt; 상한뿐이다.
     * ⚠ isNumericRange() 가 false 인 범위에는 이 메서드를 쓰지 않는다 — 호출부(LabResultService)가
     *   먼저 isNumericRange() 로 걸러서 쓰는 전제다. 수치 범위가 아닌데 그대로 부르면
     *   parseRangeBound 가 null 을 돌려줘 NPE 가 난다.
     */
    public static boolean isValidNumericRangeOrder(String referenceRange) {
        Double min = parseRangeBound(referenceRange, 0);
        Double max = parseRangeBound(referenceRange, 1);
        return min <= max;
    }

    /**
     * "3.5-5.5" 에서 index 번째 경계값을 꺼낸다. (0=하한, 1=상한)
     * 형식이 다르거나 숫자로 읽히지 않으면 null 을 돌려주고, 호출한 쪽이 정성 판정으로 넘어간다.
     *
     * ⚠ 음수 범위("-5--1")는 이 분리 방식으로 다룰 수 없다. 일반검사 수치에 음수가 없어 두고 간다.
     */
    private static Double parseRangeBound(String referenceRange, int index) {
        String[] bounds = referenceRange.split("-");
        if (bounds.length != 2) {
            return null;
        }
        return parseNumber(bounds[index]);
    }

    /** 숫자로 읽히면 값을, 아니면 null. 정성값("음성")이 그대로 들어오므로 예외로 다루지 않는다. */
    private static Double parseNumber(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
