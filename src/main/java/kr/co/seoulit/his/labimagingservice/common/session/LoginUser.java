package kr.co.seoulit.his.labimagingservice.common.session;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 로그인 사용자(SessionUser)를 주입받을 때 붙인다. (5차 Phase 2)
 *
 * <pre>
 * public ResponseEntity<...> create(@LoginUser SessionUser loginUser, @Valid @RequestBody XxxDto request)
 * </pre>
 *
 * ⚠ 로그인 세션이 없으면 예외가 아니라 null 이 들어온다. (LoginUserArgumentResolver 참고)
 *   "세션이 없으면 거절"은 여기가 아니라 ActorIdResolver 가 설정(D2)에 따라 정한다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginUser {
}
