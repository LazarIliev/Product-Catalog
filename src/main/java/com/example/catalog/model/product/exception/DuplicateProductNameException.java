package com.example.catalog.model.product.exception;

/** Thrown when a product name collides with an existing one. Mapped to 409. */
public class DuplicateProductNameException extends RuntimeException {

    private final String name;

    public DuplicateProductNameException(String name, Throwable cause) {
        super("A product named '%s' already exists".formatted(name), cause);
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
