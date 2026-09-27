package com.hrportal.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI at /swagger-ui.html; API calls use HTTP Basic with a normal HR Portal login. */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI hrPortalOpenApi() {
        return new OpenAPI()
                .info(new Info().title("HR Portal Pro API").version("stage-1")
                        .description("Self-service leave, manager approvals and HR administration."))
                .components(new Components().addSecuritySchemes("basic",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")))
                .addSecurityItem(new SecurityRequirement().addList("basic"));
    }
}
