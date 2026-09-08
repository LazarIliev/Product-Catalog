package com.example.catalog.repository;

import com.example.catalog.model.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the {@code product} package; the service is the only door into persistence. */
public interface ProductRepository extends JpaRepository<Product, Long> {
}
