package com.benly.pt.exception;

import com.benly.global.common.ApiResponse;
import com.benly.global.exception.BusinessException;
import com.benly.pt.controller.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(
        assignableTypes = {
                PtOptionController.class,
                PtSessionController.class
        }
)
public class PtExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> business(
            BusinessException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(exception.getErrorCode().getStatus())
                .body(ApiResponse.error(
                        exception.getErrorCode().getStatus().value(),
                        exception.getMessage(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResponse<Void>> invalid(
            Exception exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(
                        400,
                        "PT 요청 값이 올바르지 않습니다.",
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler({
            ConcurrencyFailureException.class,
            DataIntegrityViolationException.class
    })
    public ResponseEntity<ApiResponse<Void>> busy(
            Exception exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(409)
                .body(ApiResponse.error(
                        409,
                        "요청이 처리 중입니다. 같은 요청 ID로 다시 시도해주세요.",
                        request.getRequestURI()
                ));
    }
}