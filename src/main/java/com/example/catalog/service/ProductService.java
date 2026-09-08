package com.example.catalog.service;

import com.example.catalog.model.product.Product;
import com.example.catalog.repository.ProductRepository;
import com.example.catalog.model.product.dto.CreateProductRequest;
import com.example.catalog.model.product.dto.UpdateProductRequest;
import com.example.catalog.model.product.exception.DuplicateProductNameException;
import com.example.catalog.model.product.exception.ProductNotFoundException;
import com.example.catalog.model.product.exception.StaleProductException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Transaction boundary and business rules for the catalog. The controller stays free of both, and
 * this class never touches HTTP types — it throws domain exceptions that the web layer translates.
 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    /** Name of the unique index in V1__create_products.sql. */
    private static final String UNIQUE_NAME_INDEX = "ux_products_name_lower";

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Product create(CreateProductRequest request) {
        Product product = new Product(
                request.name(), request.price(), request.category(), request.quantity());
        Product created = persist(product, null);
        log.info("Created product id={} name={}", created.getId(), created.getName());
        return created;
    }

    @Transactional(readOnly = true)
    public List<Product> findAll() {
        List<Product> products = repository.findAll(Sort.by(Sort.Direction.ASC, "id"));
        log.debug("Listed {} products", products.size());
        return products;
    }

    @Transactional(readOnly = true)
    public Product findById(long id) {
        log.debug("Looking up product id={}", id);
        return repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional
    public Product update(long id, UpdateProductRequest request) {
        Product product = findById(id);

        if (request.version() != null && request.version() != product.getVersion()) {
            throw new StaleProductException(id, request.version(), null);
        }

        product.update(request.name(), request.price(), request.category(), request.quantity());
        Product updated = persist(product, request.version());
        log.info("Updated product id={} version={}", updated.getId(), updated.getVersion());
        return updated;
    }

    @Transactional
    public void delete(long id) {
        if (!repository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        repository.deleteById(id);
        log.info("Deleted product id={}", id);
    }

    /**
     * Flushes eagerly so that constraint violations surface here, inside the service, rather than at
     * commit time where they can no longer be attributed to a specific operation.
     *
     * <p>Uniqueness is enforced by the database rather than by a "does it already exist?" query:
     * that check would be a lost race under concurrency, and the index has to exist anyway.
     */
    private Product persist(Product product, Long expectedVersion) {
        try {
            return repository.saveAndFlush(product);
        } catch (OptimisticLockingFailureException e) {
            throw new StaleProductException(
                    product.getId() == null ? -1 : product.getId(), expectedVersion, e);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateName(e)) {
                throw new DuplicateProductNameException(product.getName(), e);
            }
            throw e;
        }
    }

    private static boolean isDuplicateName(DataIntegrityViolationException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(UNIQUE_NAME_INDEX)) {
                return true;
            }
        }
        return false;
    }
}
