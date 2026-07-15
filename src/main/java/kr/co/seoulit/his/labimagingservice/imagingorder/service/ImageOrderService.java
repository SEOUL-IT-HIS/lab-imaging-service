package kr.co.seoulit.his.labimagingservice.imagingorder.service;

import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.exception.DuplicateOrderException;
import kr.co.seoulit.his.labimagingservice.imagingorder.dto.ImageOrderCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingorder.dto.ImageOrderCreateResponseDto;
import kr.co.seoulit.his.labimagingservice.imagingorder.dto.ImageOrderItemRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageOrderEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageReceptionEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.mapper.ImageOrderMapper;
import kr.co.seoulit.his.labimagingservice.imagingorder.repository.ImageOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 영상 오더 접수 서비스
 * 대응 유스케이스: UC-IMG-01 영상오더접수 (Jira ZP2-19)
 *
 * ⚠ 타 서비스 연동은 아직 호출하지 않습니다. (LabOrderService와 동일한 상태 — 상세 사유는 그쪽 주석 참고)
 */
@Service
@RequiredArgsConstructor
public class ImageOrderService {

    private final ImageOrderRepository imageOrderRepository;
    private final ImageOrderMapper imageOrderMapper;

    // TODO: 실제 연동 시점에 주입하여 사용
    // private final PatientServiceClient patientServiceClient;
    // private final AdminCommonCodeClient adminCommonCodeClient;

    @Transactional
    public ImageOrderCreateResponseDto createOrder(ImageOrderCreateRequestDto request) {

        // TODO: (Patient Service 연동 시점) patientServiceClient.validatePatient(request.getPatientNo())
        // TODO: (Admin 공통코드 연동 시점) adminCommonCodeClient.isValidCode(...)

        if (imageOrderRepository.existsByImageOrderNo(request.getImageOrderNo())) {
            throw new DuplicateOrderException(
                    LabMessageCode.LAB008,
                    "이미 접수된 오더입니다. (imageOrderNo=" + request.getImageOrderNo() + ")"
            );
        }

        ImageOrderEntity imageOrder = ImageOrderEntity.builder()
                .imageOrderNo(request.getImageOrderNo())
                .systemCode(request.getSystemCode())
                .patientNo(request.getPatientNo())
                .physicianNo(request.getPhysicianNo())
                .treatTypeCode(request.getTreatTypeCode())
                .urgencyYn(request.getUrgencyYn())
                .orderStatusCode("RECEIVED") // TODO: 공통코드 확정 후 교체
                .receivedAt(LocalDateTime.now())
                .build();

        for (ImageOrderItemRequestDto itemRequest : request.getOrderItems()) {
            ImageOrderItemEntity item = ImageOrderItemEntity.builder()
                    .imageItemCode(itemRequest.getImageItemCode())
                    .itemStatusCode("REGISTERED") // TODO: 공통코드 확정 후 교체
                    .build();
            imageOrder.addOrderItem(item);
        }

        ImageReceptionEntity reception = ImageReceptionEntity.builder()
                .receptionNo(generateReceptionNo()) // TODO: 실제 채번 규칙 확정 필요
                .patientNo(request.getPatientNo())
                .receptionStatusCode("ACCEPTED") // TODO: 공통코드 확정 후 교체
                .urgencyYn(request.getUrgencyYn())
                .receivedById(request.getReceivedById())
                .ackSentYn("N")
                .ackSentAt(null)
                .build();
        imageOrder.addReception(reception);

        ImageOrderEntity saved = imageOrderRepository.save(imageOrder);
        ImageReceptionEntity savedReception = saved.getReceptions().get(0);

        return imageOrderMapper.toResponse(saved, savedReception);
    }

    /**
     * ⚠ 임시 채번 로직입니다. 실제 접수번호 채번 규칙이 정해지면 반드시 교체해야 합니다.
     */
    private String generateReceptionNo() {
        return "IR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
