package com.ticketlock.event.repository;

import com.ticketlock.event.entity.Event;
import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EventRepository extends JpaRepository<Event, UUID> {
    @Query("select e from Event e where exists (select s.id from Show s where s.eventId=e.id and s.status=com.ticketlock.event.entity.ShowStatus.PUBLISHED) order by e.id")
    Page<Event> findPublishedEvents(Pageable pageable);

    @Query("select e from Event e where e.id=:id and exists (select s.id from Show s where s.eventId=e.id and s.status=com.ticketlock.event.entity.ShowStatus.PUBLISHED)")
    Optional<Event> findPublishedById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findForUpdate(@Param("id") UUID id);
}
