package com.ticketproject.webapp.dtos.requests;

import com.ticketproject.webapp.constants.AppConstants;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * DeleteBlockedRegistrationsRequest specifies what a valid request
 * by a logged in event host to unblock multiple attendee registrations
 * from a single event should look like.
 * @param publicId the event's public ID
 * @param emails list of emails that map to attendees to unblock from the event
 */
@Schema(description = "Request body for unblocking multiple attendee registrations to an event")
public record DeleteBlockedRegistrationsRequest
(
    @Schema
    (
        description = "The public ID of the event",
        requiredMode = Schema.RequiredMode.REQUIRED,
        example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
        maximum = "36"
    )
    @NotBlank(message = "Event public id cannot be blank")
    @Size
    (
        max = AppConstants.Database.Events.Sizes.PUBLIC_ID_LENGTH,
        message = "Event public id length cannot be longer than " +
        AppConstants.Database.Events.Sizes.PUBLIC_ID_LENGTH +
        " characters"
    )
    String publicId,

    @Schema
    (
        description = "The list of emails which map to attendees of the event",
        requiredMode = Schema.RequiredMode.REQUIRED,
        example = "[\"jane.smith@example.com\", \"tom.smith@example.com\"]"
    )
    @NotNull(message = "List of emails cannot be null")
    @Size
    (
        min = 1,
        message = "List of emails must contain at least 1 email"
    )
    List<String> emails
)
{
}