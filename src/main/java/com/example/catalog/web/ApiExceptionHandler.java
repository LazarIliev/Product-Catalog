package com.example.catalog.web;

import com.example.catalog.product.DuplicateProductNameException;
import com.example.catalog.product.ProductNotFoundException;
import com.example.catalog.product.StaleProductException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Translates exceptions into RFC 9457 problem details ({@code application/problem+json}).
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} means Spring's own failures — unreadable JSON,
 * wrong method, unsupported media type, type-mismatched path variables — come back in the same shape
 * as ours instead of as the default error page, so a client only ever has one error format to parse.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String BASE_TYPE = "https://example.com/problems/";

    @ExceptionHandler(ProductNotFoundException.class)
    ProblemDetail handleNotFound(ProductNotFoundException e) {
        ProblemDetail problem = problem(HttpStatus.NOT_FOUND, "Product not found", e.getMessage(), "product-not-found");
        problem.setProperty("productId", e.getId());
        return problem;
    }

    @ExceptionHandler(DuplicateProductNameException.class)
    ProblemDetail handleDuplicateName(DuplicateProductNameException e) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Duplicate product name", e.getMessage(), "duplicate-product-name");
        problem.setProperty("name", e.getName());
        return problem;
    }

    @ExceptionHandler(StaleProductException.class)
    ProblemDetail handleStale(StaleProductException e) {
        ProblemDetail problem = problem(
                HttpStatus.CONFLICT,
                "Concurrent modification",
                "The product was changed since you read it. Re-read it and retry.",
                "stale-product");
        problem.setProperty("productId", e.getId());
        if (e.getExpectedVersion() != null) {
            problem.setProperty("expectedVersion", e.getExpectedVersion());
        }
        return problem;
    }

    /** Last resort: never leak stack traces or SQL to the client. */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                "The request could not be processed.",
                "internal-error");
    }

    /** Turns bean-validation failures into a machine-readable list of per-field violations. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                "One or more fields are invalid.",
                "validation-failed");

        // Sorted so the response is deterministic and diffable, and every violation is reported —
        // a client should not have to fix one field, retry, and discover the next.
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(fieldError -> Map.of(
                        "field", fieldError.getField(),
                        "message", String.valueOf(fieldError.getDefaultMessage())))
                .toList();
        problem.setProperty("errors", errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String type) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(BASE_TYPE + type));
        return problem;
    }
}
