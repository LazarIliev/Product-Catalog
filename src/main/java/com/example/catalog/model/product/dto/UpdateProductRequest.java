package com.example.catalog.model.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Full replacement of a product (PUT semantics).
 *
 * <p>{@code version} is optional. When supplied it must match the version the client last read, or
 * the update is rejected with 409 — that is the opt-in optimistic locking path. When omitted the
 * update is last-write-wins, which keeps simple clients (and curl) usable.
 */
@Schema(name = "UpdateProductRequest", description = "Payload for replacing a product")
public record UpdateProductRequest(

        @Schema(example = "Espresso Machine")
        @NotBlank
        @Size(max = 200)
        String name,

        @Schema(example = "229.99", description = "Must be strictly greater than zero")
        @NotNull
        @DecimalMin(value = "0.00", inclusive = false)
        @Digits(integer = 10, fraction = 2)
        BigDecimal price,

        @Schema(example = "kitchen")
        @NotBlank
        @Size(max = 100)
        String category,

        @Schema(example = "10", description = "Must be zero or greater")
        @NotNull
        @PositiveOrZero
        Integer quantity,

        @Schema(example = "0", description = "Optional. If present, must match the current version.")
        @PositiveOrZero
        Long version) {
}
