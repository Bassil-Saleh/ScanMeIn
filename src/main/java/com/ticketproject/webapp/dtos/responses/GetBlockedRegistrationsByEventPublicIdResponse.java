package com.ticketproject.webapp.dtos.responses;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * GetBlockedRegistrationsByEventPublicIdResponse specifies what
 * a response to a successful request to retrieve info on a
 * list of blocked registrations for a specific event should look like.
 * @param blockedRegistrations a list of records containing info on
 * blocked registrations for a specific event
 */
@Schema(description = "Response body containing a list of blocked registrations for a specific event")
public record GetBlockedRegistrationsByEventPublicIdResponse
(
    @Schema
    (
        description = "A list of records containing info about each blocked registration for a specific event",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    List<BlockedRegistrationInfo> blockedRegistrations
)
{
}
