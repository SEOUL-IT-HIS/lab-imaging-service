package kr.co.seoulit.his.labimagingservice.common.cache;

import jakarta.annotation.PostConstruct;
import kr.co.seoulit.his.labimagingservice.businessdelegate.admin.AdminCommonCodeBusinessDelegate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 공통코드 로컬 캐시.
 *
 * 2026-08 팀 결정: 코드값 검증 때마다 admin 서비스에 실시간 조회하지 않고,
 * 앱 메모리에 그룹별 코드값을 올려두고 검증한다.
 *
 * 동작
 *   - @PostConstruct : 앱 시작 시 1회 전체 적재
 *   - @Scheduled     : 10분(app.admin-service.common-code.refresh-interval-ms)마다 갱신
 *   - 갱신 실패 시    : 예외를 삼키고 로그만 남긴 뒤 기존 캐시를 그대로 유지한다.
 *                      admin 서비스가 잠깐 죽었다고 이 서비스까지 기동 실패하거나
 *                      멀쩡하던 캐시가 날아가면 안 되기 때문이다.
 *
 * 동시성
 *   조회(isValid)는 요청 스레드에서, 갱신은 스케줄러 스레드에서 일어난다.
 *   부분 수정 대신 "새 Map을 다 만든 뒤 참조만 통째로 교체"하는 방식이라
 *   volatile 참조 하나로 충분하다. 읽는 쪽은 항상 이전 스냅샷 아니면 새 스냅샷을 보고,
 *   반쯤 갱신된 중간 상태를 보지 않는다.
 *
 * ⚠ isValid(...)를 Service 계층에 연결하는 작업은 다음 단계다.
 *   각 필드(예: scheduleTypeCode, 촬영장비코드)에 대응하는 그룹코드ID가 아직 확정되지 않았고,
 *   기동 로그에서 캐시가 실제로 채워지는지("공통코드 캐시를 갱신했습니다. 그룹 N개") 먼저 확인해야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommonCodeCache {

    private final AdminCommonCodeBusinessDelegate adminCommonCodeClient;

    /** 코드그룹ID → 사용 중인 코드값 Set. 갱신 시 참조를 통째로 교체한다. */
    private volatile Map<String, Set<String>> codesByGroup = Map.of();

    @PostConstruct
    public void loadOnStartup() {
        log.info("공통코드 캐시 최초 적재를 시작합니다.");
        refresh();
    }

    @Scheduled(
            fixedRateString = "${app.admin-service.common-code.refresh-interval-ms}",
            initialDelayString = "${app.admin-service.common-code.refresh-interval-ms}")
    public void refreshPeriodically() {
        refresh();
    }

    /**
     * 코드값이 해당 그룹에서 사용 중인 값인지 확인한다.
     *
     * ⚠ 캐시에 없는 그룹코드는 false를 반환한다.
     *   (아직 적재되지 않은 그룹인지, 정말 잘못된 코드값인지 구분되지 않으므로
     *    호출부를 연결하기 전에 캐시가 실제로 채워지는지 반드시 확인해야 한다)
     */
    public boolean isValid(String groupCode, String code) {
        if (groupCode == null || code == null) {
            return false;
        }
        Set<String> codes = codesByGroup.get(groupCode);
        return codes != null && codes.contains(code);
    }

    /** 캐시에 적재된 그룹 수 — 기동 확인/모니터링용 */
    public int getCachedGroupCount() {
        return codesByGroup.size();
    }

    /**
     * admin 서비스에서 전체 공통코드를 다시 읽어 캐시를 교체한다.
     * 실패하거나 결과가 비어 있으면 기존 캐시를 유지한다.
     */
    private void refresh() {
        try {
            Map<String, List<String>> loaded = adminCommonCodeClient.getAllCodeValues();

            if (loaded == null || loaded.isEmpty()) {
                // admin 쪽 응답이 비었거나 그룹이 하나도 없는 경우.
                // 멀쩡한 기존 캐시를 빈 값으로 덮어쓰지 않는다.
                log.warn("공통코드 조회 결과가 비어 있어 캐시를 갱신하지 않습니다. (기존 {}개 그룹 유지)",
                        codesByGroup.size());
                return;
            }

            Map<String, Set<String>> refreshed = new LinkedHashMap<>();
            loaded.forEach((groupCode, codes) -> refreshed.put(groupCode, Set.copyOf(codes)));
            this.codesByGroup = Map.copyOf(refreshed);

            log.info("공통코드 캐시를 갱신했습니다. 그룹 {}개", refreshed.size());

        } catch (Exception e) {
            // 여기서 예외가 새어나가면 @PostConstruct는 기동 실패, @Scheduled는 이후 실행 중단으로 이어진다.
            // 갱신은 실패해도 서비스는 기존 캐시로 계속 동작해야 하므로 삼키고 로그만 남긴다.
            log.error("공통코드 캐시 갱신에 실패했습니다. 기존 캐시({}개 그룹)를 유지합니다.",
                    codesByGroup.size(), e);
        }
    }
}
