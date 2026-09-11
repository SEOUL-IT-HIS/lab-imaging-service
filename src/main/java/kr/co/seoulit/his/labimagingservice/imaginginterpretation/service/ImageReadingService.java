package kr.co.seoulit.his.labimagingservice.imaginginterpretation.service;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.imaginginterpretation.dto.ImageReadingSummaryDto;
import kr.co.seoulit.his.labimagingservice.imaginginterpretation.entity.ImageReadingEntity;
import kr.co.seoulit.his.labimagingservice.imaginginterpretation.mapper.ImageReadingMapper;
import kr.co.seoulit.his.labimagingservice.imaginginterpretation.repository.ImageReadingRepository;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.repository.ImageOrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 영상판독 서비스
 * 대응 유스케이스: UC-IMG-04 영상판독처리 (Jira ZP2-23)
 *
 * ── 핵심 설계: findOrCreate
 *   IMAGE_READING 행은 촬영(ZP2-21, imagingacquisition 패키지) 시점에 만들어지지 않는다.
 *   그 패키지는 판독을 몰라야 한다 — 촬영과 판독은 서로 다른 담당자·시점의 업무이고,
 *   ImageFileService 가 IMAGE_READING 을 직접 만들면 두 업무가 한 트랜잭션으로 묶여
 *   촬영 완료 자체가 판독 인프라(공통코드 검증 등)에 발목 잡힐 수 있다.
 *   그래서 이 서비스가 판독 워크리스트/상세 조회가 "처음 호출되는 시점"에 그 자리에서
 *   reading_status_code=01(대기)로 만든다(findOrCreate). imagingacquisition 패키지의
 *   ImageFileService/ImageFileRepository/ImageFileEntity 는 건드리지 않는다 — 조회(리포지토리
 *   재사용)만 한다.
 *
 * ── 상태 전이 (LabResultService 와 같은 패턴)
 *   대기(01) → 판독중(02) : assignReading (담당자 배정)
 *   판독중(02) → 확정(03) : confirmReading (findings 필수, 재확정 금지)
 *   확정(03) 이후에는 배정/소견수정/재확정 모두 거절한다.
 */
@Service
@RequiredArgsConstructor
public class ImageReadingService {

    /** 공통코드 그룹 — admin 에 01=대기, 02=판독중, 03=완료로 등록되어 있어야 한다. */
    private static final String READING_STATUS_CD = "READING_STATUS_CD";

    /** 판독상태: 대기 (촬영은 끝났고 아직 담당자가 배정되지 않음) */
    private static final String STATUS_WAITING = "01";
    /** 판독상태: 판독중 (담당자가 배정되어 소견을 작성 중) */
    private static final String STATUS_IN_PROGRESS = "02";
    /** 판독상태: 완료/확정 (더 이상 수정 불가) */
    private static final String STATUS_CONFIRMED = "03";

    private final ImageReadingRepository imageReadingRepository;
    private final ImageOrderItemRepository imageOrderItemRepository;
    private final ImageReadingMapper imageReadingMapper;
    private final CommonCodeCache commonCodeCache;

    // ------------------------------------------------------------------
    // 조회 (findOrCreate)
    // ------------------------------------------------------------------

    /**
     * 판독 워크리스트를 조회한다. (ZP2-125 응급 우선 정렬)
     *
     * 처리 순서
     *   1) 영상파일이 1건 이상인 촬영항목을 모은다. (ImageOrderItemRepository, ImageFileEntity 존재 여부로
     *      필터링 — imagingacquisition 패키지는 조회만 한다) 응급(urgencyYn=Y)이 위로, 그 안에서는
     *      오래된 항목이 위로 오도록 쿼리에서 이미 정렬해 받는다.
     *   2) 그 항목들의 IMAGE_READING 을 IN 절로 일괄 조회한다(N+1 금지).
     *   3) 없는 항목은 findOrCreate 로 새로 만든다(대기 01).
     *
     * ⚠ readOnly 가 아니다. findOrCreate 로 쓰기가 일어날 수 있는 조회라 LabResultService 의
     *   등록 계열과 같은 취급이다.
     */
    @Transactional
    public List<ImageReadingSummaryDto> getReadingWorklist() {

        List<ImageOrderItemEntity> items = imageOrderItemRepository.findAcquiredItemsWithImageOrder();
        if (items.isEmpty()) {
            return List.of();
        }

        List<String> itemIds = items.stream()
                .map(ImageOrderItemEntity::getImageOrderItemId)
                .toList();

        Map<String, ImageReadingEntity> readingByItemId = imageReadingRepository
                .findByImageOrderItem_ImageOrderItemIdIn(itemIds).stream()
                .collect(Collectors.toMap(
                        reading -> reading.getImageOrderItem().getImageOrderItemId(),
                        reading -> reading));

        List<ImageReadingEntity> toCreate = new ArrayList<>();
        List<ImageReadingEntity> readings = new ArrayList<>();

        for (ImageOrderItemEntity item : items) {
            ImageReadingEntity reading = readingByItemId.get(item.getImageOrderItemId());
            if (reading == null) {
                reading = ImageReadingEntity.builder()
                        .readingStatusCode(STATUS_WAITING)
                        .build();
                reading.assignImageOrderItem(item);
                toCreate.add(reading);
            }
            readings.add(reading);
        }

        if (!toCreate.isEmpty()) {
            validateCode(READING_STATUS_CD, STATUS_WAITING, "판독상태코드");
            imageReadingRepository.saveAll(toCreate);
        }

        return readings.stream().map(imageReadingMapper::toResponse).toList();
    }

    /**
     * 촬영항목ID로 판독 상세를 조회한다. 판독 행이 없으면 findOrCreate 로 만든다.
     * (영상파일 목록은 이 API 가 담지 않는다. 화면이 기존 ImageFileService 조회로 별도로 받아 합친다)
     */
    @Transactional
    public ImageReadingSummaryDto getReadingByOrderItemId(String imageOrderItemId) {
        return imageReadingRepository.findByImageOrderItem_ImageOrderItemId(imageOrderItemId)
                .map(imageReadingMapper::toResponse)
                .orElseGet(() -> createReading(imageOrderItemId));
    }

    private ImageReadingSummaryDto createReading(String imageOrderItemId) {

        ImageOrderItemEntity item = imageOrderItemRepository.findById(imageOrderItemId)
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB064,
                        "촬영항목 정보를 찾을 수 없습니다. (imageOrderItemId=" + imageOrderItemId + ")"));

        validateCode(READING_STATUS_CD, STATUS_WAITING, "판독상태코드");

        ImageReadingEntity reading = ImageReadingEntity.builder()
                .readingStatusCode(STATUS_WAITING)
                .build();
        reading.assignImageOrderItem(item);

        return imageReadingMapper.toResponse(imageReadingRepository.save(reading));
    }

    // ------------------------------------------------------------------
    // 상태 전이 (배정 / 소견 입력 / 확정)
    // ------------------------------------------------------------------

    /**
     * 담당자를 배정한다. 대기(01) → 판독중(02).
     *
     * ⚠ 이미 판독중(02)인 건도 허용한다 — 담당자 변경(재배정)이다. 상태는 그대로 02 로 유지되고
     *   되돌아가지 않는다. 확정(03)인 건만 거절한다. (프롬프트 요구사항 그대로)
     */
    @Transactional
    public ImageReadingSummaryDto assignReading(String imageReadingId, String assignedToId) {

        requireId(imageReadingId, "판독ID");
        ImageReadingEntity reading = findReadingOrThrow(imageReadingId);

        if (STATUS_CONFIRMED.equals(reading.getReadingStatusCode())) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB062,
                    "이미 확정된 판독은 담당자를 배정할 수 없습니다. (imageReadingId=" + imageReadingId + ")");
        }

        validateCode(READING_STATUS_CD, STATUS_IN_PROGRESS, "판독상태코드");

        reading.assign(assignedToId, LocalDateTime.now(), STATUS_IN_PROGRESS);

        return imageReadingMapper.toResponse(reading);
    }

    /**
     * 소견을 입력/수정한다. 확정 전(01/02)만 허용한다.
     * (LabResultService.updateLabResult 의 "확정 후 수정 금지" 패턴 그대로)
     */
    @Transactional
    public ImageReadingSummaryDto updateFindings(String imageReadingId, String findings) {

        requireId(imageReadingId, "판독ID");
        ImageReadingEntity reading = findReadingOrThrow(imageReadingId);

        if (STATUS_CONFIRMED.equals(reading.getReadingStatusCode())) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB062,
                    "이미 확정된 판독은 소견을 수정할 수 없습니다. (imageReadingId=" + imageReadingId + ")");
        }

        reading.updateFindings(findings);

        return imageReadingMapper.toResponse(reading);
    }

    /**
     * 판독을 확정(전자서명)한다. 판독중/대기(01/02) → 확정(03).
     * (LabResultService.confirmLabResult 패턴 그대로 — 상태전이 + 확정자/확정일시 기록 + 재확정 금지)
     *
     * ⚠ findings 가 비어 있으면 거절한다. 소견 없는 확정은 "판독하지 않고 완료 처리"가 되어 버린다.
     */
    @Transactional
    public ImageReadingSummaryDto confirmReading(String imageReadingId, String signedById) {

        requireId(imageReadingId, "판독ID");
        ImageReadingEntity reading = findReadingOrThrow(imageReadingId);

        if (STATUS_CONFIRMED.equals(reading.getReadingStatusCode())) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB062,
                    "이미 확정된 판독입니다. (확정일시=" + reading.getSignedAt() + ")");
        }

        if (reading.getFindings() == null || reading.getFindings().isBlank()) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB063,
                    "소견 없이는 확정할 수 없습니다. (imageReadingId=" + imageReadingId + ")");
        }

        validateCode(READING_STATUS_CD, STATUS_CONFIRMED, "판독상태코드");

        reading.confirm(STATUS_CONFIRMED, signedById, LocalDateTime.now());

        return imageReadingMapper.toResponse(reading);
    }

    // ------------------------------------------------------------------
    // 공통
    // ------------------------------------------------------------------

    private ImageReadingEntity findReadingOrThrow(String imageReadingId) {
        return imageReadingRepository.findById(imageReadingId)
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB061,
                        "판독 정보를 찾을 수 없습니다. (imageReadingId=" + imageReadingId + ")"));
    }

    /** ZP2-128: 배정/소견입력/확정 요청은 대상 식별자가 필수다. */
    private void requireId(String id, String label) {
        if (id == null || id.isBlank()) {
            throw new LabImagingBusinessException(LabMessageCode.LAB998, label + "는 필수입니다.");
        }
    }

    /** LabResultService.validateCode 와 같은 패턴. */
    private void validateCode(String groupCode, String code, String fieldLabel) {
        if (!commonCodeCache.isValid(groupCode, code)) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB017,
                    "유효하지 않은 " + fieldLabel + "입니다. (" + groupCode + "=" + code + ")"
            );
        }
    }
}
