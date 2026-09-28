package com.solo.equity.service;

/** Referenced grant/node/request does not exist. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
