package kr.co.seoulit.his.labimagingservice.labspecimen.service;

/**
 * "이 접수는 결과를 등록해도 되는 상태인가" 판정 규칙. (UC-RST-01, 후속조치 #10)
 *
 * ⚠ 이 규칙을 쓰는 곳이 두 군데다 — 한쪽만 고치면 안 된다.
 *   - LabWorklistService.decideNextStep : 워크리스트에 "다음은 결과 등록"(RESULT)을 표시할지
 *   - LabResultService.createLabResult   : 결과 등록 요청을 서버에서 받아줄지 (LAB066)
 *   예전에는 워크리스트만 이 조건을 보고 결과 등록 API 는 보지 않아서, 판정 전 검체로도 결과가
 *   등록됐다. 두 곳이 각자 조건을 적으면 언젠가 어긋나므로 계산을 여기 한 곳에 둔다.
 *
 * ⚠ DB 를 조회하지 않는 순수 계산만 둔다. 워크리스트는 여러 접수의 검체·판정을 IN 절로 한 번에
 *   가져와 메모리에서 계산하고(N+1 방지), 결과 등록은 접수 하나만 본다. 조회 방식이 달라서
 *   "숫자를 넘겨받아 판단"하는 형태로 맞췄다.
 *
 * ⚠ "검체가 전부 부적합인데 재채취 요청도 없는" 접수는 이 규칙상 결과 등록 가능으로 본다.
 *   워크리스트가 원래 그렇게 판단해 왔고, 이번 수정은 그 기준을 서버 검증에 옮기는 것까지만 한다.
 *   (부적합 검체만으로 결과를 받아도 되는지는 업무 규칙 확인 대상 — 작업보고서 질문 목록)
 */
public final class SpecimenReadiness {

    private SpecimenReadiness() {
    }

    /**
     * 재채취 요청이 아직 해소되지 않았는가.
     *
     * 재채취를 요청했다는 기록은 판정 이력에 그대로 남으므로, 요청 사실만으로는 다시 채취해야 하는지
     * 알 수 없다. 이미 다시 채취했을 수도 있다. 그래서 "검체 수가 재채취 요청 수보다 많으면 이미 다시
     * 받은 것"으로 본다.
     *
     *   검체 1건 → 부적합·재채취요청 1건            : 1 <= 1  → 아직 재채취 안 함
     *   재채취해서 검체 2건, 재채취요청은 그대로 1건 : 2 <= 1 아님 → 해소됨
     *
     * ⚠ recollectionCount > 0 조건을 빠뜨리면 안 된다. 검체도 판정도 없는 접수(0건)가
     *   0 <= 0 으로 성립해, 재채취를 요청한 적도 없는데 "재채취" 표시가 붙는다.
     */
    public static boolean isRecollectionPending(int specimenCount, long recollectionCount) {
        return recollectionCount > 0 && specimenCount <= recollectionCount;
    }

    /**
     * 결과를 등록해도 되는가. = 검체가 1건 이상 있고, 전부 판정됐고, 미해소 재채취 요청이 없다.
     *
     * (일정 등록 여부는 보지 않는다. 일정이 없으면 검체도 없어야 정상이고, 검체가 있는데 일정이
     *  없는 경우는 워크리스트가 SCHEDULE 로 먼저 걸러 표시한다. 결과 등록을 막을 이유는 아니다.)
     */
    public static boolean isReadyForResult(int specimenCount, int judgedCount, long recollectionCount) {
        return specimenCount > 0
                && judgedCount >= specimenCount
                && !isRecollectionPending(specimenCount, recollectionCount);
    }
}
