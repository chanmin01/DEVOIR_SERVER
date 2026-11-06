package com.benly.pt.exception;

import com.benly.global.common.ApiResponse;
import com.benly.global.exception.BusinessException;
import com.benly.global.exception.ErrorCode;
import com.benly.pt.controller.PtOptionController;
import com.benly.pt.controller.PtSessionController;
import com.benly.pt.controller.PtInterviewController;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(
        assignableTypes = {
                PtOptionController.class,
                PtSessionController.class,
                PtInterviewController.class
        }
)
public class PtExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> business(
            BusinessException exception,
            HttpServletRequest request
    ) {
        return error(exception.getErrorCode(), request);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class
    })
    public ResponseEntity<ApiResponse<Void>> invalid(
            Exception exception,
            HttpServletRequest request
    ) {
        return error(PtErrorCode.INVALID_REQUEST, request);
    }

    @ExceptionHandler({
            ConcurrencyFailureException.class,
            DataIntegrityViolationException.class
    })
    public ResponseEntity<ApiResponse<Void>> busy(
            Exception exception,
            HttpServletRequest request
    ) {
        return error(PtErrorCode.REQUEST_IN_PROGRESS, request);
    }

    private ResponseEntity<ApiResponse<Void>> error(
            ErrorCode errorCode,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(
                        errorCode.getStatus().value(),
                        errorCode.getMessage(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> audioSize(MaxUploadSizeExceededException exception,
                                                       HttpServletRequest request) {
        return error(PtErrorCode.AUDIO_TOO_LARGE, request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> audioType(HttpMediaTypeNotSupportedException exception,
                                                       HttpServletRequest request) {
        return error(PtErrorCode.REQUEST_MEDIA_TYPE_NOT_SUPPORTED, request);
    }
}