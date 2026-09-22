package com.ticketproject.webapp.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenApiConfig is a Spring configuration class that defines
 * global OpenAPI documentation settings for the application's
 * Swagger UI page.
 */
@Configuration
public class OpenApiConfig
{
    /**
     * The name of the security scheme used to authenticate
     * requests via a JSON Web Token embedded in the
     * Authorization HTTP header.
     */
    public static final String SECURITY_SCHEME_NAME = "bearerAuth";

    /**
     * Defines the global OpenAPI configuration bean, including
     * API metadata and the Bearer JWT security scheme.
     * 
     * @return an OpenAPI bean with API info and security configuration
     */
    @Bean
    public OpenAPI openAPI()
    {
        return new OpenAPI()
            .info
            (
                new Info()
                    .title("ScanMeIn API")
                    .description
                    (
                        "REST API for ScanMeIn.online, an event ticketing platform. " +
                        "Event hosts can create and manage events, attendees can register " +
                        "for public events, and tickets can be scanned at event entry. " +
                        "Routes marked with the lock icon require an " +
                        "Authorization: Bearer <JWT> header obtained from the login endpoint."
                    )
                    .version("v1")
            )
            .components
            (
                new Components()
                    .addSecuritySchemes
                    (
                        SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                            .name(SECURITY_SCHEME_NAME)
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description
                            (
                                "A JSON Web Token (JWT) obtained from the " +
                                "POST /api/v1/sessions/login endpoint. " +
                                "Include it in the Authorization header as: " +
                                "Authorization: Bearer <token>"
                            )
                    )
            )
            .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}