package kr.co.seoulit.his.labimagingservice.imagingorder.repository;

import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageOrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface ImageOrderItemRepository extends JpaRepository<ImageOrderItemEntity, String> {

    /**
     * 여러 오더의 촬영항목을 한 번에 조회한다. (워크리스트 진행상태 조립용)
     *
     * ⚠ 접수가 아니라 오더로 모은다. IMAGE_ORDER_ITEM 은 오더에 붙고,
     *   IMAGE_ORDER : IMAGE_RECEPTION = 1:N 이라 한 오더의 접수가 여럿이면 항목을 공유한다.
     *   접수마다 조회하면 행 수만큼 쿼리가 나간다(N+1).
     *
     * ⚠ join fetch 로 imageOrder 를 함께 가져온다. @ManyToOne(LAZY) 라 오더ID를 꺼내는 순간
     *   행마다 SELECT 가 추가로 나간다. (검사 LabOrderItemRepository.findByLabOrderIdIn 과 같은 구조)
     */
    @Query("""
            select i from ImageOrderItemEntity i
            join fetch i.imageOrder o
            where o.imageOrderId in :imageOrderIds
            order by i.createdAt asc
            """)
    List<ImageOrderItemEntity> findByImageOrder_ImageOrderIdIn(Collection<String> imageOrderIds);

    /**
     * 접수번호로 그 접수가 속한 오더의 촬영항목을 조회한다. (일정 등록 화면용)
     *
     * ⚠ 촬영항목은 접수가 아니라 오더에 붙는다.
     *   IMAGE_RECEPTION → IMAGE_ORDER → IMAGE_ORDER_ITEM 으로 두 단계 거슬러 올라간다.
     *   화면은 접수를 골라서 들어오므로 그 경로를 쿼리 한 번으로 좁힌다.
     */
    @Query("""
            select i from ImageOrderItemEntity i
            join fetch i.imageOrder o
            where exists (
                select 1 from ImageReceptionEntity r
                 where r.imageOrder = o
                   and r.receptionNo = :receptionNo)
            order by i.createdAt asc
            """)
    List<ImageOrderItemEntity> findByReceptionNo(String receptionNo);

    /**
     * 영상파일(IMAGE_FILE)이 1건 이상 등록된 촬영항목 전체. (판독 워크리스트 대상 조회, ZP2-23)
     *
     * ⚠ imagingacquisition 패키지(ImageFileEntity)를 조회 조건(exists)으로만 참조한다.
     *   ImageFileRepository/ImageFileService 는 건드리지 않는다 — ImageReceptionRepository 가
     *   ImageScheduleEntity 를 exists 서브쿼리로 참조하는 것과 같은, 이 프로젝트에 이미 있는 관례다.
     *
     * ⚠ 응급(urgencyYn) 우선 정렬을 쿼리에서 끝낸다. (ZP2-125) 'Y' > 'N' 이라 desc 정렬만으로
     *   응급 건이 위로 온다 — 별도 판정 로직 없이 문자열 비교로 충분하다.
     *   응급 여부 안에서는 오래 촬영된 순(먼저 만들어진 항목)으로 둔다.
     */
    @Query("""
            select i from ImageOrderItemEntity i
            join fetch i.imageOrder o
            where exists (
                select 1 from ImageFileEntity f where f.imageOrderItem = i)
            order by o.urgencyYn desc, i.createdAt asc
            """)
    List<ImageOrderItemEntity> findAcquiredItemsWithImageOrder();
}
