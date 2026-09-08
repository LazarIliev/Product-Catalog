package com.example.catalog.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI productCatalogOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Product Catalog API")
                .version("v1")
                .description("""
                        CRUD API for a product catalog.
                        Errors are returned as RFC 9457 problem details (application/problem+json).
                        """));
    }
}
