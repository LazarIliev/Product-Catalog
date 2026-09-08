package com.example.catalog.product;

import com.example.catalog.product.dto.CreateProductRequest;
import com.example.catalog.product.dto.UpdateProductRequest;
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
        return persist(product, null);
    }

    @Transactional(readOnly = true)
    public List<Product> findAll() {
        return repository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    @Transactional(readOnly = true)
    public Product findById(long id) {
        return repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional
    public Product update(long id, UpdateProductRequest request) {
        Product product = findById(id);

        // Opt-in optimistic locking: reject early and with a precise message when the client tells
        // us which version it edited. Hibernate's @Version still guards the flush itself, which is
        // what catches two concurrent writers that both read the same version.
        if (request.version() != null && request.version() != product.getVersion()) {
            throw new StaleProductException(id, request.version(), null);
        }

        product.update(request.name(), request.price(), request.category(), request.quantity());
        return persist(product, request.version());
    }

    @Transactional
    public void delete(long id) {
        if (!repository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        repository.deleteById(id);
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
