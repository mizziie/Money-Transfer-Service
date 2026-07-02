package com.banking.mts.exception;

import com.banking.mts.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ProblemDetail> handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
        HttpStatus status = mapToHttpStatus(ex.getMessage());
        ProblemDetail problem = buildProblemDetail(status, ex.getMessage(), request);
        return ResponseEntity.status(status).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        ProblemDetail problem = buildProblemDetail(HttpStatus.BAD_REQUEST, detail, request);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        ProblemDetail problem = buildProblemDetail(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
        return ResponseEntity.badRequest().body(problem);
    }

    private ProblemDetail buildProblemDetail(HttpStatus status, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://errors.bank.local/" + status.getReasonPhrase().toLowerCase().replace(" ", "-")));
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("traceId", request.getHeader(RequestIdFilter.REQUEST_ID_HEADER));
        return problem;
    }

    private HttpStatus mapToHttpStatus(String message) {
        if (message == null) return HttpStatus.INTERNAL_SERVER_ERROR;
        String lower = message.toLowerCase();
        if (lower.contains("not found")) return HttpStatus.NOT_FOUND;
        if (lower.contains("not active") || lower.contains("insufficient balance")
                || lower.contains("self-transfer") || lower.contains("currency mismatch")
                || lower.contains("invalid account status") || lower.contains("cannot close")) {
            return HttpStatus.UNPROCESSABLE_ENTITY;
        }
        if (lower.contains("duplicate transfer") || lower.contains("idempotency conflict")) {
            return HttpStatus.CONFLICT;
        }
        if (lower.contains("account already exists")) return HttpStatus.CONFLICT;
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
