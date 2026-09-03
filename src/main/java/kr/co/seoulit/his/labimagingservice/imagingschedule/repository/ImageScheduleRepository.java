package kr.co.seoulit.his.labimagingservice.imagingschedule.repository;

import kr.co.seoulit.his.labimagingservice.imagingschedule.entity.ImageScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 영상 일정 리포지토리.
 *
 * ⚠ 2026-09-03 부터 일정은 접수가 아니라 촬영항목마다 1건이다.
 *   그래서 "접수의 최종 일정"은 단건이 아니라 목록이다. 단건으로 받는 메서드는
 *   반드시 항목ID까지 조건에 넣어야 한다 — 접수ID만으로 Optional 을 받으면
 *   촬영항목이 둘 이상인 접수에서 NonUniqueResultException 이 난다.
 */
public interface ImageScheduleRepository extends JpaRepository<ImageScheduleEntity, String> {

    /**
     * 접수+촬영항목의 최종 일정 1건. (등록 중복 확인 / 재조정 대상 조회)
     * 최종 일정 UNIQUE(UX_ISCH_LATEST)가 (접수, 항목) 조합이라 결과는 최대 1건이다.
     */
    Optional<ImageScheduleEntity>
    findByImageReception_ImageReceptionIdAndImageOrderItem_ImageOrderItemIdAndLatestYn(
            String imageReceptionId, String imageOrderItemId, String latestYn);

    /**
     * 접수 1건의 최종 일정 목록. 촬영항목마다 1건씩 나온다.
     *
     * ⚠ 예전에는 Optional 이었다. 항목 단위로 바뀌면서 목록이 됐다.
     *   호출하는 쪽은 "가장 이른 예정일시" 처럼 목록을 접어서 써야 한다.
     */
    List<ImageScheduleEntity> findByImageReception_ImageReceptionIdAndLatestYn(
            String imageReceptionId, String latestYn);

    /**
     * 여러 접수의 최종 일정을 한 번에 조회한다. (목록·워크리스트 조립용)
     *
     * ⚠ 접수 건마다 조회하면 행 수만큼 쿼리가 나간다(N+1).
     *   접수ID 목록을 통째로 넘겨 IN 절 한 번으로 끝낸다.
     *
     * ⚠ 접수 하나가 여러 행으로 나온다(항목 수만큼). 접수ID 로 toMap 하면 키가 겹쳐 터진다.
     *   groupingBy 로 묶어서 써야 한다.
     */
    List<ImageScheduleEntity> findByImageReception_ImageReceptionIdInAndLatestYn(
            Collection<String> imageReceptionIds, String latestYn);
}
