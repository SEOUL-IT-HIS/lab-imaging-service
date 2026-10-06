package kr.co.seoulit.his.labimagingservice.laborder.dto;

/**
 * 검사오더 취소 처리에서 항목 1건에 내려지는 판정. (05번 지시서 Phase 2-C)
 * 판정 기준표(LabOrderCancelService.decide)와 1:1로 대응한다.
 */
public enum ItemCancelResult {

    /** 처방의 cancelledItems 에 있는 itemCode가 우리 오더에 없다. 배치 전체를 실패시키지 않고 건너뛴다. */
    NOT_FOUND,

    /** 이미 취소된 항목이다. (멱등) */
    ALREADY,

    /** 이미 결과(일반/미생물/병리)가 등록되어 취소를 거절한다. */
    REFUSED_DONE,

    /** 검체가 이미 등록되어 취소를 거절한다. (검체는 항목에 연결되지 않아 접수 전체 항목을 보수적으로 막는다) */
    REFUSED_PROG,

    /** 취소되었다. */
    CANCELLED
}
