package com.ps.authservice.exception;


import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler
{

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,String>> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex)
    {
        Map<String,String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> errors.put(error.getField(),error.getDefaultMessage()));


        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    @ExceptionHandler(UserExistsAlreadyException.class)
    public ResponseEntity<Map<String,String>> handleEmailAlreadyExistsException(UserExistsAlreadyException ex)
    {
        Map<String,String> errors = new HashMap<>();
        log.warn("UserAreadyExistsException: ",ex.getMessage());
        errors.put("Message","User Already Exists");
        errors.put("Exception Message",ex.getMessage());

        return  ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }
}
