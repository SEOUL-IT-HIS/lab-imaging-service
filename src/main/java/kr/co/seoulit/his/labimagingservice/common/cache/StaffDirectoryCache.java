package kr.co.seoulit.his.labimagingservice.common.cache;

import kr.co.seoulit.his.labimagingservice.businessdelegate.admin.AdminStaffBusinessDelegate;
import kr.co.seoulit.his.labimagingservice.businessdelegate.admin.dto.RoleInfo;
import kr.co.seoulit.his.labimagingservice.businessdelegate.admin.dto.StaffInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 직원 디렉터리 로컬 캐시. (직원 검증, 2026-10-05, 04번 지시서 Phase 2-A)
 *
 * CommonCodeCache 와 다른 점 — 지시서 §2-A-1이 요구한 대로:
 *   - 기동 시(@PostConstruct) 적재하지 않는다. 첫 조회(isActiveStaff 등)가 들어올 때 비로소
 *     admin 을 부른다(lazy) — 이 서비스가 쓰이지 않는 환경(수납처럼 아직 가동 안 하는 쪽)에서도
 *     admin 세션/가동 여부와 무관하게 기동은 항상 성공해야 한다.
 *   - @Scheduled 로 주기 적재하지 않는다. TTL(app.staff-directory.refresh-ms, 기본 10분)이
 *     지난 뒤 다음 조회가 들어올 때 갱신한다.
 *   - 갱신 실패 시 예외를 삼키고 기존 캐시를 유지한다(CommonCodeCache 와 같은 이유) —
 *     단, 최초 적재 전에 실패하면 유지할 캐시가 없으므로 "조회 불가" 상태(available=false)가 된다.
 *     StaffValidator 가 이 상태를 OFF/WARN/ENFORCE·on-unavailable 정책에 따라 처리한다.
 *
 * 동시성 — 갱신은 synchronized 블록으로 한 스레드만 수행한다. 직원 검증은 요청량이 많지 않고
 * (접수·배정 등 사람이 누르는 동작) admin 호출도 3초 타임아웃이라, 여러 스레드가 동시에 만료를
 * 발견해도 한 번만 부르고 나머지는 그 결과를 기다리는 쪽이 캐시를 이중으로 갱신하는 것보다 낫다.
 */
@Slf4j
@Component
public class StaffDirectoryCache {

    private final AdminStaffBusinessDelegate adminStaffClient;
    private final long refreshIntervalMs;

    /** null = 아직 한 번도 적재하지 못했다(기동 직후, 또는 최초 조회가 실패). */
    private volatile Snapshot snapshot;

    public StaffDirectoryCache(
            AdminStaffBusinessDelegate adminStaffClient,
            @Value("${app.staff-directory.refresh-ms:600000}") long refreshIntervalMs) {
        this.adminStaffClient = adminStaffClient;
        this.refreshIntervalMs = refreshIntervalMs;
    }

    /** 직원 디렉터리를 지금 조회할 수 있는 상태인지. false면 호출부가 on-unavailable 정책을 적용해야 한다. */
    public boolean isAvailable() {
        return ensureLoaded() != null;
    }

    /** empId가 재직 중인 직원인지. 디렉터리 조회 불가 상태면 false(모른다는 뜻 — 호출부가 별도 처리). */
    public boolean isActiveStaff(String empId) {
        if (empId == null || empId.isBlank()) {
            return false;
        }
        Snapshot snap = ensureLoaded();
        return snap != null && snap.activeStaffIds.contains(empId);
    }

    /** empId가 재직 중이면서 의사 역할인지. */
    public boolean isDoctor(String empId) {
        if (empId == null || empId.isBlank()) {
            return false;
        }
        Snapshot snap = ensureLoaded();
        return snap != null && snap.activeDoctorIds.contains(empId);
    }

    /** empId → 표시용 사번(empNo). 없으면 null. 입력(2-B 수동등록) 쪽에서 physicianNo 보정에 쓴다. */
    public String findEmpNo(String empId) {
        if (empId == null || empId.isBlank()) {
            return null;
        }
        Snapshot snap = ensureLoaded();
        return snap == null ? null : snap.empNoById.get(empId);
    }

    private Snapshot ensureLoaded() {
        Snapshot current = snapshot;
        if (current != null && !current.isExpired(refreshIntervalMs)) {
            return current;
        }

        synchronized (this) {
            current = snapshot;
            if (current != null && !current.isExpired(refreshIntervalMs)) {
                return current;
            }
            try {
                List<StaffInfo> staff = adminStaffClient.getStaffList();
                List<RoleInfo> roles = adminStaffClient.getRoleList();
                Snapshot refreshed = build(staff, roles);
                snapshot = refreshed;
                log.info("직원 디렉터리 캐시를 갱신했습니다. 직원 {}명(의사 {}명)",
                        refreshed.empNoById.size(), refreshed.activeDoctorIds.size());
                return refreshed;
            } catch (Exception e) {
                if (current == null) {
                    log.warn("직원 디렉터리 최초 적재에 실패했습니다. 조회 불가 상태로 둡니다.", e);
                } else {
                    log.warn("직원 디렉터리 갱신에 실패했습니다. 기존 캐시(적재 시각 {})를 유지합니다.",
                            current.loadedAtEpochMs, e);
                }
                return current;
            }
        }
    }

    private static Snapshot build(List<StaffInfo> staff, List<RoleInfo> roles) {
        Set<String> doctorRoleIds = new HashSet<>();
        for (RoleInfo role : roles) {
            if ("N".equals(role.getUseYn())) {
                continue;
            }
            if (isDoctorRole(role.getRoleCode(), role.getRoleName())) {
                doctorRoleIds.add(role.getRoleId());
            }
        }

        Set<String> activeStaffIds = new HashSet<>();
        Set<String> activeDoctorIds = new HashSet<>();
        Map<String, String> empNoById = new HashMap<>();

        for (StaffInfo info : staff) {
            if (info.getEmpId() == null) {
                continue;
            }
            empNoById.put(info.getEmpId(), info.getEmpNo());
            boolean retired = info.getRetireDate() != null && !info.getRetireDate().isBlank();
            if (retired) {
                continue;
            }
            activeStaffIds.add(info.getEmpId());
            boolean hasDoctorRole = info.getRoleIds() != null
                    && info.getRoleIds().stream().anyMatch(doctorRoleIds::contains);
            if (hasDoctorRole) {
                activeDoctorIds.add(info.getEmpId());
            }
        }

        return new Snapshot(System.currentTimeMillis(), activeStaffIds, activeDoctorIds, empNoById);
    }

    /** hisfrontend useStaffDirectory.ts 의 isDoctorRole 과 같은 판별 기준(§2-A-3 — 양쪽을 맞춘다). */
    private static boolean isDoctorRole(String roleCode, String roleName) {
        String code = roleCode == null ? "" : roleCode;
        String name = roleName == null ? "" : roleName;
        return code.toUpperCase().equals("DOCTOR")
                || name.contains("의사")
                || name.toLowerCase().contains("doctor");
    }

    private record Snapshot(
            long loadedAtEpochMs,
            Set<String> activeStaffIds,
            Set<String> activeDoctorIds,
            Map<String, String> empNoById) {

        boolean isExpired(long ttlMs) {
            return System.currentTimeMillis() - loadedAtEpochMs >= ttlMs;
        }
    }
}
