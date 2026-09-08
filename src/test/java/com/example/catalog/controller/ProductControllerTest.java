package com.example.catalog.controller;

import com.example.catalog.model.product.Product;
import com.example.catalog.model.product.dto.CreateProductRequest;
import com.example.catalog.model.product.exception.ProductNotFoundException;
import com.example.catalog.service.ProductService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests: status codes, headers and the error contract, with the service mocked out.
 * They fail if someone changes what the API promises, not if the persistence changes.
 */
@WebMvcTest(ProductController.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService service;

    @Test
    @DisplayName("POST returns 201 with a Location header pointing at the new product")
    void createReturns201AndLocation() throws Exception {
        given(service.create(any())).willReturn(productWithId(7L));

        String body = objectMapper.writeValueAsString(new CreateProductRequest(
                "Espresso Machine", new BigDecimal("249.99"), "kitchen", 12));

        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/products/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("Espresso Machine"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    @DisplayName("POST reports every invalid field at once as a problem detail")
    void createReportsAllValidationErrors() throws Exception {
        String body = """
                {"name": "  ", "price": -1.00, "category": "kitchen", "quantity": -5}
                """;

        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                // All three violations are reported together, so a client fixes one round trip.
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[1].field").value("price"))
                .andExpect(jsonPath("$.errors[2].field").value("quantity"));
    }

    @Test
    @DisplayName("POST rejects a missing price rather than defaulting it")
    void createRejectsMissingPrice() throws Exception {
        String body = """
                {"name": "Kettle", "category": "kitchen", "quantity": 1}
                """;

        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("price"));
    }

    @Test
    @DisplayName("GET passes the optional category through, and null when it is absent")
    void listPassesCategoryThrough() throws Exception {
        given(service.findAll(any())).willReturn(List.of(productWithId(7L)));

        mockMvc.perform(get("/api/v1/products").param("category", "kitchen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        verify(service).findAll("kitchen");

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk());
        // Absent means absent: the controller does not invent a default the service would filter on.
        verify(service).findAll(null);
    }

    @Test
    @DisplayName("GET of an unknown id returns 404 as a problem detail, not a stack trace")
    void getUnknownReturns404() throws Exception {
        willThrow(new ProductNotFoundException(99L)).given(service).findById(99L);

        mockMvc.perform(get("/api/v1/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Product not found"))
                .andExpect(jsonPath("$.productId").value(99))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("a non-numeric id is a client error, not a 500")
    void nonNumericIdReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/products/not-a-number"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("malformed JSON is a client error, not a 500")
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json "))
                .andExpect(status().isBadRequest());
    }

    private static Product productWithId(long id) {
        Product product = new Product("Espresso Machine", new BigDecimal("249.99"), "kitchen", 12);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }
}
