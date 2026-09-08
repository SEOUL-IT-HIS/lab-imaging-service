package kr.co.seoulit.his.labimagingservice.imagingacquisition.service;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.imagingacquisition.dto.ImageFileSummaryDto;
import kr.co.seoulit.his.labimagingservice.imagingacquisition.dto.ImageFileUploadRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingacquisition.entity.ImageFileEntity;
import kr.co.seoulit.his.labimagingservice.imagingacquisition.mapper.ImageFileMapper;
import kr.co.seoulit.his.labimagingservice.imagingacquisition.repository.ImageFileRepository;
import kr.co.seoulit.his.labimagingservice.imagingconsent.repository.ConsentRepository;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageReceptionEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.repository.ImageReceptionRepository;
import kr.co.seoulit.his.labimagingservice.imagingschedule.repository.ImageScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 촬영 수행 / 영상파일(IMAGE_FILE) 등록 서비스
 * 대응 유스케이스: UC-IMG-03 촬영/영상판독대기등록
 *   (Jira ZP2-105 사전요건 검증, ZP2-106 상태 전이, ZP2-108 SeaweedFS 저장, ZP2-112 실패 처리)
 *
 * ⚠ "판독대기등록"이라는 티켓 이름과 달리 이 서비스의 실제 범위는 "촬영 수행 + 영상파일 저장"이다.
 *   판독(IMAGE_READING) 자체는 ZP2-23(imaginginterpretation)의 몫이고 여기서 다루지 않는다.
 *   업로드가 끝나면 ImageWorklistService.decideNextStep 이 "다음은 판독"이라고 표시만 한다.
 *
 * ── imagingconsent 패키지 사용 범위
 *   ConsentRepository 를 조회 전용으로만 참조한다. ConsentService/ConsentEntity 는 건드리지
 *   않는다 — 0단계에서 이름만 옮긴 기존 동의 기능의 로직은 이번 작업 대상이 아니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageFileService {

    /** 최종(현재 유효) 일정 판별값. IMAGE_SCHEDULE.latest_yn (ImageScheduleService 와 동일) */
    private static final String LATEST = "Y";

    /**
     * 허용하는 영상파일 MIME 타입 화이트리스트. (ZP2-112)
     *
     * ⚠ 근거 — 이 화면이 다루는 대상은 방사선과 촬영 결과물이다.
     *   - application/dicom : 의료영상 표준 포맷(DICOM). CT/MRI 장비가 실제로 이 형식을 낸다.
     *   - image/jpeg, image/png : 장비에서 바로 못 뽑거나 스캔·사진으로 대체 등록하는 경우
     *     (외부 필름 스캔본, 초음파 캡처 이미지 등) 대비해 열어 둔다.
     *   - image/tiff : 일부 구형 초음파·내시경 장비가 무손실 저장에 쓴다.
     *   실행파일·문서(exe, html, js 등)는 의도적으로 막는다 — 영상판독 화면이 여는 파일이라
     *   그 종류가 아니면 화면이 열 수도 없고, 열리지 않는 파일을 받아 줄 이유가 없다.
     *
     * ⚠ image/* 전체를 통으로 허용하지 않는다. gif·webp 처럼 의료영상과 무관한 포맷까지
     *   열어주는 것보다, 실제로 쓰이는 포맷을 열거하고 필요해지면 추가하는 편이 안전하다.
     */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/dicom", "image/jpeg", "image/png", "image/tiff");

    private final ImageFileRepository imageFileRepository;
    private final ImageReceptionRepository imageReceptionRepository;
    private final ImageScheduleRepository imageScheduleRepository;
    private final ConsentRepository consentRepository;
    private final ImageFileMapper imageFileMapper;

    /**
     * ⚠ 공용 RestTemplate(RestTemplateConfig.restTemplate)이 아니라 SeaweedFS 전용 Bean 을 쓴다.
     *   대용량 멀티파트 전송이라 읽기 타임아웃 요구사항이 patient/admin 호출과 다르다.
     *   (RestTemplateConfig.seaweedFsRestTemplate 주석 참고)
     */
    @Qualifier("seaweedFsRestTemplate")
    private final RestTemplate seaweedFsRestTemplate;

    @Value("${app.seaweedfs.filer-url}")
    private String filerUrl;

    /**
     * 영상파일을 업로드하고 SeaweedFS + DB에 저장한다.
     *
     * 처리 순서
     *   1) 사전요건 검증 — 파일 형식, 접수/항목 존재, 환자 본인 확인, 동의, 일정. (ZP2-105)
     *   2) SeaweedFS 업로드. (ZP2-108)
     *   3) IMAGE_FILE 저장 + 촬영항목 상태를 ACQUIRED 로 전이. (ZP2-106, ZP2-110)
     *   4) 3)이 실패하면 2)에서 올린 파일을 정리한다. (ZP2-112)
     *
     * ⚠ @Transactional 이 SeaweedFS 업로드를 롤백해 주지 않는다.
     *   외부 HTTP 호출은 트랜잭션 범위 밖의 부수효과라, DB 저장이 실패해 트랜잭션이 롤백돼도
     *   이미 SeaweedFS 에 올라간 파일은 그대로 남는다("고아 파일"). 그래서 DB 저장을 별도
     *   try-catch 로 감싸 실패 시 SeaweedFS 쪽을 직접 DELETE 로 정리한다.
     */
    @Transactional
    public ImageFileSummaryDto uploadImageFile(ImageFileUploadRequestDto request) {

        ImageOrderItemEntity orderItem = validateAcquisitionPrerequisites(request);

        String storageKey = uploadToSeaweedFs(request.getImageOrderItemId(), request.getFile());

        // ⚠ 이 지점부터 실패하면 위에서 올린 파일이 고아가 된다. catch 에서 반드시 정리한다.
        try {
            ImageFileEntity imageFile = ImageFileEntity.builder()
                    .storageKey(storageKey)
                    .fileName(request.getFile().getOriginalFilename())
                    .fileSize(request.getFile().getSize())
                    .contentType(request.getFile().getContentType())
                    // 클라이언트 시계를 신뢰하지 않는다. 업로드 시각의 기준은 서버 하나여야 한다.
                    // (LabResultService.createLabResult 의 recordedAt 과 같은 원칙)
                    .uploadedAt(LocalDateTime.now())
                    .uploadedById(request.getUploadedById())
                    .build();
            imageFile.assignImageOrderItem(orderItem);

            ImageFileEntity saved = imageFileRepository.save(imageFile);

            // ZP2-106: 촬영 완료로 상태 전이. 재촬영으로 두 번째 파일이 올라와도 멱등이라 안전하다.
            orderItem.markAcquired();

            return imageFileMapper.toResponse(saved);

        } catch (RuntimeException e) {
            cleanupOrphanFile(storageKey);
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB056,
                    "영상파일 저장에 실패해 업로드가 취소되었습니다. (imageOrderItemId="
                            + request.getImageOrderItemId() + ")",
                    e);
        }
    }

    /**
     * 촬영항목 1건의 영상파일 목록을 조회한다. (ZP2-110)
     */
    @Transactional(readOnly = true)
    public List<ImageFileSummaryDto> getImageFilesByOrderItemId(String imageOrderItemId) {
        List<ImageFileEntity> files = imageFileRepository
                .findByImageOrderItem_ImageOrderItemIdOrderByUploadedAtAsc(imageOrderItemId);
        return imageFileMapper.toResponseList(files);
    }

    /**
     * 영상파일 1건을 다운로드한다. SeaweedFS 에서 그대로 스트리밍해 응답한다.
     *
     * ⚠ storageKey 를 클라이언트에 노출하지 않고 imageFileId 로만 받는다.
     *   Filer 경로를 그대로 알려주면 접근 통제(이 API 를 거치지 않고 Filer 를 직접 두드리는 것)를
     *   막을 방법이 없어진다. (ImageFileSummaryDto 가 storageKey 를 응답에서 뺀 이유와 같다)
     */
    @Transactional(readOnly = true)
    public DownloadedFile downloadImageFile(String imageFileId) {
        ImageFileEntity imageFile = imageFileRepository.findById(imageFileId)
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB050,
                        "등록된 영상파일을 찾을 수 없습니다. (imageFileId=" + imageFileId + ")"));

        byte[] content = downloadFromSeaweedFs(imageFile.getStorageKey());
        return new DownloadedFile(imageFile.getFileName(), imageFile.getContentType(), content);
    }

    /** 다운로드 결과. 파일 스트림 자체를 엔티티/DTO 로 두지 않고 컨트롤러가 쓸 최소 묶음만 돌려준다. */
    public record DownloadedFile(String fileName, String contentType, byte[] content) {
    }

    // ------------------------------------------------------------------
    // ZP2-105 사전요건 검증
    // ------------------------------------------------------------------

    /**
     * 등록 요청과 화면 조회가 같은 규칙을 쓰도록 검증을 한 메서드에 모은다.
     *
     * 검증 순서 (위에서부터 먼저 걸리는 것이 이유가 된다)
     *   1) 파일 존재/형식 — 나머지 조회보다 먼저 확인한다. DB 조회 몇 번을 아낄 수 있다.
     *   2) 접수 존재 확인
     *   3) 항목이 그 접수의 오더에 실제로 속하는지 (ImageScheduleService.findOrderItemOfReception 과 동일 검증)
     *   4) 환자 본인 확인 — 요청의 patientId 가 그 오더의 patientId 와 같은지
     *   5) 동의서 등록 여부 — 유효한(철회 전) 동의가 있는지
     *   6) 촬영 일정 등록 여부 — 이 접수+항목 조합의 최종 일정이 있는지
     *
     * @return 검증을 통과한 촬영항목 — 호출한 쪽이 재조회하지 않도록 그대로 돌려준다.
     */
    private ImageOrderItemEntity validateAcquisitionPrerequisites(ImageFileUploadRequestDto request) {

        MultipartFile file = request.getFile();
        if (file == null || file.isEmpty()) {
            // ⚠ @NotNull 은 "파트가 왔는가"만 본다. 빈 파일(0바이트)로 온 경우는
            //   Bean Validation 으로 걸리지 않아 여기서 따로 막는다.
            throw new LabImagingBusinessException(LabMessageCode.LAB998, "업로드할 파일이 비어 있습니다.");
        }
        validateContentType(file.getContentType());

        ImageReceptionEntity reception = imageReceptionRepository.findById(request.getImageReceptionId())
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB015,
                        "영상 촬영 접수 정보를 찾을 수 없습니다. (imageReceptionId="
                                + request.getImageReceptionId() + ")"));

        ImageOrderItemEntity orderItem = findOrderItemOfReception(reception, request.getImageOrderItemId());

        if (!reception.getImageOrder().getPatientId().equals(request.getPatientId())) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB051,
                    "환자 정보가 일치하지 않습니다. (imageReceptionId=" + request.getImageReceptionId() + ")");
        }

        String imageOrderId = reception.getImageOrder().getImageOrderId();
        boolean hasConsent = !consentRepository
                .findOrderIdsWithValidConsent(List.of(imageOrderId))
                .isEmpty();
        if (!hasConsent) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB052,
                    "동의가 등록되지 않았습니다. 촬영 전 동의를 먼저 등록하세요. (imageOrderId=" + imageOrderId + ")");
        }

        boolean hasSchedule = imageScheduleRepository
                .findByImageReception_ImageReceptionIdAndImageOrderItem_ImageOrderItemIdAndLatestYn(
                        request.getImageReceptionId(), request.getImageOrderItemId(), LATEST)
                .isPresent();
        if (!hasSchedule) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB053,
                    "촬영 일정이 등록되지 않았습니다. 일정을 먼저 등록하세요. (imageOrderItemId="
                            + request.getImageOrderItemId() + ")");
        }

        return orderItem;
    }

    /**
     * 촬영항목이 그 접수의 오더에 실제로 속하는지 확인한다.
     * (ImageScheduleService.findOrderItemOfReception 과 동일한 검증 — 같은 이유로 존재한다.
     *  항목ID를 그대로 믿으면 다른 오더의 촬영항목에 영상파일을 붙일 수 있다)
     */
    private ImageOrderItemEntity findOrderItemOfReception(ImageReceptionEntity reception,
                                                          String imageOrderItemId) {
        return reception.getImageOrder().getOrderItems().stream()
                .filter(item -> item.getImageOrderItemId().equals(imageOrderItemId))
                .findFirst()
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB047,
                        "이 접수의 촬영항목이 아닙니다. (imageOrderItemId=" + imageOrderItemId + ")"));
    }

    /** MIME 타입이 화이트리스트에 있는지 확인한다. 근거는 ALLOWED_CONTENT_TYPES 주석 참고. */
    private void validateContentType(String contentType) {
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB054,
                    "허용되지 않는 파일 형식입니다. (contentType=" + contentType + ", 허용="
                            + ALLOWED_CONTENT_TYPES + ")");
        }
    }

    // ------------------------------------------------------------------
    // ZP2-108 SeaweedFS 연동
    // ------------------------------------------------------------------

    /**
     * SeaweedFS Filer 에 파일을 업로드한다.
     *
     * ⚠ 경로 규칙: /image-files/{imageOrderItemId}/{uuid}_{원본파일명}
     *   uuid 를 파일명 앞에 붙이는 이유는 같은 항목에 같은 이름의 파일이 재촬영으로
     *   여러 번 올라올 수 있기 때문이다. uuid 가 없으면 Filer 가 같은 경로에 덮어써서
     *   이전 촬영본이 사라진다 — IMAGE_FILE 은 "재촬영 시 새 행 추가, 기존 행 유지"가
     *   원칙인데 파일 자체가 덮어써지면 그 원칙이 깨진다.
     *
     * ⚠ MultipartFile.getResource() 를 그대로 쓴다. FormHttpMessageConverter 가 Resource
     *   파트의 getFilename() 을 Content-Disposition 의 filename 으로 채워 준다.
     *   byte[] 를 직접 읽어 ByteArrayResource 로 감싸지 않는 이유는, 그러면 getFilename() 이
     *   없어 Filer 가 저장할 파일명을 알 수 없기 때문이다.
     *
     * @return 확정된 storage_key (이후 IMAGE_FILE.storage_key 로 저장된다)
     */
    private String uploadToSeaweedFs(String imageOrderItemId, MultipartFile file) {

        String storageKey = "/image-files/" + imageOrderItemId + "/"
                + UUID.randomUUID() + "_" + file.getOriginalFilename();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file.getResource());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            seaweedFsRestTemplate.postForEntity(filerUrl + storageKey, entity, String.class);
        } catch (RestClientException e) {
            // ⚠ 여기서는 정리할 것이 없다. 업로드 자체가 실패했으니 IMAGE_FILE 도, SeaweedFS 에
            //   남는 파일도 없다. (ZP2-112 "SeaweedFS 업로드 자체 실패 → 파일 저장 시도 안 함")
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB055,
                    "영상 저장소 연결에 실패했습니다. SeaweedFS 가 실행 중인지 확인하세요. (filerUrl="
                            + filerUrl + ")",
                    e);
        }
        return storageKey;
    }

    /**
     * SeaweedFS 에서 파일을 내려받는다. (다운로드 API 전용)
     *
     * ⚠ 전체를 메모리에 올린다(byte[]). 지금 화이트리스트(DICOM/JPEG/PNG/TIFF 단일 파일)
     *   규모에서는 문제가 되지 않는다. 대용량 스트리밍 응답이 필요해지면
     *   Resource/InputStreamResource 로 바꿔 컨트롤러가 직접 스트리밍하도록 바꿔야 한다.
     */
    private byte[] downloadFromSeaweedFs(String storageKey) {
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

    /**
     * DB 저장 실패 후 SeaweedFS 에 남은 고아 파일을 정리한다. (ZP2-112)
     *
     * ⚠ 이 메서드는 절대 예외를 던지지 않는다. 정리가 실패해도 로그만 남기고 삼킨다.
     *   호출한 쪽(uploadImageFile)이 "원래 왜 실패했는지"를 사용자에게 알려야 하는데,
     *   여기서 예외가 올라가면 그 원인이 "정리 실패"로 뒤바뀌어 버린다.
     *   대신 로그에는 반드시 남긴다 — 그래야 운영자가 SeaweedFS 에 남은 고아 파일을
     *   수동으로 찾아 지울 단서가 생긴다.
     */
    private void cleanupOrphanFile(String storageKey) {
        try {
            seaweedFsRestTemplate.delete(filerUrl + storageKey);
        } catch (RestClientException cleanupException) {
            log.warn("SeaweedFS 고아 파일 정리 실패 — 수동 삭제가 필요합니다. (storageKey={})",
                    storageKey, cleanupException);
        }
    }
}
