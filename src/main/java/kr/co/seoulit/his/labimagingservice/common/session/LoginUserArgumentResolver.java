package kr.co.seoulit.his.labimagingservice.common.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import kr.co.seoulit.his.common.session.SessionUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@code @LoginUser SessionUser} 파라미터를 채운다. (5차 Phase 2, 후속조치 #7)
 *
 * ⚠ request.getSession(false) 로만 꺼낸다. getSession() / getSession(true) 를 쓰면 로그인 안 한
 *   요청마다 빈 세션이 새로 만들어져 Redis 에 쌓인다. (admin AuthSessionInterceptor 와 같은 이유)
 *
 * ⚠ 세션이 없거나, 있어도 LOGIN_USER 가 없으면 예외를 던지지 않고 null 을 준다.
 *   거절 여부는 호출하는 쪽(ActorIdResolver)이 D2 설정에 따라 정한다.
 *   Kafka 연계처럼 사람이 없는 호출도 같은 서비스 메서드를 타기 때문에, 여기서 막으면 안 된다.
 *
 * ⚠ 값의 타입이 SessionUser 가 아니면(다른 서비스가 같은 키에 다른 객체를 넣은 경우) null 로 본다.
 *   ClassCastException 으로 요청 전체가 500 이 되는 것보다, 로그를 남기고 "로그인 사용자 없음"으로
 *   처리하는 편이 원인 추적도 쉽고 업무도 막히지 않는다.
 *
 * ⚠ 세션 복원 자체가 실패하는 경우도 있다 — Spring Session 은 세션 속성을 한꺼번에 역직렬화하는데,
 *   admin 이 kr.co.seoulit.his. 밖의 클래스를 세션에 넣으면 RedisSessionConfig 의 허용 목록에 걸린다.
 *   (admin 팀에 "세션에는 kr.co.seoulit.his.common 패키지 객체만 넣는다" 규칙 공유 요청 — 04_타팀_협의요청)
 */
@Slf4j
@Component
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUser.class)
                && SessionUser.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public SessionUser resolveArgument(MethodParameter parameter,
                                       ModelAndViewContainer mavContainer,
                                       NativeWebRequest webRequest,
                                       WebDataBinderFactory binderFactory) {

        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            return null;
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }

        Object value = session.getAttribute(SessionKeys.LOGIN_USER);
        if (value == null) {
            return null;
        }
        if (value instanceof SessionUser sessionUser) {
            return sessionUser;
        }

        log.warn("세션 {} 값의 타입이 SessionUser 가 아닙니다. 로그인 사용자 없음으로 처리합니다. (type={})",
                SessionKeys.LOGIN_USER, value.getClass().getName());
        return null;
    }
}
