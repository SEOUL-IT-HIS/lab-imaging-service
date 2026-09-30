package kr.co.seoulit.his.labimagingservice.labresult.pathology.service;

import kr.co.seoulit.his.labimagingservice.billing.service.BillingChargeService;
import kr.co.seoulit.his.labimagingservice.labresult.service.LabResultTransmissionService;
import kr.co.seoulit.his.labimagingservice.common.LabMessageCode;
import kr.co.seoulit.his.labimagingservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.labimagingservice.common.storage.SeaweedFsFileStorage;
import kr.co.seoulit.his.labimagingservice.laborder.entity.LabOrderItemEntity;
import kr.co.seoulit.his.labimagingservice.laborder.repository.LabOrderItemRepository;
import kr.co.seoulit.his.labimagingservice.labresult.pathology.dto.PathologyResultCreateRequestDto;
import kr.co.seoulit.his.labimagingservice.labresult.pathology.dto.PathologyResultUpdateRequestDto;
import kr.co.seoulit.his.labimagingservice.labresult.pathology.entity.PathologyResultEntity;
import kr.co.seoulit.his.labimagingservice.labresult.pathology.repository.PathologyResultRepository;
import kr.co.seoulit.his.labimagingservice.labresult.type.LabResultType;
import kr.co.seoulit.his.labimagingservice.labresult.type.LabResultTypeResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 병리 결과 등록 규칙 + 첨부 일관성 (UC-RST-03, 5차 Phase 4).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PathologyResultServiceTest {

    @Mock PathologyResultRepository pathologyResultRepository;
    @Mock LabOrderItemRepository labOrderItemRepository;
    @Mock CommonCodeCache commonCodeCache;
    @Mock SeaweedFsFileStorage storage;
    @Mock BillingChargeService billingChargeService;
    @Mock LabResultTransmissionService labResultTransmissionService;

    PathologyResultService service;
    LabOrderItemEntity item;

    private final MockMultipartFile png = new MockMultipartFile("file", "slide.png", "image/png", new byte[]{1});

    @BeforeEach
    void setUp() {
        // 트랜잭션 없이 도는 단위 테스트라, 서비스가 거는 커밋/롤백 후처리를 직접 모았다가 흉내 낸다.
        TransactionSynchronizationManager.initSynchronization();

        service = new PathologyResultService(pathologyResultRepository, labOrderItemRepository, commonCodeCache,
                new LabResultTypeResolver(Map.of("07", LabResultType.PATHOLOGY)), storage, billingChargeService, labResultTransmissionService);

        item = mock(LabOrderItemEntity.class);
        when(item.getLabOrderItemId()).thenReturn("item-7");
        when(item.getLabItemCode()).thenReturn("07");
        when(labOrderItemRepository.findById("item-7")).thenReturn(Optional.of(item));
        when(pathologyResultRepository.existsByLabOrderItem_LabOrderItemId("item-7")).thenReturn(false);
        when(commonCodeCache.isValid(anyString(), anyString())).thenReturn(true);
        when(pathologyResultRepository.save(any(PathologyResultEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(storage.upload(eq("/pathology-results/"), eq("item-7"), any())).thenReturn("/pathology-results/item-7/u_slide.png");
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private PathologyResultCreateRequestDto request() {
        return PathologyResultCreateRequestDto.builder()
                .labOrderItemId("item-7").pathologyTypeCode("01").findings("[Gross]\nx").recordedById("emp-1").build();
    }

    private void fire(int status) {
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            if (status == TransactionSynchronization.STATUS_COMMITTED) {
                s.afterCommit();
            }
            s.afterCompletion(status);
        }
    }

    @Test
    @DisplayName("병리가 아닌 항목이면 LAB079")
    void notPathologyItem() {
        when(item.getLabItemCode()).thenReturn("01");
        assertThatThrownBy(() -> service.createResult(request(), null))
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB079);
    }

    @Test
    @DisplayName("이미 결과가 있는 항목이면 LAB085")
    void duplicate() {
        when(pathologyResultRepository.existsByLabOrderItem_LabOrderItemId("item-7")).thenReturn(true);
        assertThatThrownBy(() -> service.createResult(request(), null))
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB085);
    }

    @Test
    @DisplayName("허용되지 않은 형식(tiff)은 LAB087 이고 업로드도 하지 않는다")
    void badContentType() {
        MockMultipartFile tiff = new MockMultipartFile("file", "a.tif", "image/tiff", new byte[]{1});
        assertThatThrownBy(() -> service.createResult(request(), tiff))
                .extracting("messageCode").isEqualTo(LabMessageCode.LAB087);
        verify(storage, never()).upload(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("첨부와 함께 등록 → 롤백되면 올린 파일을 지운다 (고아 파일 방지)")
    void rollbackDeletesUploadedFile() {
        service.createResult(request(), png);

        fire(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(storage).deleteQuietly("/pathology-results/item-7/u_slide.png");
    }

    @Test
    @DisplayName("첨부와 함께 등록 → 커밋되면 파일을 지우지 않는다")
    void commitKeepsFile() {
        service.createResult(request(), png);

        fire(TransactionSynchronization.STATUS_COMMITTED);
        verify(storage, never()).deleteQuietly(anyString());
    }

    @Test
    @DisplayName("수정에서 첨부를 교체하면 이전 파일은 커밋된 뒤에만 지운다")
    void replaceDeletesPreviousOnlyAfterCommit() {
        PathologyResultEntity saved = PathologyResultEntity.builder()
                .pathologyTypeCode("01").findings("x").attachmentFileKey("/pathology-results/item-7/old_a.png")
                .resultStatusCode("01").recordedAt(LocalDateTime.now()).recordedById("emp-1").build();
        saved.assignLabOrderItem(item);
        when(pathologyResultRepository.findDetailById("pr-1")).thenReturn(Optional.of(saved));

        service.updateResult("pr-1",
                PathologyResultUpdateRequestDto.builder().pathologyTypeCode("01").findings("y").build(), png);

        verify(storage, never()).deleteQuietly("/pathology-results/item-7/old_a.png");
        fire(TransactionSynchronization.STATUS_COMMITTED);
        verify(storage).deleteQuietly("/pathology-results/item-7/old_a.png");
    }

    @Test
    @DisplayName("저장 키에서 원본 파일명·콘텐츠타입을 되살린다")
    void keyHelpers() {
        String key = "/pathology-results/item-7/0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0_biopsy report.pdf";
        assertThat(PathologyResultService.fileNameOf(key)).isEqualTo("biopsy report.pdf");
        assertThat(PathologyResultService.contentTypeOf(key)).isEqualTo("application/pdf");
        assertThat(PathologyResultService.contentTypeOf("x/y_a.JPG")).isEqualTo("image/jpeg");
    }
}
