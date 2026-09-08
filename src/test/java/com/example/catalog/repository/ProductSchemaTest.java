package com.example.catalog.repository;

import com.example.catalog.AbstractPostgresIntegrationTest;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Asserts that the invariants hold at the database level, not only in the API layer. Written against
 * raw SQL on purpose: it is the check that the migration — not bean validation — is doing the work,
 * so the data stays correct even for writers that never go through this service.
 */
@SpringBootTest(properties =
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.example.catalog.repository.ProductSchemaTest$SqlCapture")
class ProductSchemaTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProductRepository repository;

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
    @DisplayName("the category filter actually uses the category index")
    void categoryFilterUsesTheIndex() {
        // Asserting that the index exists would prove nothing: an index is only worth having if the
        // query planner picks it, and the predicate has to match the indexed expression exactly for
        // that to be possible. Spring Data's derived `...IgnoreCase` spells the comparison with
        // upper(), which this index does not cover — so the check has to be against the SQL the
        // repository really emits, not against the SQL we believe it emits.
        seedProducts();
        SqlCapture.STATEMENTS.clear();

        repository.findByCategoryIgnoreCase("RARE", Sort.by(Sort.Direction.ASC, "id"));

        String sql = SqlCapture.STATEMENTS.stream()
                .filter(statement -> statement.contains("from products"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no SELECT against products was captured"));

        String plan = String.join(
                "\n",
                jdbc.queryForList("EXPLAIN " + sql.replace("?", "'rare'"), String.class));

        assertThat(plan)
                .as("plan for the query the repository issues:\n%s\n%s", sql, plan)
                .contains("ix_products_category_lower");

        jdbc.update("DELETE FROM products WHERE name LIKE 'Index Probe%'");
    }

    /** Enough rows, with statistics refreshed, that a sequential scan is not the cheap option. */
    private void seedProducts() {
        jdbc.update("DELETE FROM products WHERE name LIKE 'Index Probe%'");
        jdbc.batchUpdate(
                "INSERT INTO products (name, price, category, quantity) VALUES (?, ?, ?, ?)",
                java.util.stream.IntStream.range(0, 2000)
                        .mapToObj(i -> new Object[] {
                                "Index Probe " + i,
                                new java.math.BigDecimal("1.00"),
                                i == 0 ? "rare" : "common" + (i % 20),
                                1})
                        .toList());
        jdbc.execute("ANALYZE products");
    }

    /**
     * Hands the test the SQL Hibernate really sends, which is the only thing worth explaining — the
     * gap between the intended and the emitted predicate is exactly the bug this test exists to catch.
     */
    public static class SqlCapture implements StatementInspector {

        static final List<String> STATEMENTS = new CopyOnWriteArrayList<>();

        @Override
        public String inspect(String sql) {
            STATEMENTS.add(sql);
            return sql;
        }
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
