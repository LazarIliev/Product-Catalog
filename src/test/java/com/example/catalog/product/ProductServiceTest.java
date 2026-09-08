package com.example.catalog.product;

import com.example.catalog.product.dto.UpdateProductRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the rules the service owns and nothing else — no Spring context, no database.
 * These cover the branches that are awkward to provoke through HTTP.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository repository;

    @InjectMocks
    private ProductService service;

    @Test
    @DisplayName("update with a stale version is rejected before anything is written")
    void rejectsStaleVersionWithoutWriting() {
        Product stored = productWithId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(stored));

        UpdateProductRequest request = new UpdateProductRequest(
                "Espresso Machine", new BigDecimal("229.99"), "kitchen", 3, 7L);

        assertThatThrownBy(() -> service.update(1L, request))
                .isInstanceOf(StaleProductException.class)
                .extracting("expectedVersion")
                .isEqualTo(7L);

        // The important half of the assertion: a conflict must not leave a partial write behind.
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("update without a version is last-write-wins")
    void updateWithoutVersionProceeds() {
        Product stored = productWithId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(stored));
        when(repository.saveAndFlush(stored)).thenReturn(stored);

        UpdateProductRequest request = new UpdateProductRequest(
                "Espresso Machine Mk2", new BigDecimal("199.00"), "Kitchen ", 3, null);

        Product updated = service.update(1L, request);

        assertThat(updated.getName()).isEqualTo("Espresso Machine Mk2");
        assertThat(updated.getQuantity()).isEqualTo(3);
        // Whitespace is normalised by the entity so that " Kitchen " and "Kitchen" group together.
        assertThat(updated.getCategory()).isEqualTo("Kitchen");
        // Scale is normalised to the column's, so the response matches a subsequent read.
        assertThat(updated.getPrice()).isEqualByComparingTo("199.00");
        assertThat(updated.getPrice().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("deleting an unknown id fails instead of silently succeeding")
    void deleteUnknownIdThrows() {
        when(repository.existsById(42L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(42L))
                .isInstanceOf(ProductNotFoundException.class);

        verify(repository, never()).deleteById(any());
    }

    private static Product productWithId(long id) {
        Product product = new Product("Espresso Machine", new BigDecimal("249.99"), "kitchen", 12);
        org.springframework.test.util.ReflectionTestUtils.setField(product, "id", id);
        return product;
    }
}
