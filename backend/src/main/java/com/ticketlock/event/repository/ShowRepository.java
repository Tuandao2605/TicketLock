package com.ticketlock.event.repository;

import com.ticketlock.event.entity.Show;
import com.ticketlock.event.entity.ShowStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShowRepository extends JpaRepository<Show, UUID> {
    Optional<Show> findByIdAndStatus(UUID id, ShowStatus status);
    Page<Show> findByEventIdAndStatusOrderByStartsAtAscIdAsc(UUID eventId, ShowStatus status, Pageable pageable);

    @Query("select s.eventId from Show s where s.id = :id")
    Optional<UUID> findEventId(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Show s where s.id = :id")
    Optional<Show> findForUpdate(@Param("id") UUID id);
}
