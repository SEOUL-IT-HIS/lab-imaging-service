package kr.co.seoulit.his.labimagingservice.imagingacquisition.service;

import kr.co.seoulit.his.labimagingservice.billing.service.BillingChargeService;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.common.storage.SeaweedFsFileStorage;
import kr.co.seoulit.his.labimagingservice.imagingacquisition.dto.ImageFileUploadRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingacquisition.mapper.ImageFileMapper;
import kr.co.seoulit.his.labimagingservice.imagingacquisition.repository.ImageFileRepository;
import kr.co.seoulit.his.labimagingservice.imagingacquisition.dto.ImageFileSummaryDto;
import kr.co.seoulit.his.labimagingservice.imagingacquisition.entity.ImageFileEntity;
import kr.co.seoulit.his.labimagingservice.imagingconsent.repository.ConsentRepository;
import kr.co.seoulit.his.labimagingservice.imagingconsent.service.ConsentRequirementPolicy;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageOrderEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.entity.ImageReceptionEntity;
import kr.co.seoulit.his.labimagingservice.imagingorder.repository.ImageReceptionRepository;
import kr.co.seoulit.his.labimagingservice.imagingschedule.entity.ImageScheduleEntity;
import kr.co.seoulit.his.labimagingservice.imagingschedule.repository.ImageScheduleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 영상파일 업로드 사전검증 — 파일 크기 상한(LAB109, 04번 지시서 Phase 3-D).
 *
 * ⚠ 파일 크기 검사는 접수/항목/동의/일정 조회보다 먼저 일어난다(ImageFileService.
 *   validateAcquisitionPrerequisites 순서 참고) — 그래서 이 테스트는 그 뒤 의존성(접수 조회 등)을
 *   목(mock)할 필요 없이, 빈 레포지토리로도 파일 검사 세 가지(빈 파일/형식/크기)를 확인할 수 있다.
 */
class ImageFileServiceTest {

    private static final long IMAGE_MAX_BYTES = 8_388_608L;

    private final ImageFileRepository imageFileRepository = mock(ImageFileRepository.class);
    private final ImageReceptionRepository imageReceptionRepository = mock(ImageReceptionRepository.class);
    private final ImageScheduleRepository imageScheduleRepository = mock(ImageScheduleRepository.class);
    private final ConsentRepository consentRepository = mock(ConsentRepository.class);
    private final ConsentRequirementPolicy consentRequirementPolicy = mock(ConsentRequirementPolicy.class);
    private final ImageFileMapper imageFileMapper = mock(ImageFileMapper.class);
    private final SeaweedFsFileStorage seaweedFsFileStorage = mock(SeaweedFsFileStorage.class);
    private final BillingChargeService billingChargeService = mock(BillingChargeService.class);

    private final ImageFileService service = new ImageFileService(
            imageFileRepository,
            imageReceptionRepository,
            imageScheduleRepository,
            consentRepository,
            consentRequirementPolicy,
            imageFileMapper,
            seaweedFsFileStorage,
            billingChargeService,
            IMAGE_MAX_BYTES);

    private static ImageFileUploadRequestDto request(MockMultipartFile file) {
        return ImageFileUploadRequestDto.builder()
                .imageReceptionId("rec-1")
                .imageOrderItemId("item-1")
                .patientId("patient-1")
                .file(file)
                .build();
    }

    @Test
    @DisplayName("빈 파일(0바이트)은 LAB998로 거절한다")
    void emptyFileRejected() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.dcm", "application/dicom", new byte[0]);

        assertThatThrownBy(() -> service.uploadImageFile(request(empty)))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB998);
    }

    @Test
    @DisplayName("허용되지 않은 형식은 LAB054로 거절한다")
    void badContentTypeRejected() {
        MockMultipartFile exe = new MockMultipartFile("file", "a.exe", "application/octet-stream", new byte[]{1});

        assertThatThrownBy(() -> service.uploadImageFile(request(exe)))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB054);
    }

    @Test
    @DisplayName("파일 크기가 상한을 넘으면 LAB109로 거절한다")
    void tooLargeRejected() {
        MockMultipartFile tooLarge = new MockMultipartFile(
                "file", "a.dcm", "application/dicom", new byte[(int) (IMAGE_MAX_BYTES + 1)]);

        assertThatThrownBy(() -> service.uploadImageFile(request(tooLarge)))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB109);
    }

    @Test
    @DisplayName("파일 크기가 상한과 같으면(경계값) 통과하고 그 다음 검증(접수 조회)으로 넘어간다")
    void atLimitPassesFileChecks() {
        MockMultipartFile atLimit = new MockMultipartFile(
                "file", "a.dcm", "application/dicom", new byte[(int) IMAGE_MAX_BYTES]);

        // 접수 레포지토리가 빈 Optional 이라 LAB015(접수 못 찾음)로 넘어간다 — 파일 검사는 이미 통과했다는 뜻.
        assertThatThrownBy(() -> service.uploadImageFile(request(atLimit)))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB015);
    }

    // ==================================================================
    // 동의 필요 여부 (06번 지시서 Phase 3 — ConsentRequirementPolicy.mayAcquire)
    // ==================================================================

    private static final String PATIENT_ID = "patient-1";

    /**
     * 접수·오더·촬영항목을 하나로 묶어 둔다. 일정도 이미 등록된 상태로 만든다(LAB053 전에 멈추지 않도록).
     * ⚠ imageOrderItemId 는 @PrePersist 로만 채워져 순수 단위 테스트에서는 null 로 남는다.
     *   findOrderItemOfReception 이 이 값으로 항목을 찾으므로(getImageOrderItemId().equals(...)),
     *   null 이면 그 비교 자체에서 NPE 가 난다 — ReflectionTestUtils 로 고정값을 직접 넣어 피한다.
     */
    private ImageReceptionEntity readyReception(String imageItemCode) {
        ImageOrderEntity order = ImageOrderEntity.builder().patientId(PATIENT_ID).build();
        ReflectionTestUtils.setField(order, "imageOrderId", "order-1"); // 아래 설명과 같은 이유(PrePersist 미실행)
        ImageOrderItemEntity item = ImageOrderItemEntity.builder().imageItemCode(imageItemCode).build();
        ReflectionTestUtils.setField(item, "imageOrderItemId", "item-1");
        order.addOrderItem(item);
        ImageReceptionEntity reception = ImageReceptionEntity.builder().build();
        order.addReception(reception);

        when(imageReceptionRepository.findById("rec-1")).thenReturn(Optional.of(reception));
        when(imageScheduleRepository
                .findByImageReception_ImageReceptionIdAndImageOrderItem_ImageOrderItemIdAndLatestYn(
                        anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(mock(ImageScheduleEntity.class)));
        when(consentRepository.findOrderIdsWithValidConsent(any())).thenReturn(List.of());

        return reception;
    }

    private static ImageFileUploadRequestDto requestFor(ImageOrderItemEntity item, MockMultipartFile file) {
        return ImageFileUploadRequestDto.builder()
                .imageReceptionId("rec-1")
                .imageOrderItemId(item.getImageOrderItemId())
                .patientId(PATIENT_ID)
                .file(file)
                .build();
    }

    private static MockMultipartFile dicomFile() {
        return new MockMultipartFile("file", "a.dcm", "application/dicom", new byte[]{1, 2, 3});
    }

    @Test
    @DisplayName("동의가 필요 없는 항목(LISTED 모드, 목록 밖)은 동의 없이도 업로드가 성공한다")
    void uploadSucceedsWithoutConsentWhenNotRequired() {
        ImageReceptionEntity reception = readyReception("04"); // X-RAY, 가정상 동의 불필요
        ImageOrderItemEntity item = reception.getImageOrder().getOrderItems().get(0);
        when(consentRequirementPolicy.mayAcquire("04", false)).thenReturn(true);
        when(seaweedFsFileStorage.upload(anyString(), anyString(), any())).thenReturn("/image-files/key1");
        when(imageFileRepository.save(any(ImageFileEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(imageFileMapper.toResponse(any())).thenReturn(mock(ImageFileSummaryDto.class));

        service.uploadImageFile(requestFor(item, dicomFile()));

        assertThat(item.getItemStatusCode()).isEqualTo("ACQUIRED");
    }

    @Test
    @DisplayName("동의가 필요한 항목에 유효 동의가 없으면 LAB052 로 거절하고 업로드하지 않는다")
    void uploadRejectedWithoutConsentWhenRequired() {
        ImageReceptionEntity reception = readyReception("01"); // CT, 가정상 동의 필요
        ImageOrderItemEntity item = reception.getImageOrder().getOrderItems().get(0);
        when(consentRequirementPolicy.mayAcquire("01", false)).thenReturn(false);

        assertThatThrownBy(() -> service.uploadImageFile(requestFor(item, dicomFile())))
                .isInstanceOf(LabImagingBusinessException.class)
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB052);
        verify(imageFileRepository, never()).save(any());
    }

    @Test
    @DisplayName("동의가 필요한 항목이어도 유효 동의(Y, 미철회)가 있으면 업로드가 성공한다")
    void uploadSucceedsWhenValidConsentExists() {
        ImageReceptionEntity reception = readyReception("01");
        ImageOrderItemEntity item = reception.getImageOrder().getOrderItems().get(0);
        String orderId = reception.getImageOrder().getImageOrderId();
        when(consentRepository.findOrderIdsWithValidConsent(any())).thenReturn(List.of(orderId));
        when(consentRequirementPolicy.mayAcquire("01", true)).thenReturn(true);
        when(seaweedFsFileStorage.upload(anyString(), anyString(), any())).thenReturn("/image-files/key2");
        when(imageFileRepository.save(any(ImageFileEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(imageFileMapper.toResponse(any())).thenReturn(mock(ImageFileSummaryDto.class));

        service.uploadImageFile(requestFor(item, dicomFile()));

        assertThat(item.getItemStatusCode()).isEqualTo("ACQUIRED");
    }
}
