package kr.co.seoulit.his.labimagingservice.config;

import kr.co.seoulit.his.common.session.SessionUser;
import kr.co.seoulit.his.labimagingservice.common.session.LoginUserArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Spring MVC 확장 설정. (5차 Phase 2 — 이 서비스에서 처음 생긴 WebMvcConfigurer)
 *
 * ⚠ CORS 설정은 여기 두지 않는다. 브라우저가 lab-imaging 을 직접 부르지 않고 Next rewrite 로만
 *   거쳐 오기 때문에 필요 없다(docs/cors-guide.md). 나중에 필요해지면 이 클래스에 추가한다.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    /*
     * ⚠ @LoginUser SessionUser 파라미터를 Swagger 문서에서 숨긴다.
     *   springdoc 은 모르는 객체 파라미터를 쿼리 파라미터로 문서화한다. 그대로 두면 Swagger 화면에
     *   empId·loginId 같은 입력칸이 생겨, 거기에 값을 넣으면 로그인 사용자가 바뀌는 것처럼 오해하게 된다.
     *   (실제로는 세션에서만 읽으므로 영향은 없다 — 화면 혼동 방지용)
     */
    static {
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(SessionUser.class);
    }

    private final LoginUserArgumentResolver loginUserArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginUserArgumentResolver);
    }
}
