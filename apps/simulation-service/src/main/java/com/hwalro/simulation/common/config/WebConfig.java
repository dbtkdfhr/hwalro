package com.hwalro.simulation.common.config;

import com.hwalro.simulation.common.jwt.JwtAuthInterceptor;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 인증 인터셉터가 붙는 경로.
     *
     * <p>여기 빠진 컨트롤러는 {@code jwtUser} 요청 속성을 받지 못해 500으로 죽고, {@code @RequireRole}도 검사되지 않는다. 컨트롤러를 새로 만들면 이
     * 목록에 반드시 추가한다. {@code WebConfigTest}가 빠진 경로를 잡는다.
     */
    public static final List<String> AUTHENTICATED_PATH_PATTERNS = List.of(
            "/api/drawings/**",
            "/api/my-zones/**",
            "/api/simulation-results/**",
            "/api/simulations/**",
            "/api/layout-searches/**");

    /** 인증 없이 열어 두는 경로. 로드밸런서가 부르는 헬스 체크뿐이다. */
    public static final List<String> PUBLIC_PATHS = List.of("/api/health");

    private final JwtAuthInterceptor jwtAuthInterceptor;

    public WebConfig(JwtAuthInterceptor jwtAuthInterceptor) {
        this.jwtAuthInterceptor = jwtAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor).addPathPatterns(AUTHENTICATED_PATH_PATTERNS);
    }
}
