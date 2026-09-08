package com.example.catalog.product;

import com.example.catalog.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Asserts that the invariants hold at the database level, not only in the API layer. Written against
 * raw SQL on purpose: it is the check that the migration — not bean validation — is doing the work,
 * so the data stays correct even for writers that never go through this service.
 */
@SpringBootTest
class ProductSchemaTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("the database rejects a non-positive price and a negative quantity")
    void checkConstraintsAreEnforced() {
        assertThatThrownBy(() -> insert("Direct Insert A", "0.00", "misc", 1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_products_price_positive");

        assertThatThrownBy(() -> insert("Direct Insert B", "1.00", "misc", -1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_products_quantity_non_negative");

        assertThatThrownBy(() -> insert("   ", "1.00", "misc", 1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_products_name_not_blank");
    }

    @Test
    @DisplayName("price keeps two decimal places rather than binary-float rounding")
    void priceIsExactDecimal() {
        jdbc.update("DELETE FROM products WHERE name = 'Precision Probe'");
        insert("Precision Probe", "0.10", "misc", 1);
        insert("Precision Probe 2", "0.20", "misc", 1);

        // 0.10 + 0.20 is exactly 0.30 in NUMERIC; in a float column it would not be.
        assertThat(jdbc.queryForObject(
                "SELECT sum(price) FROM products WHERE name LIKE 'Precision Probe%'",
                java.math.BigDecimal.class))
                .isEqualByComparingTo("0.30");

        jdbc.update("DELETE FROM products WHERE name LIKE 'Precision Probe%'");
    }

    private void insert(String name, String price, String category, int quantity) {
        jdbc.update(
                "INSERT INTO products (name, price, category, quantity) VALUES (?, ?, ?, ?)",
                name, new java.math.BigDecimal(price), category, quantity);
    }
}
