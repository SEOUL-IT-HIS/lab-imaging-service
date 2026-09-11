package kr.co.seoulit.his.labimagingservice.imaginginterpretation.repository;

import kr.co.seoulit.his.labimagingservice.imaginginterpretation.entity.ImageReadingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 영상판독 리포지토리.
 * (LabResultRepository 와 같은 모양 — 1:1 상대 엔티티의 PK 로 찾고, IN 절로 일괄 조회한다)
 */
public interface ImageReadingRepository extends JpaRepository<ImageReadingEntity, String> {

    /**
     * 촬영항목ID로 판독을 찾는다. (단건 조회 / findOrCreate 용)
     * image_order_item_id 에 UNIQUE 가 걸려 있어 판독은 최대 1건이다.
     *
     * ⚠ join fetch 로 imageOrderItem 과 그 오더까지 함께 가져온다. ImageReadingMapper 가
     *   imageOrderItemId 뿐 아니라 imageOrder 쪽 필드(patientId, urgencyYn 등)도 응답에 담는데,
     *   두 관계 모두 LAZY 라 그냥 두면 매핑 시점에 SELECT 가 추가로 나간다.
     *   (ImageFileRepository.findByImageOrderItem_ImageOrderItemIdOrderByUploadedAtAsc 와 같은 이유)
     */
    @Query("""
            select r from ImageReadingEntity r
            join fetch r.imageOrderItem i
            join fetch i.imageOrder o
            where i.imageOrderItemId = :imageOrderItemId
            """)
    Optional<ImageReadingEntity> findByImageOrderItem_ImageOrderItemId(
            @Param("imageOrderItemId") String imageOrderItemId);

    /**
     * 여러 촬영항목의 판독을 한 번에 조회한다. (판독 워크리스트 findOrCreate 조립, 영상 워크리스트
     * 진행도 집계 양쪽에서 쓴다)
     *
     * ⚠ 항목마다 조회하면 항목 수만큼 쿼리가 나간다(N+1). 항목ID를 통째로 넘겨 IN 절 한 번으로 끝낸다.
     *   (ImageOrderItemRepository.findByImageOrder_ImageOrderIdIn 과 같은 패턴)
     *
     * ⚠ join fetch 두 단계(imageOrderItem, 그 오더)를 모두 건다. 위 단건 조회와 같은 이유다.
     */
    @Query("""
            select r from ImageReadingEntity r
            join fetch r.imageOrderItem i
            join fetch i.imageOrder o
            where i.imageOrderItemId in :imageOrderItemIds
            """)
    List<ImageReadingEntity> findByImageOrderItem_ImageOrderItemIdIn(
            @Param("imageOrderItemIds") Collection<String> imageOrderItemIds);

    /**
     * 여러 촬영항목 중 특정 판독상태(주로 03=확정)인 건수를 센다.
     *
     * ⚠ 워크리스트 오더 단위 합산(ImageWorklistItemDto.readingCompletedCount)에는 이 메서드를
     *   그대로 쓰지 않는다. 이 메서드는 IN 절 전체에 대한 단일 스칼라 값만 돌려주는데, 워크리스트는
     *   "오더별로 몇 건인지" 그룹 단위 값이 필요하다. 그래서 ImageWorklistService 는 대신
     *   findByImageOrderItem_ImageOrderItemIdIn 으로 엔티티를 받아 imageFileCount 집계와 같은
     *   방식(Collectors.groupingBy)으로 오더별로 묶는다.
     *   이 count 메서드는 오더 구분 없이 "전체 중 몇 건 확정됐는지"가 필요한 다른 조회(통계 등)를
     *   위해 남겨 둔다.
     */
    long countByImageOrderItem_ImageOrderItemIdInAndReadingStatusCode(
            Collection<String> imageOrderItemIds, String readingStatusCode);
}
