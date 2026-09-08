package com.example.catalog.model.product.exception;

/**
 * Thrown when the client's view of a product is out of date — either because the version it sent
 * does not match the stored one, or because another transaction won the race at flush time. Mapped
 * to 409.
 */
public class StaleProductException extends RuntimeException {

    private final long id;
    private final Long expectedVersion;

    public StaleProductException(long id, Long expectedVersion, Throwable cause) {
        super("Product %d was modified by another request".formatted(id), cause);
        this.id = id;
        this.expectedVersion = expectedVersion;
    }

    public long getId() {
        return id;
    }

    /** The version the client believed was current, when it supplied one. */
    public Long getExpectedVersion() {
        return expectedVersion;
    }
}
