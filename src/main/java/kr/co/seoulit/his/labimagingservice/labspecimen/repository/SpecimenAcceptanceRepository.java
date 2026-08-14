package kr.co.seoulit.his.labimagingservice.labspecimen.repository;

import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenAcceptanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 검체 인수/적합성 판정 리포지토리.
 */
public interface SpecimenAcceptanceRepository extends JpaRepository<SpecimenAcceptanceEntity, String> {

    /** 검체 1건의 판정. SPECIMEN : SPECIMEN_ACCEPTANCE 가 1:1(specimen_id UNIQUE)이라 단건이다. */
    Optional<SpecimenAcceptanceEntity> findBySpecimen_SpecimenId(String specimenId);

    /**
     * 이미 인수/판정된 검체인지 확인. (중복 판정 방지)
     * DB 의 UNIQUE 제약이 최종 방어선이지만, 그대로 두면 사용자에게 DB 예외가 그대로 나간다.
     * 미리 확인해서 업무 메시지(LAB022)로 돌려주려는 것이다.
     */
    boolean existsBySpecimen_SpecimenId(String specimenId);

    /**
     * 여러 검체의 판정을 한 번에 조회한다. (목록에서 적합상태를 함께 보여주기 위함)
     *
     * ⚠ 검체마다 단건 조회를 부르면 행 수만큼 쿼리가 나간다(N+1).
     *   검체ID 목록을 통째로 넘겨 IN 절 한 번으로 끝낸다.
     *   (LabScheduleRepository 의 예정일시 일괄 조회와 같은 구조)
     */
    List<SpecimenAcceptanceEntity> findBySpecimen_SpecimenIdIn(Collection<String> specimenIds);
}
