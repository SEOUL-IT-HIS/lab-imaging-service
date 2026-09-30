package kr.co.seoulit.his.labimagingservice.imagingorder.dto;

/**
 * 영상 워크리스트의 "다음에 해야 할 일".
 *
 * ⚠ DB 에 저장되는 값이 아니다. 접수 하나에 대해 일정·동의·촬영 데이터가 어디까지 쌓였는지를 보고
 *   서버가 매번 계산해서 내려주는 화면용 값이다. (그래서 entity 가 아니라 dto 패키지에 있다)
 *
 * ⚠ 이 계산을 프론트에 맡기지 않는 이유 —
 *   "담당자가 바뀌어도 다음에 뭘 해야 할지 목록에서 바로 보인다"가 워크리스트의 목적인데,
 *   판단 규칙이 화면마다 흩어지면 검사 화면과 영상 화면이 서로 다르게 판단하기 시작한다.
 *   규칙은 서버 한 곳에만 둔다. (ImageWorklistService.decideNextStep)
 *
 * ⚠ 검사(WorklistStep)와 단계가 다르다. 합치지 않는다.
 *   검사 : SCHEDULE → SPECIMEN → ACCEPTANCE → RECOLLECT → RESULT
 *   영상 : SCHEDULE → CONSENT  → ACQUISITION → READING
 *   영상에는 검체가 없어 적합성 판정 단계가 성립하지 않고, 대신 조영제·침습검사 동의가
 *   촬영 앞을 막는 단계로 들어간다. 하나의 enum 으로 묶으면 양쪽 모두에 안 쓰는 값이 생긴다.
 */
public enum ImageWorklistStep {

    /** 일정 등록 대기 — 최종 일정이 없다. */
    SCHEDULE,

    /**
     * 동의 대기 — 일정은 잡혔는데 유효한 동의(동의함 + 미철회)가 없다.
     * ⚠ 동의가 필요 없는 오더(ConsentRequirementPolicy, required-mode=LISTED)는 이 단계를 건너뛴다. (5차 Phase 9-1)
     */
    CONSENT,

    /** 촬영 대기 — 동의까지 끝났다(또는 동의가 필요 없다). 영상파일이 올라오면 READING 으로 넘어간다. (ZP2-21) */
    ACQUISITION,

    /**
     * 판독 대기 — 영상파일이 하나라도 있다. (ZP2-23)
     *
     * ⚠ 마지막 단계다. 판독이 확정돼도 여기 머물고 진행도는 readingCompletedCount 칩으로 보여준다.
     * ⚠ 촬영 후 동의가 철회돼도 CONSENT 로 되돌아가지 않는다(D14) — consentWithdrawnYn 배지로 알린다.
     */
    READING
}
