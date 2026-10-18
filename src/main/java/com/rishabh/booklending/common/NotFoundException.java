package com.rishabh.booklending.common;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource, Long id) {
        super(resource + " " + id + " not found");
    }

    public NotFoundException(String message) {
        super(message);
    }
}