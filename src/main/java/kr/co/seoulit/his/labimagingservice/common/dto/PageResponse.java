package kr.co.seoulit.his.labimagingservice.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 페이지 조회 응답. (5차 Phase 5 — 발신 이력 조회에서 처음 쓴다)
 *
 * ⚠ Spring Data 의 Page 를 그대로 응답에 내보내지 않는다. Page 의 JSON 모양(pageable, sort 등)은
 *   Spring 버전마다 바뀌고 필드도 많아, 프론트가 그 구조에 묶인다. 화면에 필요한 값만 담는다.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
