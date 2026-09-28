package com.solo.equity.service;

/** Business rule rejection, e.g. exercising more than the exercisable pool. */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
