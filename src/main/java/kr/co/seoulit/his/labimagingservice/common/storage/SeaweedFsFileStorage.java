package kr.co.seoulit.his.labimagingservice.common.storage;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * SeaweedFS Filer HTTP 업로드·다운로드·삭제 공통 모듈. (5차 Phase 4 — ImageFileService 에서 추출)
 *
 * 쓰는 곳: 영상파일(ImageFileService, "/image-files/"), 병리 첨부(PathologyResultService, "/pathology-results/")
 *
 * ⚠ 추출 전후로 영상 업로드 동작은 한 글자도 달라지면 안 된다(5차 조건). 그래서
 *   저장 키 형식({prefix}{referenceId}/{uuid}_{원본파일명}), 실패 메시지코드(LAB055)와 문구를 그대로 옮겼다.
 *   영상 쪽 LAB056(저장 실패 → 업로드 취소)은 DB 저장 실패를 다루는 규칙이라 여기가 아니라 호출한 서비스에 남는다.
 *
 * ⚠ 허용 파일 형식은 여기서 정하지 않는다. 형식 규칙은 업무마다 다르다(영상=dicom/jpg/png/tiff, 병리=jpg/png/pdf).
 *   호출하는 쪽이 검사한 뒤 넘긴다.
 *
 * ⚠ @Transactional 과 무관하다. DB 가 롤백돼도 여기서 올린 파일은 남는다(고아 파일).
 *   호출하는 쪽이 DB 저장 실패 시 deleteQuietly 로 정리해야 한다. (ZP2-112 와 같은 처리)
 */
@Slf4j
@Component
public class SeaweedFsFileStorage {

    /**
     * ⚠ 공용 RestTemplate 이 아니라 SeaweedFS 전용 Bean 이다. 대용량 멀티파트 전송이라 읽기 타임아웃
     *   요구사항이 patient/admin 호출과 다르다. (RestTemplateConfig.seaweedFsRestTemplate 주석 참고)
     */
    private final RestTemplate seaweedFsRestTemplate;
    private final String filerUrl;

    public SeaweedFsFileStorage(@Qualifier("seaweedFsRestTemplate") RestTemplate seaweedFsRestTemplate,
                                @Value("${app.seaweedfs.filer-url}") String filerUrl) {
        this.seaweedFsRestTemplate = seaweedFsRestTemplate;
        this.filerUrl = filerUrl;
    }

    /**
     * 파일을 올리고 저장 키를 돌려준다.
     *
     * @param pathPrefix  "/image-files/" 처럼 앞뒤 슬래시가 붙은 업무별 경로
     * @param referenceId 파일이 붙는 원본 ID (촬영항목ID, 검사항목ID 등) — 경로 한 단계가 된다
     * @throws LabImagingBusinessException LAB055 — SeaweedFS 연결 실패(올라간 파일 없음, 정리할 것도 없음)
     */
    public String upload(String pathPrefix, String referenceId, MultipartFile file) {

        String storageKey = pathPrefix + referenceId + "/"
                + UUID.randomUUID() + "_" + file.getOriginalFilename();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file.getResource());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            seaweedFsRestTemplate.postForEntity(filerUrl + storageKey, entity, String.class);
        } catch (RestClientException e) {
            // ⚠ 여기서는 정리할 것이 없다. 업로드 자체가 실패했으니 SeaweedFS 에 남는 파일도 없다.
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB055,
                    "영상 저장소 연결에 실패했습니다. SeaweedFS 가 실행 중인지 확인하세요. (filerUrl="
                            + filerUrl + ")",
                    e);
        }
        return storageKey;
    }

    /**
     * 파일을 내려받는다.
     * ⚠ 전체를 메모리에 올린다(byte[]). 단일 영상·첨부 규모에서는 문제없다. 대용량 스트리밍이 필요해지면 바꾼다.
     */
    public byte[] download(String storageKey) {
        try {
            return seaweedFsRestTemplate.getForObject(filerUrl + storageKey, byte[].class);
        } catch (RestClientException e) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB055,
                    "영상 저장소 연결에 실패했습니다. SeaweedFS 가 실행 중인지 확인하세요. (storageKey="
                            + storageKey + ")",
                    e);
        }
    }

    /** 고아 파일 정리. 실패해도 예외를 던지지 않는다 — 원래 실패(DB 저장 실패)를 가리면 안 되기 때문이다. */
    public void deleteQuietly(String storageKey) {
        try {
            seaweedFsRestTemplate.delete(filerUrl + storageKey);
        } catch (RestClientException cleanupException) {
            log.warn("SeaweedFS 고아 파일 정리 실패 — 수동 삭제가 필요합니다. (storageKey={})",
                    storageKey, cleanupException);
        }
    }
}
