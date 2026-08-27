package com.hwalro.regulation;

import com.hwalro.regulation.common.jwt.JwtProperties;
import com.hwalro.regulation.law.api.LawApiProperties;
import com.hwalro.regulation.report.client.AuthServiceProperties;
import com.hwalro.regulation.report.client.SimulationServiceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
@EnableConfigurationProperties({
    LawApiProperties.class,
    JwtProperties.class,
    AuthServiceProperties.class,
    SimulationServiceProperties.class
})
/** regulation-service의 Spring Boot 시작점이며 법령 API 설정 바인딩을 활성화한다. */
public class RegulationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RegulationServiceApplication.class, args);
    }
}
