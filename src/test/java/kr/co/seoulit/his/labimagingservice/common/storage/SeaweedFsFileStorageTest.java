package kr.co.seoulit.his.labimagingservice.common.storage;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SeaweedFS 공통 모듈 (5차 Phase 4 추출).
 * ⚠ 영상 업로드 동작이 추출 전과 같아야 한다(5차 조건) — 저장 키 형식과 실패 코드·문구를 고정해 둔다.
 */
class SeaweedFsFileStorageTest {

    private static final String FILER = "http://filer:8888";

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final SeaweedFsFileStorage storage = new SeaweedFsFileStorage(restTemplate, FILER);
    private final MockMultipartFile png = new MockMultipartFile("file", "chest.png", "image/png", new byte[]{1, 2, 3});

    @Test
    @DisplayName("영상 업로드 키 형식은 추출 전과 같다: /image-files/{항목ID}/{uuid}_{원본파일명}")
    void imageKeyFormatUnchanged() {
        String key = storage.upload("/image-files/", "item-1", png);

        assertThat(key).matches("^/image-files/item-1/[0-9a-f\\-]{36}_chest\\.png$");
        verify(restTemplate).postForEntity(eq(FILER + key), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("업로드 연결 실패는 LAB055 + 추출 전과 같은 문구")
    void uploadFailure() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("down"));

        assertThatThrownBy(() -> storage.upload("/image-files/", "item-1", png))
                .isInstanceOf(LabImagingBusinessException.class)
                .hasMessageStartingWith("영상 저장소 연결에 실패했습니다. SeaweedFS 가 실행 중인지 확인하세요.")
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB055);
    }

    @Test
    @DisplayName("고아 파일 정리는 실패해도 예외를 던지지 않는다")
    void deleteQuietly() {
        doThrow(new ResourceAccessException("down")).when(restTemplate).delete(anyString());
        storage.deleteQuietly("/image-files/item-1/x_chest.png");
    }
}
