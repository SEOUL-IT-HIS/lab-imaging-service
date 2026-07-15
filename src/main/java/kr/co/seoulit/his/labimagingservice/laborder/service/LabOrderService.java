package kr.co.seoulit.his.labimagingservice.laborder.service;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.exception.DuplicateOrderException;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderCreateResponseDto;
import kr.co.seoulit.his.labimagingservice.laborder.dto.LabOrderItemRequestDto;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabReceptionEntity;
import kr.co.seoulit.his.labimagingservice.laborder.mapper.LabOrderMapper;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 검사 오더 접수 서비스
 * 대응 유스케이스: UC-SPC-01 검사오더접수 (Jira ZP2-12)
 *
 * ⚠ 타 서비스 연동(PatientServiceClient, AdminCommonCodeClient)은 아직 호출하지 않습니다.
 *   실제 연동 시점에 검증 로직을 추가해야 하는 지점을 TODO로 표시해뒀습니다.
 */
@Service
@RequiredArgsConstructor
public class LabOrderService {

    private final LabOrderRepository labOrderRepository;
    private final LabOrderMapper labOrderMapper;

    // TODO: 실제 연동 시점에 주입하여 사용
    // private final PatientServiceClient patientServiceClient;
    // private final AdminCommonCodeClient adminCommonCodeClient;

    @Transactional
    public LabOrderCreateResponseDto createOrder(LabOrderCreateRequestDto request) {

        // TODO: (Patient Service 연동 시점) patientServiceClient.validatePatient(request.getPatientNo())
        //       유효하지 않은 환자번호면 LabImagingBusinessException(LAB003, ...) 발생

        // TODO: (Admin 공통코드 연동 시점) adminCommonCodeClient.isValidCode("TREAT_TYPE_CD", request.getTreatTypeCode())
        //       systemCode, treatTypeCode 등의 코드값 유효성 검증

        // 중복 접수 방지: 오더번호(lab_order_no) UNIQUE 위반을 사전에 확인
        if (labOrderRepository.existsByLabOrderNo(request.getLabOrderNo())) {
            throw new DuplicateOrderException(
                    LabMessageCode.LAB004,
                    "이미 접수된 오더입니다. (labOrderNo=" + request.getLabOrderNo() + ")"
            );
        }

        LabOrderEntity labOrder = LabOrderEntity.builder()
                .labOrderNo(request.getLabOrderNo())
                .systemCode(request.getSystemCode())
                .patientNo(request.getPatientNo())
                .physicianNo(request.getPhysicianNo())
                .treatTypeCode(request.getTreatTypeCode())
                .urgencyYn(request.getUrgencyYn())
                .orderStatusCode("RECEIVED") // TODO: 공통코드 확정 후 상수/코드 테이블 참조로 교체
                .receivedAt(LocalDateTime.now())
                .build();

        for (LabOrderItemRequestDto itemRequest : request.getOrderItems()) {
            LabOrderItemEntity item = LabOrderItemEntity.builder()
                    .labItemCode(itemRequest.getLabItemCode())
                    .itemStatusCode("REGISTERED") // TODO: 공통코드 확정 후 교체
                    .build();
            labOrder.addOrderItem(item);
        }

        // 오더 접수 = 접수(LAB_RECEPTION) 동시 생성 (0단계 스코프 확정 사항)
        LabReceptionEntity reception = LabReceptionEntity.builder()
                .receptionNo(generateReceptionNo()) // TODO: 실제 채번 규칙 확정 필요 (현재는 임시 로직)
                .patientNo(request.getPatientNo())
                .receptionStatusCode("ACCEPTED") // TODO: 공통코드 확정 후 교체
                .urgencyYn(request.getUrgencyYn())
                .receivedById(request.getReceivedById())
                .ackSentYn("N")
                .ackSentAt(null)
                .build();
        labOrder.addReception(reception);

        LabOrderEntity saved = labOrderRepository.save(labOrder);
        LabReceptionEntity savedReception = saved.getReceptions().get(0);

        return labOrderMapper.toResponse(saved, savedReception);
    }

    /**
     * ⚠ 임시 채번 로직입니다. 실제 접수번호 채번 규칙(부서별 접두어, 일자별 시퀀스 등)이
     *   정해지면 반드시 교체해야 합니다.
     */
    private String generateReceptionNo() {
        return "LR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
