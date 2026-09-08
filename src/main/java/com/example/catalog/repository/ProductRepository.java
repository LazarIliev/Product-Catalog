package com.example.catalog.repository;

import com.example.catalog.model.product.Product;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Internal to the {@code product} package; the service is the only door into persistence. */
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Case-insensitive lookup by category.
     *
     * <p>The predicate is spelled out rather than derived from a {@code ...IgnoreCase} method name on
     * purpose: Spring Data derives that as {@code upper(category) = upper(?)}, which no index here
     * covers, so the filter would silently fall back to a sequential scan. {@code lower(...)} is the
     * expression indexed by {@code ix_products_category_lower} in {@code V2__add_category_index.sql},
     * and matches how {@code ux_products_name_lower} already treats {@code name}.
     *
     * <p>{@code ProductSchemaTest} pins the two together by explaining the SQL this method really
     * emits, so a future edit that breaks the pairing fails the build rather than the latency graph.
     */
    @Query("SELECT p FROM Product p WHERE lower(p.category) = lower(:category)")
    List<Product> findByCategoryIgnoreCase(@Param("category") String category, Sort sort);
}
