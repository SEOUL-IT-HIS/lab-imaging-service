package kr.co.seoulit.his.labimagingservice.imagingacquisition.repository;

import kr.co.seoulit.his.labimagingservice.imagingacquisition.entity.ConsentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 동의서 리포지토리.
 *
 * TODO: 조회 메서드 추가
 *   - 영상오더별 동의 이력 조회 (ZP2-80 검사 진행 전 동의 상태 확인)
 *   - 오더 1건에 재동의 이력이 여러 건 쌓이므로, "현재 유효한 동의"를 어떻게 판별할지
 *     먼저 정할 것 (withdrawn_yn='N' 중 최신? LAB_SCHEDULE 처럼 latest_yn 컬럼 추가?)
 */
public interface ConsentRepository extends JpaRepository<ConsentEntity, String> {
}
