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
}
