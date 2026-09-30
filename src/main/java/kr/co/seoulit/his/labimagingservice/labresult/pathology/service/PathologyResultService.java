package kr.co.seoulit.his.labimagingservice.labresult.pathology.service;

import kr.co.seoulit.his.labimagingservice.billing.service.BillingChargeService;
import kr.co.seoulit.his.labimagingservice.labresult.service.LabResultTransmissionService;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.common.storage.SeaweedFsFileStorage;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabOrderItemRepository;
import kr.co.seoulit.his.labimagingservice.labresult.pathology.dto.PathologyResultCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.labresult.pathology.dto.PathologyResultSummaryDto;
import kr.co.seoulit.his.labimagingservice.labresult.pathology.dto.PathologyResultUpdateRequestDto;
import kr.co.seoulit.his.labimagingservice.labresult.pathology.entity.PathologyResultEntity;
import kr.co.seoulit.his.labimagingservice.labresult.pathology.repository.PathologyResultRepository;
import kr.co.seoulit.his.labimagingservice.labresult.type.LabResultType;
import kr.co.seoulit.his.labimagingservice.labresult.type.LabResultTypeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 병리검사결과 서비스
 * 대응 유스케이스: UC-RST-03 병리검사결과등록 (Jira ZP2-15: 93~98)
 *
 * 흐름: 병리유형(조직/세포) → 소견(육안/현미경/진단, D6) → 진단명코드(선택) → 첨부 1건(선택) → 저장(01) → 확정(02)
 *
 * ── 첨부와 DB 의 일관성 (영상파일 ZP2-112 와 같은 원칙, 처리 방식은 한 단계 보강)
 *   - 업로드 실패(LAB055) → 결과도 저장하지 않는다. (UC-RST-03 예외: 첨부 실패 시 재등록)
 *   - 업로드 후 DB 저장이 실패해 트랜잭션이 롤백되면 → 올린 파일을 지운다.
 *   - 수정에서 첨부를 교체하면 → 이전 파일은 "커밋된 뒤에만" 지운다. 커밋 전에 지우면 롤백됐을 때
 *     DB 는 이전 키를 가리키는데 파일은 없는 상태가 된다.
 *   ⚠ 이 정리는 TransactionSynchronization 에 건다. 영상 업로드처럼 save() 주변 try-catch 만으로는
 *     커밋 시점(flush)에 나는 실패를 못 잡기 때문이다.
 *
 * ── 확정 규칙은 일반검사와 같다: 01→02 단방향, 확정 후 수정 불가(LAB040), 재확정 불가(LAB041),
 *   작성자·확정자=로그인 사용자(컨트롤러), 입력자=확정자 금지는 설정(D3).
 *
 * ⚠ 확정 시 청구는 일반검사와 같은 경로(BillingChargeService)로 요청한다(5차 Phase 5).
 */
@Service
@RequiredArgsConstructor
public class PathologyResultService {

    private static final String RESULT_STATUS_CD = "RESULT_STATUS_CD";
    private static final String PATHOLOGY_TYPE_CD = "PATHOLOGY_TYPE_CD";
    private static final String PATHOLOGY_DIAGNOSIS_CD = "PATHOLOGY_DIAGNOSIS_CD";

    private static final String STATUS_RECORDED = "01";
    private static final String STATUS_CONFIRMED = "02";

    /** 병리 첨부 저장 경로 (SeaweedFS) */
    private static final String ATTACHMENT_PATH_PREFIX = "/pathology-results/";

    /**
     * 병리 첨부 허용 형식 (D7: jpg, png, pdf).
     * ⚠ 영상파일 화이트리스트(dicom/jpg/png/tiff)와 다르다. 병리 첨부는 현미경 사진·판독지 PDF 가 대상이다.
     */
    static final Map<String, String> CONTENT_TYPE_BY_EXTENSION = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "pdf", "application/pdf");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "application/pdf");

    private final PathologyResultRepository pathologyResultRepository;
    private final LabOrderItemRepository labOrderItemRepository;
    private final CommonCodeCache commonCodeCache;
    private final LabResultTypeResolver labResultTypeResolver;
    private final SeaweedFsFileStorage seaweedFsFileStorage;
    /** 확정 시 청구 요청 (5차 Phase 5) */
    private final BillingChargeService billingChargeService;
    /** 확정 시 결과 전송 — 발신 이력(01) 경유, 커밋 후 발행 (5차 Phase 6) */
    private final LabResultTransmissionService labResultTransmissionService;

    @Value("${app.auth.forbid-self-confirm:false}")
    private boolean forbidSelfConfirm;

    // ------------------------------------------------------------------ 등록 (ZP2-93~97)

    @Transactional
    public PathologyResultSummaryDto createResult(PathologyResultCreateRequestDto request, MultipartFile file) {

        LabOrderItemEntity item = labOrderItemRepository.findById(request.getLabOrderItemId())
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB035,
                        "검사항목 정보를 찾을 수 없습니다. (labOrderItemId=" + request.getLabOrderItemId() + ")"));

        LabResultType type = labResultTypeResolver.resolve(item.getLabItemCode());
        if (type != LabResultType.PATHOLOGY) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB079,
                    "병리 결과로 등록할 수 없는 항목입니다. (검사항목코드=" + item.getLabItemCode() + ", 유형=" + type + ")");
        }
        if (pathologyResultRepository.existsByLabOrderItem_LabOrderItemId(item.getLabOrderItemId())) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB085,
                    "이미 병리 결과가 등록된 검사항목입니다. (검사항목코드=" + item.getLabItemCode() + ")");
        }

        validateCodes(request.getPathologyTypeCode(), request.getDiagnosisCode());
        validateCode(RESULT_STATUS_CD, STATUS_RECORDED, "결과상태코드");

        // 업무 검증이 모두 끝난 뒤에 올린다. 검증에 걸릴 요청 때문에 파일부터 올라가는 일을 막는다.
        String attachmentKey = uploadIfPresent(item.getLabOrderItemId(), file);

        try {
            PathologyResultEntity result = PathologyResultEntity.builder()
                    .pathologyTypeCode(request.getPathologyTypeCode())
                    .diagnosisCode(blankToNull(request.getDiagnosisCode()))
                    .findings(request.getFindings())
                    .attachmentFileKey(attachmentKey)
                    .resultStatusCode(STATUS_RECORDED)
                    .recordedAt(LocalDateTime.now())
                    .recordedById(request.getRecordedById())
                    .build();
            result.assignLabOrderItem(item);
            return toResponse(pathologyResultRepository.save(result));

        } catch (RuntimeException e) {
            // 올린 파일은 롤백 동기화(registerRollbackCleanup)가 지운다. 여기서는 원인만 사용자 메시지로 바꾼다.
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB056,
                    "병리 결과 저장에 실패해 첨부 업로드가 취소되었습니다. (labOrderItemId=" + item.getLabOrderItemId() + ")",
                    e);
        }
    }

    // ------------------------------------------------------------------ 수정 (첨부 재등록 포함)

    @Transactional
    public PathologyResultSummaryDto updateResult(String resultId, PathologyResultUpdateRequestDto request,
                                                  MultipartFile file) {

        PathologyResultEntity result = findDetailOrThrow(resultId);
        if (STATUS_CONFIRMED.equals(result.getResultStatusCode())) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB040,
                    "이미 확정된 결과는 수정할 수 없습니다. (pathologyResultId=" + resultId + ")");
        }

        validateCodes(request.getPathologyTypeCode(), request.getDiagnosisCode());

        result.modifyResult(request.getPathologyTypeCode(), blankToNull(request.getDiagnosisCode()), request.getFindings());

        String newKey = uploadIfPresent(result.getLabOrderItem().getLabOrderItemId(), file);
        if (newKey != null) {
            String previousKey = result.replaceAttachment(newKey);
            if (previousKey != null) {
                // 이전 파일은 커밋이 확정된 뒤에만 지운다.
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        seaweedFsFileStorage.deleteQuietly(previousKey);
                    }
                });
            }
        }
        return toResponse(result);
    }

    // ------------------------------------------------------------------ 확정

    @Transactional
    public PathologyResultSummaryDto confirmResult(String resultId, String confirmedById) {

        PathologyResultEntity result = findDetailOrThrow(resultId);
        if (STATUS_CONFIRMED.equals(result.getResultStatusCode())) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB041, "이미 확정된 결과입니다. (확정일시=" + result.getConfirmedAt() + ")");
        }

        validateCode(RESULT_STATUS_CD, STATUS_CONFIRMED, "결과상태코드");

        if (forbidSelfConfirm && confirmedById != null && confirmedById.equals(result.getRecordedById())) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB068,
                    "결과 입력자와 확정자가 같습니다. 다른 담당자가 확정해야 합니다. (pathologyResultId=" + resultId + ")");
        }

        result.confirm(STATUS_CONFIRMED, confirmedById, LocalDateTime.now());
        // 청구 요청 (항목당 1회, 5차 Phase 5)
        billingChargeService.requestLabCharge(result.getLabOrderItem());
        // 결과 전송 (5차 Phase 6)
        labResultTransmissionService.transmitPathology(result);
        return toResponse(result);
    }

    // ------------------------------------------------------------------ 조회

    @Transactional(readOnly = true)
    public PathologyResultSummaryDto getResult(String resultId) {
        return toResponse(findDetailOrThrow(resultId));
    }

    /** 접수의 병리 결과 목록 (병리 항목마다 0~1건). 0건이 정상인 조회라 예외를 던지지 않는다. */
    @Transactional(readOnly = true)
    public List<PathologyResultSummaryDto> getResultsByReceptionNo(String receptionNo) {
        List<String> pathologyItemIds = labOrderItemRepository.findByReceptionNo(receptionNo).stream()
                .filter(item -> labResultTypeResolver.resolve(item.getLabItemCode()) == LabResultType.PATHOLOGY)
                .map(LabOrderItemEntity::getLabOrderItemId)
                .toList();
        if (pathologyItemIds.isEmpty()) {
            return List.of();
        }
        return pathologyResultRepository.findByItemIds(pathologyItemIds).stream()
                .map(this::toResponse)
                .toList();
    }

    /** 첨부 다운로드 (ZP2-98 미리보기용). 첨부가 없으면 LAB086. */
    @Transactional(readOnly = true)
    public Attachment downloadAttachment(String resultId) {
        PathologyResultEntity result = findDetailOrThrow(resultId);
        String key = result.getAttachmentFileKey();
        if (key == null) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB086, "첨부 파일이 없습니다. (pathologyResultId=" + resultId + ")");
        }
        return new Attachment(fileNameOf(key), contentTypeOf(key), seaweedFsFileStorage.download(key));
    }

    public record Attachment(String fileName, String contentType, byte[] content) {
    }

    // ------------------------------------------------------------------ 내부

    /**
     * 첨부가 있으면 형식을 확인하고 올린다. 없으면 null.
     * ⚠ 올리자마자 "롤백되면 지운다"를 건다. 이후 어디서 실패하든(커밋 시점 포함) 고아 파일이 남지 않는다.
     */
    private String uploadIfPresent(String labOrderItemId, MultipartFile file) {
        if (file == null) {
            return null;
        }
        if (file.isEmpty()) {
            throw new LabImagingBusinessException(LabMessageCode.LAB998, "첨부 파일이 비어 있습니다.");
        }
        if (file.getContentType() == null || !ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB087,
                    "허용되지 않는 첨부 파일 형식입니다. (contentType=" + file.getContentType() + ", 허용=jpg/png/pdf)");
        }

        String key = seaweedFsFileStorage.upload(ATTACHMENT_PATH_PREFIX, labOrderItemId, file);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    seaweedFsFileStorage.deleteQuietly(key);
                }
            }
        });
        return key;
    }

    private void validateCodes(String pathologyTypeCode, String diagnosisCode) {
        validateCode(PATHOLOGY_TYPE_CD, pathologyTypeCode, "병리유형코드");
        if (diagnosisCode != null && !diagnosisCode.isBlank()) {
            validateCode(PATHOLOGY_DIAGNOSIS_CD, diagnosisCode, "병리진단명코드");
        }
    }

    private PathologyResultEntity findDetailOrThrow(String resultId) {
        return pathologyResultRepository.findDetailById(resultId)
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB082, "병리 검사 결과를 찾을 수 없습니다. (pathologyResultId=" + resultId + ")"));
    }

    private PathologyResultSummaryDto toResponse(PathologyResultEntity r) {
        String key = r.getAttachmentFileKey();
        return PathologyResultSummaryDto.builder()
                .pathologyResultId(r.getPathologyResultId())
                .labOrderItemId(r.getLabOrderItem().getLabOrderItemId())
                .labItemCode(r.getLabOrderItem().getLabItemCode())
                .pathologyTypeCode(r.getPathologyTypeCode())
                .diagnosisCode(r.getDiagnosisCode())
                .findings(r.getFindings())
                .attachmentYn(key == null ? "N" : "Y")
                .attachmentFileName(key == null ? null : fileNameOf(key))
                .attachmentContentType(key == null ? null : contentTypeOf(key))
                .resultStatusCode(r.getResultStatusCode())
                .recordedAt(r.getRecordedAt())
                .recordedById(r.getRecordedById())
                .confirmedAt(r.getConfirmedAt())
                .confirmedById(r.getConfirmedById())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    /**
     * 저장 키(…/{referenceId}/{uuid}_{원본파일명})에서 원본 파일명을 되살린다.
     * ⚠ 파일명 컬럼이 없어 키에서 꺼낸다. uuid 는 36자이고 뒤에 "_" 가 붙는다(SeaweedFsFileStorage.upload).
     */
    static String fileNameOf(String key) {
        String last = key.substring(key.lastIndexOf('/') + 1);
        int underscore = last.indexOf('_');
        return underscore >= 0 ? last.substring(underscore + 1) : last;
    }

    /** 확장자로 콘텐츠타입을 정한다(허용 형식 3가지뿐이라 확장자로 충분). 모르면 octet-stream. */
    static String contentTypeOf(String key) {
        int dot = key.lastIndexOf('.');
        String ext = dot >= 0 ? key.substring(dot + 1).toLowerCase() : "";
        return CONTENT_TYPE_BY_EXTENSION.getOrDefault(ext, "application/octet-stream");
    }

    private void validateCode(String groupCode, String code, String fieldLabel) {
        if (!commonCodeCache.isValid(groupCode, code)) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB017, "유효하지 않은 " + fieldLabel + "입니다. (" + groupCode + "=" + code + ")");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
