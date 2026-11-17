package com.ticketproject.webapp.dtos.requests;

import com.ticketproject.webapp.constants.AppConstants;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * CreateBlockedRegistrationRequest specifies what a valid request
 * by a logged in event host to block an attendee's registration
 * for an event should look like.
 * @param publicId the event's public ID
 * @param email the attendee's email address
 * @param reason why the attendee's registration was blocked (optional)
 */
@Schema(description = "Request body for blocking an attendee's registration to an event")
public record CreateBlockedRegistrationRequest
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
        description = "The attendee's email address",
        requiredMode = Schema.RequiredMode.REQUIRED,
        example = "jane.smith@example.com",
        maximum = "254"
    )
    @NotBlank(message = "Email address is required")
    @Email
    (
        // Without this regular expression, the @Email annotation will accept
        // email addresses without a top-level domain, which isn't want I want
        // because I can't send emails to addresses without a top-level domain.
        regexp = AppConstants.Database.EventHosts.Definitions.EMAIL_ADDRESS_REGEX,
        message = "Must be a valid email address"
    )
    @Size
    (
        max = AppConstants.Database.EventHosts.Sizes.MAX_EMAIL_LENGTH,
        message = "Email address must not exceed " + AppConstants.Database.EventHosts.Sizes.MAX_EMAIL_LENGTH + " characters"
    )
    String email,

    @Schema
    (
        description = "The (optional) message by the event host explaining why the attendee's registration was blocked.",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED,
        example = "Spam registration",
        maximum = "5000"
    )
    @Size
    (
        max = AppConstants.DTO.Tickets.Sizes.MAX_MESSAGE_LENGTH,
        message =
            "Message cannot be longer than " +
            AppConstants.DTO.Tickets.Sizes.MAX_MESSAGE_LENGTH +
            " characters"
    )
    String reason
)
{
}
