package com.ps.authservice.exception;

public class UserExistsAlreadyException extends RuntimeException
{
    public UserExistsAlreadyException(String message) {
        super(message);
    }
}
