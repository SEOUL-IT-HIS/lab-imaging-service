package kr.co.seoulit.his.labimagingservice.common;

import jakarta.validation.constraints.Pattern;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * DB의 CHAR(1) 'Y'/'N' 컬럼과 매핑되는 필드용 검증 어노테이션.
 * (개발표준가이드 13장 코드 컨벤션 - _yn 접미사 컬럼은 'Y'/'N'만 허용)
 */

@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {})
@Pattern(regexp = "^[YN]$", message = "Y 또는 N 값만 허용됩니다.")
public @interface YnValue {
    String message() default "Y 또는 N 값만 허용됩니다.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}





