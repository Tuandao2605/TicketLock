package com.ticketlock.event.repository;

import com.ticketlock.event.entity.Show;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShowRepository extends JpaRepository<Show, UUID> {
    @Query("select s.eventId from Show s where s.id = :id")
    Optional<UUID> findEventId(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Show s where s.id = :id")
    Optional<Show> findForUpdate(@Param("id") UUID id);
}
