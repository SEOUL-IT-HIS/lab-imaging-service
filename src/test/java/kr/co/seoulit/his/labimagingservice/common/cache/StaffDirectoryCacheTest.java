package kr.co.seoulit.his.labimagingservice.common.cache;

import kr.co.seoulit.his.labimagingservice.businessdelegate.admin.AdminStaffBusinessDelegate;
import kr.co.seoulit.his.labimagingservice.businessdelegate.admin.dto.RoleInfo;
import kr.co.seoulit.his.labimagingservice.businessdelegate.admin.dto.StaffInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 직원 디렉터리 캐시 — lazy 적재·TTL·갱신 실패 시 기존 캐시 유지. (04번 지시서 Phase 2-E)
 */
class StaffDirectoryCacheTest {

    private static StaffInfo staff(String empId, String empNo, String retireDate, List<String> roleIds) {
        StaffInfo info = new StaffInfo();
        info.setEmpId(empId);
        info.setEmpNo(empNo);
        info.setEmpName("Name-" + empNo);
        info.setRetireDate(retireDate);
        info.setRoleIds(roleIds);
        return info;
    }

    private static RoleInfo role(String roleId, String roleCode, String roleName, String useYn) {
        RoleInfo role = new RoleInfo();
        role.setRoleId(roleId);
        role.setRoleCode(roleCode);
        role.setRoleName(roleName);
        role.setUseYn(useYn);
        return role;
    }

    @Test
    @DisplayName("기동 직후에는 admin을 부르지 않는다(lazy) — 첫 조회가 들어올 때 비로소 부른다")
    void doesNotLoadUntilFirstQuery() {
        AdminStaffBusinessDelegate client = mock(AdminStaffBusinessDelegate.class);
        StaffDirectoryCache cache = new StaffDirectoryCache(client, 600_000L);

        verify(client, never()).getStaffList();

        when(client.getStaffList()).thenReturn(List.of());
        when(client.getRoleList()).thenReturn(List.of());
        cache.isActiveStaff("emp-1");

        verify(client, times(1)).getStaffList();
    }

    @Test
    @DisplayName("TTL 안에서는 여러 번 조회해도 admin을 한 번만 부른다")
    void reusesCacheWithinTtl() {
        AdminStaffBusinessDelegate client = mock(AdminStaffBusinessDelegate.class);
        when(client.getStaffList()).thenReturn(List.of(staff("emp-1", "10001", null, List.of("role-doctor"))));
        when(client.getRoleList()).thenReturn(List.of(role("role-doctor", "DOCTOR", "의사", "Y")));

        StaffDirectoryCache cache = new StaffDirectoryCache(client, 600_000L);

        cache.isActiveStaff("emp-1");
        cache.isDoctor("emp-1");
        cache.isActiveStaff("emp-2");

        verify(client, times(1)).getStaffList();
        verify(client, times(1)).getRoleList();
    }

    @Test
    @DisplayName("TTL이 지나면 다음 조회에서 다시 적재한다")
    void reloadsAfterTtlExpires() throws InterruptedException {
        AdminStaffBusinessDelegate client = mock(AdminStaffBusinessDelegate.class);
        when(client.getStaffList()).thenReturn(List.of(staff("emp-1", "10001", null, List.of())));
        when(client.getRoleList()).thenReturn(List.of());

        StaffDirectoryCache cache = new StaffDirectoryCache(client, 20L);

        cache.isActiveStaff("emp-1");
        Thread.sleep(40);
        cache.isActiveStaff("emp-1");

        verify(client, times(2)).getStaffList();
    }

    @Test
    @DisplayName("재직 중이고 의사 역할인 직원만 isDoctor가 true다 — 퇴사자/비의사는 false")
    void isDoctorOnlyForActiveDoctors() {
        AdminStaffBusinessDelegate client = mock(AdminStaffBusinessDelegate.class);
        when(client.getStaffList()).thenReturn(List.of(
                staff("emp-doctor", "10001", null, List.of("role-doctor")),
                staff("emp-retired-doctor", "10002", "2026-01-01", List.of("role-doctor")),
                staff("emp-nurse", "10003", null, List.of("role-nurse"))));
        when(client.getRoleList()).thenReturn(List.of(
                role("role-doctor", "DOCTOR", "의사", "Y"),
                role("role-nurse", "NURSE", "간호사", "Y")));

        StaffDirectoryCache cache = new StaffDirectoryCache(client, 600_000L);

        assertThat(cache.isDoctor("emp-doctor")).isTrue();
        assertThat(cache.isActiveStaff("emp-doctor")).isTrue();
        assertThat(cache.isDoctor("emp-retired-doctor")).isFalse();
        assertThat(cache.isActiveStaff("emp-retired-doctor")).isFalse();
        assertThat(cache.isDoctor("emp-nurse")).isFalse();
        assertThat(cache.isActiveStaff("emp-nurse")).isTrue();
        assertThat(cache.isActiveStaff("no-such-emp")).isFalse();
    }

    @Test
    @DisplayName("사용 중지(useYn=N)된 역할은 의사 판별에서 뺀다")
    void excludesDisabledRoleFromDoctorCheck() {
        AdminStaffBusinessDelegate client = mock(AdminStaffBusinessDelegate.class);
        when(client.getStaffList()).thenReturn(List.of(staff("emp-1", "10001", null, List.of("role-doctor"))));
        when(client.getRoleList()).thenReturn(List.of(role("role-doctor", "DOCTOR", "의사", "N")));

        StaffDirectoryCache cache = new StaffDirectoryCache(client, 600_000L);

        assertThat(cache.isDoctor("emp-1")).isFalse();
    }

    @Test
    @DisplayName("최초 적재가 실패하면 조회 불가 상태다")
    void unavailableWhenFirstLoadFails() {
        AdminStaffBusinessDelegate client = mock(AdminStaffBusinessDelegate.class);
        when(client.getStaffList()).thenThrow(new RuntimeException("admin down"));

        StaffDirectoryCache cache = new StaffDirectoryCache(client, 600_000L);

        assertThat(cache.isAvailable()).isFalse();
        assertThat(cache.isActiveStaff("emp-1")).isFalse();
    }

    @Test
    @DisplayName("갱신 중 실패하면 기존 캐시를 그대로 유지한다")
    void retainsStaleCacheWhenRefreshFails() throws InterruptedException {
        AdminStaffBusinessDelegate client = mock(AdminStaffBusinessDelegate.class);
        when(client.getStaffList())
                .thenReturn(List.of(staff("emp-1", "10001", null, List.of())))
                .thenThrow(new RuntimeException("admin down"));
        when(client.getRoleList()).thenReturn(List.of());

        StaffDirectoryCache cache = new StaffDirectoryCache(client, 20L);

        assertThat(cache.isActiveStaff("emp-1")).isTrue();
        Thread.sleep(40);
        // 두 번째 호출은 getStaffList()에서 예외가 나지만, 기존 캐시(emp-1 재직 중)를 그대로 돌려준다.
        assertThat(cache.isActiveStaff("emp-1")).isTrue();
        assertThat(cache.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("findEmpNo는 조회 불가 상태면 null, 적재 후에는 사번을 돌려준다")
    void findEmpNoReturnsEmpNoOrNull() {
        AdminStaffBusinessDelegate client = mock(AdminStaffBusinessDelegate.class);
        when(client.getStaffList()).thenReturn(List.of(staff("emp-1", "10001", null, List.of())));
        when(client.getRoleList()).thenReturn(List.of());

        StaffDirectoryCache cache = new StaffDirectoryCache(client, 600_000L);

        assertThat(cache.findEmpNo("emp-1")).isEqualTo("10001");
        assertThat(cache.findEmpNo("no-such-emp")).isNull();
        assertThat(cache.findEmpNo(null)).isNull();
    }
}
