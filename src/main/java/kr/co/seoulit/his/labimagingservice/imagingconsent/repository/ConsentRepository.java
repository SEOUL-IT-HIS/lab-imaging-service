package kr.co.seoulit.his.labimagingservice.imagingconsent.repository;

import kr.co.seoulit.his.labimagingservice.imagingconsent.dto.ConsentFlagDto;
import kr.co.seoulit.his.labimagingservice.imagingconsent.entity.ConsentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 동의서 리포지토리.
 *
 * ⚠ "현재 유효한 동의"는 consent_yn='Y' 이고 withdrawn_yn='N' 인 행이다. (2026-09-29 정정, D13)
 *   이전에는 withdrawn_yn='N' 만 봐서, 환자가 "거부"(consent_yn='N')한 기록도 유효한 동의로
 *   판정돼 촬영·업로드가 통과했다(후속조치 #1). 거부는 철회되지 않은 기록이지만 동의가 아니다.
 *   LAB_SCHEDULE 처럼 latest_yn 컬럼을 두는 방식도 검토했으나, 동의서는 일정과 달리
 *   같은 오더에 서로 다른 유형(조영제/침습)이 동시에 유효할 수 있어 "최종 1건" 개념이 맞지 않는다.
 *   그래서 유형별로 동의·철회 여부를 보는 방식을 쓴다.
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
     * 여러 오더의 "유효한" 동의를 한 번에 조회한다. (워크리스트 진행상태 조립 + 촬영 업로드 LAB052 검사)
     *
     * ⚠ 오더마다 조회하면 행 수만큼 쿼리가 나간다(N+1). 오더ID 를 통째로 넘겨 IN 절 한 번으로 끝낸다.
     *
     * ⚠ 유효 = 동의함(consent_yn='Y') + 철회 안 됨(withdrawn_yn='N'). 둘 중 하나라도 빠지면 안 된다.
     *   consent_yn 조건이 없던 시절에는 "거부" 기록만 있는 오더가 동의 완료로 보였다. (후속조치 #1)
     *   위 findByImageOrderIdWithOrder 는 이력 화면용이라 거부·철회분까지 보여주는 것과 다르다.
     *
     * ⚠ 엔티티가 아니라 오더ID 만 뽑는다. 동의 본문은 워크리스트에서 쓰지 않고,
     *   엔티티로 받으면 imageOrder 지연로딩까지 따라붙는다.
     */
    @Query("""
            select distinct o.imageOrderId from ConsentEntity c
            join c.imageOrder o
            where o.imageOrderId in :imageOrderIds
              and c.consentYn = 'Y'
              and c.withdrawnYn = 'N'
            """)
    List<String> findOrderIdsWithValidConsent(@Param("imageOrderIds") List<String> imageOrderIds);

    /**
     * 여러 오더의 동의 상태 조각(동의여부·철회여부)을 한 번에 조회한다. (워크리스트 — 5차 Phase 9-2)
     * 유효 동의·거부 배지·철회 배지를 모두 이 결과에서 계산한다(ConsentFlagDto). 쿼리 1번.
     */
    @Query("""
            select new kr.co.seoulit.his.labimagingservice.imagingconsent.dto.ConsentFlagDto(
                   o.imageOrderId, c.consentYn, c.withdrawnYn)
            from ConsentEntity c
            join c.imageOrder o
            where o.imageOrderId in :imageOrderIds
            """)
    List<ConsentFlagDto> findConsentFlags(@Param("imageOrderIds") List<String> imageOrderIds);

    /**
     * 같은 오더에 같은 유형의 "유효한 동의"(동의함 + 미철회)가 이미 있는지 확인한다. (중복 등록 차단, LAB031)
     *
     * ⚠ 거부(consent_yn='N') 기록은 중복 판정 대상이 아니다. (2026-09-29, D13)
     *   환자가 처음엔 거부했다가 설명을 듣고 다시 동의하는 경우가 있다. 거부 기록이 남아 있다고
     *   재동의를 막으면 그 오더는 영영 촬영할 수 없다.
     *
     * ⚠ DB에 UNIQUE 제약이 없다. 오더 1건에 재동의·거부 이력이 여러 건 쌓이는 것이 정상이라
     *   제약을 걸 수 없기 때문이다. 그래서 조건을 코드에서 판단한다.
     */
    boolean existsByImageOrder_ImageOrderIdAndConsentTypeCodeAndConsentYnAndWithdrawnYn(
            String imageOrderId, String consentTypeCode, String consentYn, String withdrawnYn);
}
