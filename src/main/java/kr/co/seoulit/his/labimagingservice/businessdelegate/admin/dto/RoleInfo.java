package kr.co.seoulit.his.labimagingservice.businessdelegate.admin.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * admin-service의 역할 1개. (직원 검증, 2026-10-05)
 *
 * roleCode/roleName 으로 "의사" 역할을 판별하는 데 쓴다 (hisfrontend useStaffDirectory.ts 의
 * isDoctorRole 과 같은 판별 기준). roleId 는 StaffInfo.roleIds 와 join 하는 키다.
 */
@Getter
@Setter
@NoArgsConstructor
public class RoleInfo {

    private String roleId;

    private String roleCode;

    private String roleName;

    /** 사용 여부 — "N" 이면 폐기된 역할이라 의사 판별에서 뺀다. */
    private String useYn;
}
