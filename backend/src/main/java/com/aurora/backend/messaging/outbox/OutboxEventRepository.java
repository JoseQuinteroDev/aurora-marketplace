package com.aurora.backend.messaging.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Claim a batch of the oldest unpublished events for relaying.
     *
     * <p>A pessimistic write lock with {@code SKIP LOCKED} (Hibernate maps the
     * {@code -2} lock timeout to {@code FOR UPDATE SKIP LOCKED} on PostgreSQL)
     * makes the relay safe to run on multiple core instances: each picks a
     * disjoint set of rows instead of contending on the same ones.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select e from OutboxEvent e where e.status = :status order by e.createdAt asc")
    List<OutboxEvent> claimBatch(@Param("status") OutboxStatus status, Limit limit);

    long countByStatus(OutboxStatus status);

    /**
     * Bulk-deletes already-delivered rows older than {@code cutoff}. Keeps the outbox from
     * growing unbounded and, more importantly, bounds how long an event payload's sensitive
     * cleartext (e.g. a password-reset / email-verification token) lingers in the DB after the
     * event has been relayed (OWASP A07 residual). Bulk JPQL — bypasses the persistence context.
     */
    @Modifying
    @Query("delete from OutboxEvent e where e.status = :status and e.publishedAt is not null and e.publishedAt < :cutoff")
    int deleteByStatusAndPublishedAtBefore(@Param("status") OutboxStatus status, @Param("cutoff") Instant cutoff);
}
