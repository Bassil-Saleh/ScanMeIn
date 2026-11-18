package com.ticketproject.webapp.exceptions;

/**
 * AttendeeBlockedException is thrown when an attempt is made to
 * perform an operation with an attendee who has been blocked
 * from an event (i.e. attempting to scan an attendee's ticket
 * when they've been blocked from the event).
 */
public class AttendeeBlockedException extends RuntimeException
{
    /**
     * Constructs a new AttendeeBlockedException with the specified detail message.
     * @param message the detail message
     */
    public AttendeeBlockedException(String message)
    {
        super(message);
    }
}
