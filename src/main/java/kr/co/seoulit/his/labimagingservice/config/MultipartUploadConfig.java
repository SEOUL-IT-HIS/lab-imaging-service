package kr.co.seoulit.his.labimagingservice.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 멀티파트 업로드 요청 크기 상한. (04번 지시서 Phase 3-D, 2026-10-05)
 *
 * ⚠ Boot 자동설정(spring.servlet.multipart.*)은 이 빈이 있으면 물러난다 — 사용자 정의
 *   MultipartConfigElement 빈이 있으면 Boot 가 자체 등록을 건너뛴다(Spring Boot 표준 동작).
 *   application.properties 를 건드리지 않고 상한을 코드로 관리하려는 지시서 §0-4 방침과
 *   이 자동 양보 동작이 맞아떨어진다.
 *
 * ⚠ 여기서 정하는 상한은 "서블릿 컨테이너가 받아줄 수 있는 요청 전체의 최대치"다 — 영상
 *   업로드(app.upload.image-max-bytes)와 병리 첨부(app.upload.attachment-max-bytes) 중
 *   더 큰 쪽을 기준으로 한다. 더 작은 쪽(병리 첨부)의 실제 상한은 이 빈이 아니라
 *   PathologyResultService 가 서비스 계층에서 별도로 검사한다(같은 LAB109) — 컨테이너
 *   상한만으로는 "업로드 종류별로 다른 한도"를 표현할 수 없기 때문이다.
 *
 * ⚠ Phase 0-5 에서 확인된 Next.js 프록시(rewrite) 본문 상한(10MB, 초과 시 조용히 잘림)에
 *   안전하게 맞추려고, 지시서 기본값(영상 100MB·첨부 20MB)보다 낮춰서 default 를 둔다
 *   (2026-10-05 사용자 결정). 운영에서 프록시 상한을 올리면 이 값도 같이 올릴 수 있다.
 */
@Configuration
public class MultipartUploadConfig {

    @Bean
    public MultipartConfigElement multipartConfigElement(
            @Value("${app.upload.image-max-bytes:8388608}") long imageMaxBytes,
            @Value("${app.upload.attachment-max-bytes:5242880}") long attachmentMaxBytes) {

        long maxBytes = Math.max(imageMaxBytes, attachmentMaxBytes);

        // location="" → 컨테이너 기본 임시 디렉터리. fileSizeThreshold=0 → 메모리에 버퍼링하지 않고
        // 바로 디스크에 쓴다(큰 파일을 메모리에 쌓지 않기 위함 — Tomcat 기본값과 같은 선택).
        return new MultipartConfigElement("", maxBytes, maxBytes, 0);
    }
}
