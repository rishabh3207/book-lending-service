package com.rishabh.booklending.common;

public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}