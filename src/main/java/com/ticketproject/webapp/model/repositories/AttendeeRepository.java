package com.ticketproject.webapp.model.repositories;

import com.ticketproject.webapp.model.entities.Attendee;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

/**
 * AttendeeRepository is used to perform CRUD operations with Attendee entities.
 */
@Repository
public interface AttendeeRepository extends JpaRepository<Attendee, Long>
{
    /**
     * Find and retrieve an Attendee entity based on their email blind index.
     * 
     * @param index an email blind index
     * @return an Optional<Attendee>
     */
    @Query("SELECT a from Attendee a WHERE a.emailBlindIndex = :index")
    Optional<Attendee> findByEmailIndex(@Param("index") byte[] index);

    /**
     * Find and retrieve a list of Attendee entities using
     * a list of email blind indexes.
     * @param emailBlindIndexes a list of email blind indexes
     * @return a list of Attendee entities
     */
    @Query("SELECT a FROM Attendee a WHERE a.emailBlindIndex IN :emailBlindIndexes")
    List<Attendee> findAllByEmailIndexes
    (
        @Param("emailBlindIndexes")
        List<byte[]> emailBlindIndexes
    );

    /**
     * Check whether an Attendee entity with a specified email blind index exists.
     * 
     * @param index an email blind index
     * @return true if an Attendee entity with the specified
     * email blind index exists, false otherwise
     */
    @Query("SELECT COUNT(a) > 0 FROM Attendee a WHERE a.emailBlindIndex = :index")
    boolean existsByEmailIndex(@Param("index") byte[] index);
}
