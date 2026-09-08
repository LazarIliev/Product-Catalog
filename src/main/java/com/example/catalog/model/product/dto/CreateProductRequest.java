package com.example.catalog.model.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "CreateProductRequest", description = "Payload for creating a product")
public record CreateProductRequest(

        @Schema(example = "Espresso Machine")
        @NotBlank
        @Size(max = 200)
        String name,

        @Schema(example = "249.99", description = "Must be strictly greater than zero")
        @NotNull
        @DecimalMin(value = "0.00", inclusive = false)
        @Digits(integer = 10, fraction = 2)
        BigDecimal price,

        @Schema(example = "kitchen")
        @NotBlank
        @Size(max = 100)
        String category,

        @Schema(example = "12", description = "Must be zero or greater")
        @NotNull
        @PositiveOrZero
        Integer quantity) {
}
