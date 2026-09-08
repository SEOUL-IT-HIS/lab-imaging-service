package kr.co.seoulit.his.labimagingservice.imagingacquisition.repository;

import kr.co.seoulit.his.labimagingservice.imagingacquisition.entity.ImageFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ImageFileRepository extends JpaRepository<ImageFileEntity, String> {

    /**
     * 촬영항목 1건에 등록된 영상파일 전체. (등록 화면 목록용, ZP2-110)
     * 오래된 순으로 내려준다 — 재촬영 이력이 위에서 아래로 쌓인 순서 그대로 보이게.
     *
     * ⚠ join fetch 로 imageOrderItem 을 함께 가져온다. ImageFileMapper.toResponse 가
     *   imageOrderItemId 를 응답에 담는데, @ManyToOne(LAZY) 라 그 값을 꺼내는 순간
     *   행마다 SELECT 가 추가로 나간다. (ConsentRepository.findByImageOrderIdWithOrder 의
     *   join fetch 와 같은 이유 — 이 프로젝트에서 실제로 겪은 문제라 항상 이렇게 막는다)
     */
    @Query("""
            select f from ImageFileEntity f
            join fetch f.imageOrderItem i
            where i.imageOrderItemId = :imageOrderItemId
            order by f.uploadedAt asc
            """)
    List<ImageFileEntity> findByImageOrderItem_ImageOrderItemIdOrderByUploadedAtAsc(
            @Param("imageOrderItemId") String imageOrderItemId);

    /**
     * 여러 항목의 영상파일을 한 번에 조회한다. (워크리스트 진행상태 조립용)
     *
     * ⚠ 항목마다 조회하면 행 수만큼 쿼리가 나간다(N+1). 항목ID 를 통째로 넘겨 IN 절 한 번으로 끝낸다.
     *   (ImageOrderItemRepository.findByImageOrder_ImageOrderIdIn 과 같은 패턴)
     *
     * ⚠ join fetch 가 위 메서드보다 더 중요하다. 여기서는 서로 다른 항목ID 여러 개를 넘기므로
     *   join fetch 가 없으면 항목 종류 수만큼 SELECT 가 추가로 나간다(진짜 N+1).
     *
     * ⚠ 엔티티 목록을 그대로 돌려주고, 항목별 개수·최신 업로드 시각으로 묶는 건 호출하는 쪽이 한다.
     *   count(...) in (...) 같은 집계 쿼리 한 방으로도 만들 수 있지만, 그러면 결과가
     *   "항목ID + 개수" 투영 타입이 되어 이 프로젝트의 다른 워크리스트 조립(ImageWorklistService)과
     *   방식이 달라진다. 엔티티를 모아 오고 Collectors.groupingBy 로 묶는 쪽으로 통일한다.
     */
    @Query("""
            select f from ImageFileEntity f
            join fetch f.imageOrderItem i
            where i.imageOrderItemId in :imageOrderItemIds
            """)
    List<ImageFileEntity> findByImageOrderItem_ImageOrderItemIdIn(
            @Param("imageOrderItemIds") Collection<String> imageOrderItemIds);
}
