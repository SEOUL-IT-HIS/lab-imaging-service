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

    /** 동의 대기 — 일정은 잡혔는데 유효한 동의가 없다. */
    CONSENT,

    /**
     * 촬영 대기 — 동의까지 끝났다.
     *
     * ⚠ 지금은 사실상 마지막 단계다. 촬영(IMAGE_FILE) 등록 기능이 없어 이 상태에서 더 나아가지 않는다.
     *   ZP2-21 이 붙으면 파일 등록 여부를 보고 READING 으로 넘어간다.
     */
    ACQUISITION,

    /**
     * 판독 대기 — 촬영까지 끝났다.
     *
     * ⚠ 아직 계산되지 않는다. 값만 선언해 둔다. (2026-09-02 결정)
     *   IMAGE_READING 테이블은 생겼지만 판독 화면 설계가 없어 엔티티를 만들지 않았다.
     *   ZP2-23 착수 시 ImageWorklistService.decideNextStep 에 조건을 추가한다.
     */
    READING
}
