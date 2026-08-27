package com.hwalro.simulation.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.hwalro.simulation.common.jwt.RequireRole;
import com.hwalro.simulation.drawing.controller.DrawingController;
import com.hwalro.simulation.zone.controller.LayoutZoneController;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.server.PathContainer;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * 인증 인터셉터에 등록되지 않은 API 경로를 잡는다.
 *
 * <p>등록에서 빠지면 컨트롤러가 {@code jwtUser} 요청 속성을 받지 못해 모든 요청이 500으로 죽는다. {@code @RequireRole}도 그 인터셉터가 검사하므로 함께
 * 무력화된다. 실제로 {@code /api/my-zones}가 이렇게 빠져 있었고, 배정된 구역이 생기고 나서야 드러났다.
 */
class WebConfigTest {
    private static final String CONTROLLER_PACKAGE = "com.hwalro.simulation";
    private static final PathPatternParser PARSER = PathPatternParser.defaultInstance;

    @Test
    void everyApiEndpointIsEitherAuthenticatedOrDeliberatelyPublic() {
        List<PathPattern> authenticated = WebConfig.AUTHENTICATED_PATH_PATTERNS.stream()
                .map(PARSER::parse)
                .toList();
        List<PathPattern> publicPaths =
                WebConfig.PUBLIC_PATHS.stream().map(PARSER::parse).toList();

        List<String> unprotected = new ArrayList<>();
        for (String path : mappedApiPaths()) {
            PathContainer container = PathContainer.parsePath(path);
            boolean covered = authenticated.stream().anyMatch(pattern -> pattern.matches(container))
                    || publicPaths.stream().anyMatch(pattern -> pattern.matches(container));
            if (!covered) {
                unprotected.add(path);
            }
        }

        assertThat(unprotected)
                .as("WebConfig.AUTHENTICATED_PATH_PATTERNS 에 빠진 경로")
                .isEmpty();
    }

    @Test
    void employeeCanReadAssignedZoneMetadata() throws NoSuchMethodException {
        Method metadata = LayoutZoneController.class.getDeclaredMethod(
                "metadata", Long.class, com.hwalro.simulation.common.jwt.JwtUser.class);

        assertThat(metadata.getAnnotation(RequireRole.class).value()).contains("GENERAL_EMPLOYEE");
    }

    @Test
    void everyRoleUsesTheSingleDrawingEvacuationRoutesEndpoint() throws NoSuchMethodException {
        List<String> evacuationPaths = mappedApiPaths().stream()
                .filter(path -> path.contains("evacuation-route"))
                .toList();
        Method drawing = DrawingController.class.getDeclaredMethod(
                "get", Long.class, com.hwalro.simulation.common.jwt.JwtUser.class);

        assertThat(evacuationPaths).containsExactly("/api/drawings/1/evacuation-routes");
        assertThat(drawing.getAnnotation(RequireRole.class).value()).contains("GENERAL_EMPLOYEE");
    }

    /** 컨트롤러의 클래스 매핑과 메서드 매핑을 합쳐 실제로 열리는 /api 경로를 모은다. 경로 변수는 아무 값으로 채운다. */
    private static Set<String> mappedApiPaths() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        Set<String> paths = new LinkedHashSet<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(CONTROLLER_PACKAGE)) {
            Class<?> controller = load(definition.getBeanClassName());
            for (String base : classPaths(controller)) {
                for (Method method : controller.getDeclaredMethods()) {
                    RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                    if (mapping == null) {
                        continue;
                    }
                    for (String suffix : mapping.path().length == 0 ? new String[] {""} : mapping.path()) {
                        String combined = concrete(base + suffix);
                        if (combined.startsWith("/api")) {
                            paths.add(combined);
                        }
                    }
                }
            }
        }
        assertThat(paths).as("스캔된 API 경로").isNotEmpty();
        return paths;
    }

    private static String[] classPaths(Class<?> controller) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
        if (mapping == null || mapping.path().length == 0) {
            return new String[] {""};
        }
        return mapping.path();
    }

    /** {id} 같은 경로 변수를 가진 패턴은 경로로 매칭할 수 없다. 아무 값으로 바꿔 실제 요청 경로처럼 만든다. */
    private static String concrete(String path) {
        return path.replaceAll("\\{[^}]*}", "1");
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("컨트롤러를 불러올 수 없습니다: " + className, e);
        }
    }
}
