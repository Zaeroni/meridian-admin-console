package com.meridian.admin.service;

/** The request is valid on its own but not allowed in the resource's current state. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
