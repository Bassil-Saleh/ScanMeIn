package com.ticketproject.webapp.dtos.responses;
import com.ticketproject.webapp.constants.AppConstants;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * BlockedRegistrationInfo specifies what a single record of info
 * on a blocked registration for an event should look like.
 * Used when retrieving info on a list of people who have been
 * blocked from registering for an event by a logged in event host.
 * @param firstName the attendee's first name
 * @param middleName the attendee's middle name
 * @param lastName the attendee's last name
 * @param email the attendee's email address
 * @param blockedByEmail the email address of the event host
 *                       who blocked the attendee
 * @param reason why the attendee was blocked (optional)
 * @param created when the attendee was blocked
 * @param revoked when the attendee's block was revoked
 */
public record BlockedRegistrationInfo
(
    @Schema
    (
        description = "The attendee's first name",
        requiredMode = Schema.RequiredMode.REQUIRED,
        example = "Jane",
        maximum = "100"
    )
    @NotBlank(message = "First name is required")
    @Size
    (
        max = AppConstants.Database.EventHosts.Sizes.MAX_NAME_LENGTH,
        message = "First name must not exceed " + AppConstants.Database.EventHosts.Sizes.MAX_NAME_LENGTH + " characters"
    )
    String firstName,

    @Schema
    (
        description = "The attendee's middle name (optional)",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED,
        example = "Marie",
        maximum = "100"
    )
    @Size
    (
        max = AppConstants.Database.EventHosts.Sizes.MAX_NAME_LENGTH,
        message = "Middle name must not exceed " + AppConstants.Database.EventHosts.Sizes.MAX_NAME_LENGTH + " characters"
    )
    String middleName,

    @Schema
    (
        description = "The attendee's last name",
        requiredMode = Schema.RequiredMode.REQUIRED,
        example = "Smith",
        maximum = "100"
    )
    @Size
    (
        max = AppConstants.Database.EventHosts.Sizes.MAX_NAME_LENGTH,
        message = "Last name must not exceed " + AppConstants.Database.EventHosts.Sizes.MAX_NAME_LENGTH + " characters"
    )
    String lastName,

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
    String email,

    @Schema
    (
        description = "The email address of the event host who blocked the attendee",
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
    String blockedByEmail,

    @Schema
    (
        description = "The optional message sent to the attendee explaining why their registration was blocked.",
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
    String reason,

    @Schema
    (
        description = "When the registration block was first created",
        requiredMode = Schema.RequiredMode.REQUIRED,
        example = "2026-08-14T10:30:00"
    )
    @NotNull(message = "Creation date/time is required")
    LocalDateTime created,

    @Schema
    (
        description = "When the registration block was revoked (if at all)",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED,
        example = "2026-08-14T10:30:00"
    )
    LocalDateTime revoked
)
{
}