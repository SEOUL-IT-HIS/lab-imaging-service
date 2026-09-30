package kr.co.seoulit.his.labimagingservice.imagingconsent.dto;

/**
 * 워크리스트용 동의 상태 조각 — 오더ID + 동의여부 + 철회여부만. (5차 Phase 9-2)
 * ⚠ 엔티티를 통째로 받지 않는다. 워크리스트는 동의 본문을 쓰지 않고, 엔티티로 받으면 지연로딩이 따라붙는다.
 */
public record ConsentFlagDto(String imageOrderId, String consentYn, String withdrawnYn) {

    public boolean isValidConsent() {
        return "Y".equals(consentYn) && "N".equals(withdrawnYn);
    }

    public boolean isActiveRefusal() {
        return "N".equals(consentYn) && "N".equals(withdrawnYn);
    }

    public boolean isWithdrawn() {
        return "Y".equals(withdrawnYn);
    }
}
