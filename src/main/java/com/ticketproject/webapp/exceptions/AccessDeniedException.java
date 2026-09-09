package com.ticketproject.webapp.exceptions;

/**
 * AccessDeniedException is thrown when an operation is attempted with an
 * email address that is not permitted by the configured allowlist.
 */
public class AccessDeniedException extends RuntimeException
{
    /**
     * Constructs a new AccessDeniedException with the specified detail message.
     * @param message the detail message
     */
    public AccessDeniedException(String message)
    {
        super(message);
    }
}
