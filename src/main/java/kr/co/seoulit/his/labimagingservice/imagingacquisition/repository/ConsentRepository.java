package kr.co.seoulit.his.labimagingservice.imagingacquisition.repository;

import kr.co.seoulit.his.labimagingservice.imagingacquisition.entity.ConsentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 동의서 리포지토리.
 *
 * ⚠ "현재 유효한 동의"는 withdrawn_yn='N' 인 행이다.
 *   LAB_SCHEDULE 처럼 latest_yn 컬럼을 두는 방식도 검토했으나, 동의서는 일정과 달리
 *   같은 오더에 서로 다른 유형(조영제/침습)이 동시에 유효할 수 있어 "최종 1건" 개념이 맞지 않는다.
 *   그래서 유형별로 철회 여부를 보는 방식을 쓴다.
 */
public interface ConsentRepository extends JpaRepository<ConsentEntity, String> {

    /**
     * 영상오더 1건의 동의 이력 전체. (ZP2-80 검사 진행 전 동의 상태 확인)
     *
     * 철회된 건까지 모두 내려준다. 화면에서 이력을 보여줘야 하고,
     * "왜 다시 동의를 받았는지"는 철회 기록이 함께 보여야 이해되기 때문이다.
     *
     * ── N+1 방어: "join fetch c.imageOrder"
     *   ConsentEntity.imageOrder 는 @ManyToOne(LAZY) 라, 매핑에서 imageOrderId 를 꺼내는 순간
     *   행마다 SELECT 가 추가로 나간다. 응답 DTO 가 imageOrderId 를 포함하므로 반드시 필요하다.
     *   @JoinColumn(nullable = false) 라 inner join fetch 로도 누락 행이 없다.
     */
    @Query("""
            select c from ConsentEntity c
            join fetch c.imageOrder o
            where o.imageOrderId = :imageOrderId
            order by c.createdAt desc
            """)
    List<ConsentEntity> findByImageOrderIdWithOrder(@Param("imageOrderId") String imageOrderId);

    /**
     * 여러 오더의 "철회되지 않은" 동의를 한 번에 조회한다. (워크리스트 진행상태 조립용)
     *
     * ⚠ 오더마다 조회하면 행 수만큼 쿼리가 나간다(N+1). 오더ID 를 통째로 넘겨 IN 절 한 번으로 끝낸다.
     *
     * ⚠ 철회된 건은 제외한다. 위 findByImageOrderIdWithOrder 와 다른 점이다.
     *   그쪽은 이력 화면용이라 철회분까지 보여줘야 하지만, 워크리스트는 "지금 유효한 동의가 있는가"만
     *   판단하면 된다.
     *
     * ⚠ 엔티티가 아니라 오더ID 만 뽑는다. 동의 본문은 워크리스트에서 쓰지 않고,
     *   엔티티로 받으면 imageOrder 지연로딩까지 따라붙는다.
     */
    @Query("""
            select distinct o.imageOrderId from ConsentEntity c
            join c.imageOrder o
            where o.imageOrderId in :imageOrderIds
              and c.withdrawnYn = 'N'
            """)
    List<String> findOrderIdsWithValidConsent(@Param("imageOrderIds") List<String> imageOrderIds);

    /**
     * 같은 오더에 같은 유형의 "철회되지 않은" 동의가 이미 있는지 확인한다. (중복 등록 차단)
     *
     * ⚠ DB에 UNIQUE 제약이 없다. 오더 1건에 재동의 이력이 여러 건 쌓이는 것이 정상이라
     *   제약을 걸 수 없기 때문이다. 그래서 "철회 전 같은 유형"이라는 조건을 코드에서 판단한다.
     */
    boolean existsByImageOrder_ImageOrderIdAndConsentTypeCodeAndWithdrawnYn(
            String imageOrderId, String consentTypeCode, String withdrawnYn);
}
