package com.ticketproject.webapp.model.repositories;

import com.ticketproject.webapp.model.entities.BlockedRegistration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * BlockedRegistrationRepository is used to perform CRUD operations with BlockedRegistration entities.
 */
@Repository
public interface BlockedRegistrationRepository extends JpaRepository<BlockedRegistration, Long>
{
    /**
     * Find and retrieve a list of blocked registrations for an event.
     * To avoid triggering additional queries, each block's attendee
     * and the event host who authored the block are eagerly fetched.
     * @param eventId the event's ID (not its public ID)
     * @return a list of BlockedRegistration entities
     */
    @Query
    (
        "SELECT b FROM BlockedRegistration b " + 
        "JOIN FETCH b.attendee " +
        "JOIN FETCH b.blockedBy " +
        "WHERE b.event.id = :eventId"
    )
    List<BlockedRegistration> findAllByEventId(@Param("eventId") Long eventId);
}
