package kr.co.seoulit.his.labimagingservice.imagingacquisition.service;

import kr.co.seoulit.his.labimagingservice.billing.service.BillingChargeService;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.exception.LabImagingBusinessException;
import kr.co.seoulit.his.labimagingservice.common.storage.SeaweedFsFileStorage;
import kr.co.seoulit.his.labimagingservice.imagingacquisition.dto.ImageFileUploadRequestDto;
import kr.co.seoulit.his.labimagingservice.imagingacquisition.mapper.ImageFileMapper;
import kr.co.seoulit.his.labimagingservice.imagingacquisition.repository.ImageFileRepository;
import kr.co.seoulit.his.labimagingservice.imagingconsent.repository.ConsentRepository;
import kr.co.seoulit.his.labimagingservice.imagingconsent.service.ConsentRequirementPolicy;
import kr.co.seoulit.his.labimagingservice.imagingorder.repository.ImageReceptionRepository;
import kr.co.seoulit.his.labimagingservice.imagingschedule.repository.ImageScheduleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 영상파일 업로드 사전검증 — 파일 크기 상한(LAB109, 04번 지시서 Phase 3-D).
 *
 * ⚠ 파일 크기 검사는 접수/항목/동의/일정 조회보다 먼저 일어난다(ImageFileService.
 *   validateAcquisitionPrerequisites 순서 참고) — 그래서 이 테스트는 그 뒤 의존성(접수 조회 등)을
 *   목(mock)할 필요 없이, 빈 레포지토리로도 파일 검사 세 가지(빈 파일/형식/크기)를 확인할 수 있다.
 */
class ImageFileServiceTest {

    private static final long IMAGE_MAX_BYTES = 8_388_608L;

    private final ImageFileService service = new ImageFileService(
            mock(ImageFileRepository.class),
            mock(ImageReceptionRepository.class),
            mock(ImageScheduleRepository.class),
            mock(ConsentRepository.class),
            mock(ConsentRequirementPolicy.class),
            mock(ImageFileMapper.class),
            mock(SeaweedFsFileStorage.class),
            mock(BillingChargeService.class),
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
}
