package com.example.catalog.product;

/** Thrown when a product id does not exist. Mapped to 404 by the global exception handler. */
public class ProductNotFoundException extends RuntimeException {

    private final long id;

    public ProductNotFoundException(long id) {
        super("Product %d was not found".formatted(id));
        this.id = id;
    }

    public long getId() {
        return id;
    }
}
