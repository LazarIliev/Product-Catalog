package com.example.catalog.product;

import com.example.catalog.AbstractPostgresIntegrationTest;
import com.example.catalog.product.dto.CreateProductRequest;
import com.example.catalog.product.dto.ProductResponse;
import com.example.catalog.product.dto.UpdateProductRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests over real HTTP against a real PostgreSQL, with Flyway having created the schema.
 * These are the tests that would catch a broken migration, a wrong column type or a mapping that
 * only works in memory.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ProductApiIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PRODUCTS = "/api/v1/products";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ProductRepository repository;

    @BeforeEach
    void clearCatalog() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("a product can be created, read, listed, updated and deleted")
    void fullLifecycle() {
        ResponseEntity<ProductResponse> created = rest.postForEntity(
                PRODUCTS,
                new CreateProductRequest("Espresso Machine", new BigDecimal("249.99"), "kitchen", 12),
                ProductResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        long id = created.getBody().id();
        URI location = created.getHeaders().getLocation();
        assertThat(location).isNotNull();
        assertThat(location.getPath()).isEqualTo(PRODUCTS + "/" + id);
        assertThat(created.getBody().createdAt()).isNotNull();

        // The Location header is honoured: following it returns the same product.
        ResponseEntity<ProductResponse> fetched = rest.getForEntity(location, ProductResponse.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody()).isNotNull();
        assertThat(fetched.getBody().price()).isEqualByComparingTo("249.99");
        assertThat(fetched.getBody().version()).isZero();

        ResponseEntity<List<ProductResponse>> listed = rest.exchange(
                PRODUCTS, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listed.getBody()).extracting(ProductResponse::id).containsExactly(id);

        ResponseEntity<ProductResponse> updated = rest.exchange(
                PRODUCTS + "/" + id,
                HttpMethod.PUT,
                new HttpEntity<>(new UpdateProductRequest(
                        "Espresso Machine", new BigDecimal("199.00"), "kitchen", 5, 0L)),
                ProductResponse.class);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).isNotNull();
        assertThat(updated.getBody().quantity()).isEqualTo(5);
        assertThat(updated.getBody().price()).isEqualByComparingTo("199.00");
        // The version moved on, which is what makes the client's next optimistic update meaningful.
        assertThat(updated.getBody().version()).isEqualTo(1L);

        ResponseEntity<Void> deleted = rest.exchange(
                PRODUCTS + "/" + id, HttpMethod.DELETE, null, Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(rest.getForEntity(PRODUCTS + "/" + id, String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.exchange(PRODUCTS + "/" + id, HttpMethod.DELETE, null, String.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("updating with a version the client no longer holds returns 409")
    void staleUpdateReturnsConflict() {
        long id = create("Kettle", "39.90", "kitchen", 4);

        // First writer wins.
        ResponseEntity<ProductResponse> first = rest.exchange(
                PRODUCTS + "/" + id,
                HttpMethod.PUT,
                new HttpEntity<>(new UpdateProductRequest("Kettle", new BigDecimal("34.90"), "kitchen", 4, 0L)),
                ProductResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second writer still holds version 0 and is told to re-read instead of clobbering.
        ResponseEntity<String> second = rest.exchange(
                PRODUCTS + "/" + id,
                HttpMethod.PUT,
                new HttpEntity<>(new UpdateProductRequest("Kettle", new BigDecimal("29.90"), "kitchen", 9, 0L)),
                String.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody()).contains("stale-product");

        // The losing write left no trace.
        ResponseEntity<ProductResponse> current = rest.getForEntity(PRODUCTS + "/" + id, ProductResponse.class);
        assertThat(current.getBody()).isNotNull();
        assertThat(current.getBody().price()).isEqualByComparingTo("34.90");
    }

    @Test
    @DisplayName("the unique index on name is enforced and surfaces as 409, case-insensitively")
    void duplicateNameReturnsConflict() {
        create("Kettle", "39.90", "kitchen", 4);

        ResponseEntity<String> duplicate = rest.postForEntity(
                PRODUCTS,
                new CreateProductRequest("kettle", new BigDecimal("41.00"), "kitchen", 1),
                String.class);

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).contains("duplicate-product-name");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("invalid input is rejected with 400 and never reaches the database")
    void invalidInputIsRejected() {
        ResponseEntity<String> response = rest.exchange(
                PRODUCTS,
                HttpMethod.POST,
                jsonEntity("""
                        {"name": "", "price": 0, "category": "kitchen", "quantity": -1}
                        """),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType())
                .isNotNull()
                .matches(type -> type.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(repository.count()).isZero();
    }

    private long create(String name, String price, String category, int quantity) {
        ResponseEntity<ProductResponse> response = rest.postForEntity(
                PRODUCTS,
                new CreateProductRequest(name, new BigDecimal(price), category, quantity),
                ProductResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody().id();
    }

    private static HttpEntity<String> jsonEntity(String json) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(json, headers);
    }
}
