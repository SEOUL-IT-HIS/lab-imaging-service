package kr.co.seoulit.his.labimagingservice.businessdelegate.admin.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * admin-service의 직원 1명. (직원 검증, 2026-10-05)
 *
 * 필드명은 hisfrontend features/emp/types/empTypes.ts 의 Emp 와 맞춘다. 그쪽이 실제로 쓰고
 * 있는 응답 모양을 그대로 따른 것이다 — 명세서가 아니라 이미 동작하는 화면을 기준으로 했다.
 * 검증에 쓰는 건 empId·retireDate·roleIds 뿐이라 나머지(empEmail 등)는 매핑하지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
public class StaffInfo {

    /** EMPLOYEE.EMP_ID (UUID, 36자) */
    private String empId;

    private String empNo;

    private String empName;

    /** 퇴사일 — 없으면(null/빈 문자열) 재직 중 */
    private String retireDate;

    /** 배정된 역할ID 목록. 지금은 1인 1역이라 최대 1개지만 List 로 받는다(응답 그대로). */
    private List<String> roleIds;
}
