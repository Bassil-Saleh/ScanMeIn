package com.ticketproject.webapp.controllers;

import com.ticketproject.webapp.services.model.BlockedRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.RestController;

import com.ticketproject.webapp.constants.ApiPaths;
import com.ticketproject.webapp.constants.AppConstants;
import com.ticketproject.webapp.dtos.requests.CreateBlockedRegistrationsRequest;
import com.ticketproject.webapp.dtos.requests.DeleteBlockedRegistrationsRequest;
import com.ticketproject.webapp.dtos.responses.GetBlockedRegistrationsByEventPublicIdResponse;
import com.ticketproject.webapp.dtos.responses.SingleMessageResponse;
import com.ticketproject.webapp.exceptions.UnauthorizedException;
import com.ticketproject.webapp.model.entities.EventHost;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

/**
 * BlockedRegistrationController is a REST controller that routes requests
 * concerning BlockedRegistration entities to different API routes.
 */
@RestController
@RequestMapping(ApiPaths.BASE + ApiPaths.BlockedRegistrations.ROOT)
@Tag(name = "BlockedRegistrations", description = "Endpoints for blocked registration management")
public class BlockedRegistrationController
{
    private final BlockedRegistrationService blockedRegistrationService;

    BlockedRegistrationController(BlockedRegistrationService blockedRegistrationService)
    {
        this.blockedRegistrationService = blockedRegistrationService;
    }

    /**
     * Handles a request to let a logged in event host retrieve a list of records
     * on blocked registrations for an event identified by its public ID.
     * Only the event host who created the event should be allowed
     * to manage those records.
     * @param publicId the event's public ID
     * @param servletRequest the HTTP Servlet request
     * @return a GetBlockedRegistrationsByEventPublicIdResponse on success
     */
    @Operation
    (
        summary = "Retrieve info on a list of blocked registrations for a specific event",
        description = 
            "Retrieves a list of records on blocked registrations for a specific event. " +
            "Only the event host who created the event should be allowed to see those records. " +
            "Requires authentication."
    )
    @GetMapping(ApiPaths.BlockedRegistrations.BY_EVENT_PUBLIC_ID)
    @ResponseStatus(HttpStatus.OK)
    public GetBlockedRegistrationsByEventPublicIdResponse getBlockedRegistrationsByEventPublicId
    (
        @PathVariable String publicId,
        HttpServletRequest servletRequest
    )
    {
        EventHost eventHost = (EventHost) servletRequest.getAttribute(AppConstants.Jwt.Filter.AUTHENTICATED_EVENT_HOST_ATTRIBUTE);
        if (eventHost == null)
        {
            throw new UnauthorizedException("Authentication required");
        }
        return blockedRegistrationService.getBlockedRegistrationsByEventPublicId(eventHost, publicId);
    }

    /**
     * Handles a request to let a logged in event host block multiple attendee registrations
     * from a specific event identified by the event's public ID.
     * Only the event host who created the event should be allowed to do this.
     * @param request the request body
     * @param servletRequest the HTTP Servlet request
     * @return a SingleMessageResponse on success
     */
    @Operation
    (
        summary = "Block multiple attendee registrations from a specific event",
        description =
            "Block multiple attendee registrations from a specific event. " +
            "Only the event host who created the event should be allowed to do this. " +
            "Requires authentication."
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SingleMessageResponse blockRegistrations
    (
        @Valid
        @RequestBody CreateBlockedRegistrationsRequest request,
        HttpServletRequest servletRequest
    )
    {
        EventHost eventHost = (EventHost) servletRequest.getAttribute(AppConstants.Jwt.Filter.AUTHENTICATED_EVENT_HOST_ATTRIBUTE);
        if (eventHost == null)
        {
            throw new UnauthorizedException("Authentication required");
        }
        return blockedRegistrationService.blockRegistrations(eventHost, request);
    }

    /**
     * Handles a request to let a logged in event host unblock multiple attendee registrations
     * from a specific event identified by the event's public ID.
     * Only the event host who created the event should be allowed to do this.
     * @param request the request body
     * @param servletRequest the HTTP Servlet request
     * @return a SingleMessageResponse on success
     */
    @Operation
    (
        summary = "Unblock multiple attendee registrations from a specific event",
        description =
            "Unblock multiple attendee registrations from a specific event. " +
            "Only the event host who created the event should be allowed to do this. " +
            "Requires authentication."
    )
    @DeleteMapping
    @ResponseStatus(HttpStatus.OK)
    public SingleMessageResponse unblockRegistrations
    (
        @Valid
        @RequestBody DeleteBlockedRegistrationsRequest request,
        HttpServletRequest servletRequest
    )
    {
        EventHost eventHost = (EventHost) servletRequest.getAttribute(AppConstants.Jwt.Filter.AUTHENTICATED_EVENT_HOST_ATTRIBUTE);
        if (eventHost == null)
        {
            throw new UnauthorizedException("Authentication required");
        }
        return blockedRegistrationService.unblockRegistrations(eventHost, request);
    }
}
