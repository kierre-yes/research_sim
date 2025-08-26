package com.backend.cloudsim.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {
    
    @Value("${server.port:8081}")
    private String serverPort;
    
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("CloudSim Load Balancing API ")
                .version("1.0.0")
                .description("REST API for running EPSO and EACO load balancing simulations on CloudSim 7. " +
                    "This API supports single and iteration-based simulations with configurable parameters, " +
                    "CSV workload uploads, statistical comparisons, and MATLAB plot generation.")
                .contact(new Contact()
                    .name("CSA_7 UCPnc")
                    .email("reyeskierchristian64@gmail.com"))
                .license(new License()
                    .name("MIT License")
                    .url("https://opensource.org/licenses/MIT")))
            .servers(List.of(
                new Server()
                    .url("http://localhost:" + serverPort)
                    .description("Local Development Server (Port " + serverPort + ")"),
                new Server()
                    .url("http://localhost:8081")
                    .description("Default Backend Server")
            ));
    }
}
