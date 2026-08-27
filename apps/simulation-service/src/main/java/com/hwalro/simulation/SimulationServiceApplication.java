package com.hwalro.simulation;

import com.hwalro.simulation.common.jwt.JwtProperties;
import com.hwalro.simulation.search.config.LayoutSearchProperties;
import com.hwalro.simulation.simulation.client.RegulationServiceProperties;
import com.hwalro.simulation.zone.client.AuthServiceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
    JwtProperties.class,
    LayoutSearchProperties.class,
    RegulationServiceProperties.class,
    AuthServiceProperties.class
})
public class SimulationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SimulationServiceApplication.class, args);
    }
}
