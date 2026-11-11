package com.ticketproject.webapp.controllers;

import com.ticketproject.webapp.constants.ApiPaths;
import com.ticketproject.webapp.constants.AppConstants;
import com.ticketproject.webapp.dtos.requests.CreatePrivateEventInvitationRequest;
import com.ticketproject.webapp.dtos.requests.CreatePublicEventRegistrationRequest;
import com.ticketproject.webapp.dtos.requests.ResendEventTicketEmailRequest;
import com.ticketproject.webapp.dtos.responses.SingleMessageResponse;
import com.ticketproject.webapp.model.entities.EventHost;
import com.ticketproject.webapp.services.model.AttendeeService;
import com.ticketproject.webapp.exceptions.UnauthorizedException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


/**
 * AttendeeController is a REST controller that routes requests
 * concerning Attendee entities to different API routes.
 */
@RestController
@RequestMapping(ApiPaths.BASE + ApiPaths.Attendees.ROOT)
@Tag(name = "Attendees", description = "Endpoints for attendee registration and management")
public class AttendeeController
{
    private final AttendeeService attendeeService;

    public AttendeeController(AttendeeService attendeeService)
    {
        this.attendeeService = attendeeService;
    }

    /**
     * Handles a request to let a user register for a public event.
     * @param request the request body
     * @return a SingleMessageResponse on success
     */
    @Operation
    (
        summary = "Register for a public event",
        description =
            "Allows an attendee to register for a public event by providing " +
            "the event's public ID along with their personal details. " +
            "Upon successful registration, a ticket is generated and sent " +
            "to the attendee's email address. " +
            "This endpoint does not require authentication."
    )
    @SecurityRequirements
    @PostMapping(ApiPaths.Attendees.REGISTRATION)
    @ResponseStatus(HttpStatus.CREATED)
    public SingleMessageResponse createPublicEventRegistration
    (
        @Valid
        @RequestBody CreatePublicEventRegistrationRequest request
    )
    {
        return attendeeService.createPublicEventRegistration(request);
    }

    /**
     * Handles a request to let a logged in event host create
     * an invitation for a private event. Only the event host
     * who created the event should be able to create invitations
     * for the event.
     * @param request the request body
     * @param servletRequest the HTTP Servlet request
     * @return a SingleMessageResponse on success
     */
    @Operation
    (
        summary = "Create an invitation for a private event",
        description =
            "Create an invitation for a private event. Upon success, " +
            "a ticket is generated, and an invitation is sent to the " +
            "recipient's email address. Requires authentication. Only the " +
            "event host who created the event can invite someone else to the event."
    )
    @PostMapping(ApiPaths.Attendees.INVITATION)
    @ResponseStatus(HttpStatus.CREATED)
    public SingleMessageResponse createPrivateEventInvitation
    (
        @Valid
        @RequestBody CreatePrivateEventInvitationRequest request,
        HttpServletRequest servletRequest
    )
    {
        EventHost eventHost = (EventHost) servletRequest.getAttribute(AppConstants.Jwt.Filter.AUTHENTICATED_EVENT_HOST_ATTRIBUTE);
        if (eventHost == null)
        {
            throw new UnauthorizedException("Authentication required");
        }
        return attendeeService.createPrivateEventInvitation(eventHost, request);
    }

    /**
     * Handles a request to let an attendee have their ticket for
     * a specific event be resent to their email address (in case
     * they lost the original email message containing their ticket).
     * @param publicId the event's public ID
     * @param request the request body
     * @return a SingleMessageResponse on success
     */
    @Operation
    (
        summary = "Resend a ticket email to a registered attendee for a specific event",
        description =
            "Given an attendee's email address and an event's public ID, " +
            "resend the attendee's ticket to their email address."
    )
    @PostMapping(ApiPaths.Attendees.RESENDS + ApiPaths.Attendees.BY_EVENT_PUBLIC_ID)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SingleMessageResponse resendEventTicketEmail
    (
        @PathVariable String publicId,
        @Valid
        @RequestBody ResendEventTicketEmailRequest request
    )
    {
        return attendeeService.resendEventTicketEmail(request, publicId);
    }
}
