package kr.co.seoulit.his.labimagingservice.labspecimen.service;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabReceptionEntity;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabReceptionRepository;
import kr.co.seoulit.his.labimagingservice.labspecimen.dto.SpecimenCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.labspecimen.dto.SpecimenSummaryDto;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.FitnessStatus;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenAcceptanceEntity;
import kr.co.seoulit.his.labimagingservice.labspecimen.entity.SpecimenEntity;
import kr.co.seoulit.his.labimagingservice.labspecimen.mapper.SpecimenMapper;
import kr.co.seoulit.his.labimagingservice.labspecimen.repository.SpecimenAcceptanceRepository;
import kr.co.seoulit.his.labimagingservice.labspecimen.repository.SpecimenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 검체 식별관리 서비스
 * 대응 유스케이스: UC-SPC-03 검체식별관리 (Jira ZP2-7)
 *
 * ⚠ Service 인터페이스 없이 클래스로 바로 구현한다 (SpecimenServiceImpl 형태 아님).
 *   사유는 LabOrderService 주석 참고.
 *
 * ── 구현 현황
 *   ZP2-68 검체 채취정보 등록      : 완료 (createSpecimen)
 *   ZP2-66 필수값/유효성 검증      : 완료 (DTO Bean Validation + 검체용기코드 공통코드 검증)
 *   ZP2-65 검체 바코드 발행        : 완료 (generateSpecimenBarcode — 중복 확인 포함)
 *   ZP2-75 검체 바코드 검증        : 완료 (getSpecimenByBarcode)
 *   ZP2-79 검체 목록 조회          : 완료. 다만 화면은 접수별 목록만 쓴다.
 *                                   판정여부 필터는 후반 이력 화면용 — getSpecimens 주석 참고
 *
 * ⚠ 환자 검증(PatientServiceBusinessDelegate.validatePatient)은 호출하지 않는다.
 *   검체는 이미 접수된 건에 붙는 것이고, 그 접수를 만들 때 환자ID를 이미 검증했다.
 */
@Service
@RequiredArgsConstructor
public class SpecimenService {

    /** 바코드 채번 재시도 상한. 이 횟수를 넘길 확률은 사실상 0이지만, 무한 루프를 막으려고 둔다. */
    private static final int BARCODE_GENERATION_MAX_ATTEMPTS = 5;

    private final SpecimenRepository specimenRepository;
    private final SpecimenAcceptanceRepository specimenAcceptanceRepository;
    private final CommonCodeCache commonCodeCache;
    private final SpecimenMapper specimenMapper;
    private final LabReceptionRepository labReceptionRepository;

    @Transactional
    public SpecimenSummaryDto createSpecimen(SpecimenCreateRequestDto request) {

        LabReceptionEntity reception = labReceptionRepository.findById(request.getLabReceptionId())
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB013, "검사 접수 정보를 찾을 수 없습니다."));

        validateCode("SPECIMEN_CONTAINER_CD", request.getSpecimenContainerCode(), "검체용기코드");

        SpecimenEntity specimen = SpecimenEntity.builder()
                .specimenBarcode(generateSpecimenBarcode())
                .specimenContainerCode(request.getSpecimenContainerCode())
                .specimenTypeCode(request.getSpecimenType())
                .patientId(request.getPatientId())
                .collectedAt(request.getCollectedAt())
                .collectedById(request.getCollectedById())
                .build();
        specimen.assignLabReception(reception);
        SpecimenEntity saved = specimenRepository.save(specimen);
        // 방금 만든 검체라 판정이 있을 수 없다.
        return specimenMapper.toResponse(saved, null);

    }

    // ------ 검체 목록 조회 ------
    /**
     * 검체 목록.
     *
     * @param judgedYn    "N"=미판정(판정 대상), "Y"=판정완료, null=전체
     *                    TODO(ZP2-79, 후반 작업): 화면에서 아직 보내지 않는다. 검체 이력 조회 화면이 생기면 쓴다.
     *                    사유는 SpecimenRepository 의 같은 TODO 참고.
     * @param receptionNo 접수번호. 값이 있으면 그 접수의 검체만 반환하고 judgedYn 은 무시한다.
     *                    (워크리스트 오른쪽 작업 폼에서 "이 접수의 검체"를 보여줄 때 쓴다)
     *
     * - 조회 전용이라 @Transactional(readOnly = true).
     * - 결과 0건은 정상적인 빈 목록이므로 예외를 던지지 않고 [] 를 그대로 반환한다.
     *   (단건 조회는 "못 찾음 = 예외"가 맞지만, 목록은 빈 결과가 정상 케이스다.)
     * - 필터 조건과 N+1(join fetch) 방어 설명은 SpecimenRepository 주석 참고.
     *
     * ⚠ 적합상태(fitnessStatus)는 검체 목록을 먼저 뽑은 뒤 IN 절로 한 번에 조회해 붙인다.
     *   검체마다 판정을 조회하면 행 수만큼 쿼리가 나간다(N+1).
     *   (접수 목록의 scheduledAt 과 같은 방식)
     *
     * TODO(후속): 접수번호/채취일자/검체종류 등 검색조건, 페이지네이션(Pageable) 필요 시 확장.
     */
    @Transactional(readOnly = true)
    public List<SpecimenSummaryDto> getSpecimens(String judgedYn, String receptionNo) {
        List<SpecimenEntity> specimens = findSpecimensBy(judgedYn, receptionNo);
        Map<String, FitnessStatus> fitnessBySpecimenId = findFitnessStatus(specimens);

        return specimens.stream()
                .map(specimen -> specimenMapper.toResponse(
                        specimen,
                        fitnessBySpecimenId.get(specimen.getSpecimenId())))
                .toList();
    }

    /**
     * 조회 조건에 따라 조회 메서드를 고른다.
     * 접수번호가 있으면 그 접수의 검체가 답이므로 판정여부 필터를 볼 필요가 없다.
     */
    private List<SpecimenEntity> findSpecimensBy(String judgedYn, String receptionNo) {
        if (receptionNo != null && !receptionNo.isBlank()) {
            return specimenRepository.findByLabReception_ReceptionNoOrderByCreatedAtAsc(receptionNo);
        }
        if ("Y".equals(judgedYn)) {
            return specimenRepository.findJudgedWithLabReception();
        }
        if ("N".equals(judgedYn)) {
            return specimenRepository.findUnjudgedWithLabReception();
        }
        return specimenRepository.findAllWithLabReception();
    }

    /**
     * 검체ID → 적합상태 맵. 판정이 없는 검체는 맵에 키가 없다(= null 로 응답).
     * 검체가 0건이면 IN 절 자체가 무의미하므로 쿼리를 보내지 않는다.
     */
    private Map<String, FitnessStatus> findFitnessStatus(List<SpecimenEntity> specimens) {
        if (specimens.isEmpty()) {
            return Map.of();
        }
        // ⚠ Xxx::method = 메서드 참조. s -> s.getSpecimenId() 와 같다. (LabOrderService 주석 참고)
        List<String> specimenIds = specimens.stream()
                .map(SpecimenEntity::getSpecimenId)
                .toList();

        return specimenAcceptanceRepository.findBySpecimen_SpecimenIdIn(specimenIds).stream()
                .collect(Collectors.toMap(
                        acceptance -> acceptance.getSpecimen().getSpecimenId(),
                        SpecimenAcceptanceEntity::getFitnessStatusCode));
    }

    // ------ 검체 단건 조회 ------
    @Transactional(readOnly = true)
    public SpecimenSummaryDto getSpecimenById(String specimenId) {
        SpecimenEntity specimen = specimenRepository.findById(specimenId)
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB020,
                        "등록된 검체 정보를 찾을 수 없습니다. (specimenId=" + specimenId + ")"
                ));

        // 목록과 같은 정보를 보여주기 위해 적합상태도 채운다. (미판정이면 null)
        FitnessStatus fitnessStatus = specimenAcceptanceRepository
                .findBySpecimen_SpecimenId(specimenId)
                .map(SpecimenAcceptanceEntity::getFitnessStatusCode)
                .orElse(null);

        return specimenMapper.toResponse(specimen, fitnessStatus);
    }

    /**
     * 검체바코드로 단건 조회. (ZP2-75 바코드 검증)
     *
     * ⚠ 조회만 한다. "지금 선택한 접수의 검체인지" 대조는 서버가 하지 않는다.
     *   서버는 화면이 어느 접수를 보고 있는지 모른다. 그 값을 파라미터로 받아 비교해서 돌려주는 건
     *   화면이 이미 아는 값을 왕복시키는 것이다. 대조는 응답의 receptionNo 로 화면에서 한다.
     *   (SpecimenSummaryDto 가 receptionNo 와 fitnessStatus 를 이미 담고 있어 추가 조회가 필요 없다)
     *
     * ⚠ 환자·오더 일치 여부를 따로 확인하지 않는다.
     *   SPECIMEN → LAB_RECEPTION → LAB_ORDER → patient_id 로 이어지므로
     *   접수번호가 같으면 환자와 오더도 같다. 접수 대조가 곧 환자·오더 대조다.
     *
     * 적합상태를 채우는 방식은 getSpecimenById 와 같다. (미판정이면 null)
     */
    @Transactional(readOnly = true)
    public SpecimenSummaryDto getSpecimenByBarcode(String specimenBarcode) {
        SpecimenEntity specimen = specimenRepository.findBySpecimenBarcode(specimenBarcode)
                .orElseThrow(() -> new LabImagingBusinessException(
                        LabMessageCode.LAB020,
                        "해당 바코드의 검체를 찾을 수 없습니다. (barcode=" + specimenBarcode + ")"
                ));

        FitnessStatus fitnessStatus = specimenAcceptanceRepository
                .findBySpecimen_SpecimenId(specimen.getSpecimenId())
                .map(SpecimenAcceptanceEntity::getFitnessStatusCode)
                .orElse(null);

        return specimenMapper.toResponse(specimen, fitnessStatus);
    }

    private void validateCode(String groupCode, String code, String fieldLabel) {
        if (!commonCodeCache.isValid(groupCode, code)) {
            throw new LabImagingBusinessException(
                    LabMessageCode.LAB017,
                    "유효하지 않은 " + fieldLabel + "입니다. (" + groupCode + "=" + code + ")"
            );
        }
    }

    /**
     * 검체바코드를 채번한다. "SP-" + UUID 앞 8자리(대문자).
     *
     * ⚠ specimen_barcode 에 UNIQUE 제약이 있다. 중복 확인 없이 저장하면 충돌했을 때
     *   DataIntegrityViolationException 이 LAB999(500)로 나가, 담당자에게는 원인을 알 수 없는
     *   오류로 보인다. 바코드로 검체를 지목하는 기능이 생긴 이상 그대로 둘 수 없다. (ZP2-75)
     *
     * ⚠ 이 확인만으로 완전하지는 않다. exists 확인과 저장 사이에 다른 트랜잭션이 같은 값을
     *   넣을 수 있다. 최종 방어선은 DB 의 UNIQUE 제약이고, 이 루프는 충돌 확률을 낮추는 것이다.
     *   (UUID 앞 8자리는 16^8 ≈ 43억 가지라 실제 충돌은 극히 드물다)
     *
     * ⚠ 상한을 넘긴 경우는 LabImagingBusinessException 으로 던지지 않는다.
     *   그 예외는 GlobalExceptionHandler 에서 무조건 400 이라, 서버 사정으로 실패한 것을
     *   "요청이 잘못됐다"로 응답하게 된다. 일반 예외로 던져 500 + LAB999 가 나가게 한다.
     */
    private String generateSpecimenBarcode() {
        for (int attempt = 0; attempt < BARCODE_GENERATION_MAX_ATTEMPTS; attempt++) {
            String barcode = "SP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            if (!specimenRepository.existsBySpecimenBarcode(barcode)) {
                return barcode;
            }
        }
        throw new IllegalStateException(
                "검체바코드 채번에 실패했습니다. (재시도 " + BARCODE_GENERATION_MAX_ATTEMPTS + "회 초과)");
    }


}