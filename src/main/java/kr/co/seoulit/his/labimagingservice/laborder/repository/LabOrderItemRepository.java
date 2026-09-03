package kr.co.seoulit.his.labimagingservice.laborder.repository;

import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LabOrderItemRepository extends JpaRepository<LabOrderItemEntity, String> {

    /**
     * 접수번호로 그 접수가 속한 오더의 검사항목을 조회한다. (결과 등록 화면용)
     *
     * ⚠ LAB_ORDER_ITEM 은 접수가 아니라 오더에 붙는다.
     *   LAB_RECEPTION → LAB_ORDER → LAB_ORDER_ITEM 으로 두 단계 거슬러 올라가야 한다.
     *   화면은 접수를 골라서 들어오므로 그 경로를 쿼리 한 번으로 좁힌다.
     *
     * ⚠ join fetch 로 labOrder 를 함께 가져온다.
     *   LabOrderItemEntity.labOrder 가 @ManyToOne(LAZY) 라, 매핑에서 건드리면
     *   행마다 SELECT 가 추가로 나간다(N+1).
     *
     * ⚠ 정렬은 created_at asc — 오더에 담긴 순서 그대로 보여준다.
     *   담당자가 처방을 보며 대조하는 화면이라 순서가 흔들리면 안 된다.
     */
    /**
     * 여러 오더의 검사항목을 한 번에 조회한다. (워크리스트 진행상태 조립용)
     *
     * ⚠ 접수가 아니라 오더로 모은다. LAB_ORDER_ITEM 은 오더에 붙고,
     *   LAB_ORDER : LAB_RECEPTION = 1:N 이라 한 오더의 접수가 여럿이면 항목을 공유한다.
     *   접수마다 조회하면 행 수만큼 쿼리가 나간다(N+1).
     */
    @Query("""
            select i from LabOrderItemEntity i
            join fetch i.labOrder o
            where o.labOrderId in :labOrderIds
            order by i.createdAt asc
            """)
    List<LabOrderItemEntity> findByLabOrderIdIn(java.util.Collection<String> labOrderIds);

    @Query("""
            select i from LabOrderItemEntity i
            join fetch i.labOrder o
            where exists (
                select 1 from LabReceptionEntity r
                 where r.labOrder = o
                   and r.receptionNo = :receptionNo)
            order by i.createdAt asc
            """)
    List<LabOrderItemEntity> findByReceptionNo(String receptionNo);
}
