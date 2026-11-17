package com.ticketproject.webapp.services.model;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketproject.webapp.model.repositories.BlockedRegistrationRepository;

/**
 * BlockedRegistrationService is a service used by controllers to handle
 * API route requests involving BlockedRegistration entities
 * (used to block attendees from specific events).
 */
@Service
@Transactional
public class BlockedRegistrationService
{
    // TODO: implement logic for a GET route to retrieve
    //       blocked registrations by event public ID.

    private final BlockedRegistrationRepository blockedRegistrationRepository;

    public BlockedRegistrationService
    (BlockedRegistrationRepository blockedRegistrationRepository)
    {
        this.blockedRegistrationRepository = blockedRegistrationRepository;
    }
}
