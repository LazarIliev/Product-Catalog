package com.example.catalog.model.product.dto;

import com.example.catalog.model.product.Product;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Schema(name = "Product", description = "A product in the catalog")
public record ProductResponse(
        Long id,
        String name,
        BigDecimal price,
        String category,
        int quantity,
        @Schema(description = "Pass this back in an update to detect concurrent modification")
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getCategory(),
                product.getQuantity(),
                product.getVersion(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
