package kr.co.seoulit.his.labimagingservice.interfacelog.send.repository;

import kr.co.seoulit.his.labimagingservice.interfacelog.send.entity.InterfaceSendLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface InterfaceSendLogRepository extends JpaRepository<InterfaceSendLogEntity, String> {

    /** 같은 원본을 같은 유형으로 이미 기록했는가 (UX_ISLG_TYPE_REF 에 걸리기 전에 확인 — D10/D11) */
    boolean existsByEventTypeCodeAndReferenceId(String eventTypeCode, String referenceId);

    /**
     * 재처리 대상 — 실패(03)이거나, 대기(01)인데 오래된 것. 재시도 횟수가 최대 미만인 것만.
     * ⚠ "오래된 01"을 포함하는 이유: 커밋 직후·발행 전에 서버가 죽으면 행은 01 로 남고 발행은 안 됐다.
     *   AFTER_COMMIT 발행만으로는 이 틈을 못 메운다(D8 함정 5).
     * ⚠ 방금 만든 01 은 제외한다(staleBefore) — 정상 발행이 진행 중인 행을 스케줄러가 또 보내지 않게.
     */
    @Query("""
            select l from InterfaceSendLogEntity l
            where (l.sendStatusCode = '03' or (l.sendStatusCode = '01' and l.sentAt < :staleBefore))
              and l.retryCount < :maxRetry
            order by l.sentAt asc
            """)
    List<InterfaceSendLogEntity> findRetryTargets(@Param("staleBefore") LocalDateTime staleBefore,
                                                   @Param("maxRetry") int maxRetry,
                                                   Pageable pageable);

    /**
     * 조회 화면 (ZP2-120). 조건은 모두 선택이다(null 이면 그 조건을 보지 않는다).
     * ⚠ payload(CLOB)는 목록에서 쓰지 않지만 엔티티째 읽는다. 행 수가 페이지 크기로 제한되고,
     *   CLOB 은 Oracle 이 지연 로딩(LOB locator)하므로 목록 성능에 큰 영향이 없다.
     */
    @Query("""
            select l from InterfaceSendLogEntity l
            where (:eventTypeCode is null or l.eventTypeCode = :eventTypeCode)
              and (:sendStatusCode is null or l.sendStatusCode = :sendStatusCode)
              and (:from is null or l.createdAt >= :from)
              and (:to is null or l.createdAt < :to)
            """)
    Page<InterfaceSendLogEntity> search(@Param("eventTypeCode") String eventTypeCode,
                                        @Param("sendStatusCode") String sendStatusCode,
                                        @Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to,
                                        Pageable pageable);
}
