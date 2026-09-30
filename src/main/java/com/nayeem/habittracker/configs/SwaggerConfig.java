package com.nayeem.habittracker.configs;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.servers.Server;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static com.nayeem.habittracker.common.AppConstants.JWT_TOKEN;

@Configuration
@RequiredArgsConstructor
@SecurityScheme(
        name = "jwtToken",
        scheme = "bearer",
        type = SecuritySchemeType.HTTP
)
class SwaggerConfig {

    private final AppProperties appProperties;

    @Bean
    public OpenAPI springShopOpenAPI() {
        String appName = appProperties.getName();

        Info info = new Info();
        info.title(appName + " API");
        info.description(appName + " API Documentation");
        info.version("Version 1.0.0");
        info.license(new License().name("Apache 2.0").url("https://springdoc.org"));

        Server server = new Server();
        server.setUrl(appProperties.getBackendUrl());

        return new OpenAPI()
                .info(info)
                .addSecurityItem(new SecurityRequirement().addList(JWT_TOKEN))
                .servers(List.of(server))
                .externalDocs(new ExternalDocumentation()
                        .description("Full project README")
                        .url("https://github.com/nay-eem-01/habit-tracker#readme"));
    }
}