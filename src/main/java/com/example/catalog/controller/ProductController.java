package com.example.catalog.controller;

import com.example.catalog.model.product.Product;
import com.example.catalog.service.ProductService;
import com.example.catalog.model.product.dto.CreateProductRequest;
import com.example.catalog.model.product.dto.ProductResponse;
import com.example.catalog.model.product.dto.UpdateProductRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Tag(name = "Products", description = "CRUD operations on the product catalog")
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @Operation(summary = "Create a product")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created; Location header points at the new product"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
            @ApiResponse(responseCode = "409", description = "A product with that name already exists", content = @Content)
    })
    @PostMapping(consumes = "application/json")
    public ResponseEntity<ProductResponse> create(
            @Valid @RequestBody CreateProductRequest request, UriComponentsBuilder uriBuilder) {

        Product created = service.create(request);
        URI location = uriBuilder.path("/api/v1/products/{id}").build(created.getId());
        return ResponseEntity.created(location).body(ProductResponse.from(created));
    }

    @Operation(
            summary = "List products",
            description = "Ordered by id ascending. Optionally narrowed to a single category, which "
                    + "is matched case-insensitively; omitting it (or leaving it empty) returns the "
                    + "whole catalog.")
    @GetMapping
    public List<ProductResponse> list(
            @Parameter(description = "Only return products in this category", example = "kitchen")
            @RequestParam(required = false) String category) {

        return service.findAll(category).stream().map(ProductResponse::from).toList();
    }

    @Operation(summary = "Get a single product")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found"),
            @ApiResponse(responseCode = "404", description = "No product with that id", content = @Content)
    })
    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable long id) {
        return ProductResponse.from(service.findById(id));
    }

    @Operation(
            summary = "Replace a product",
            description = "Full replacement. Send the version you last read to get a 409 instead of "
                    + "silently overwriting someone else's change.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
            @ApiResponse(responseCode = "404", description = "No product with that id", content = @Content),
            @ApiResponse(responseCode = "409", description = "Concurrent modification or duplicate name", content = @Content)
    })
    @PutMapping(path = "/{id}", consumes = "application/json")
    public ProductResponse update(
            @PathVariable long id, @Valid @RequestBody UpdateProductRequest request) {
        return ProductResponse.from(service.update(id, request));
    }

    @Operation(summary = "Delete a product")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted"),
            @ApiResponse(responseCode = "404", description = "No product with that id", content = @Content)
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }
}
