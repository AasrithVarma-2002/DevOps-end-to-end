package com.hrportal.repository;

import com.hrportal.domain.AuditEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Deliberately extends {@link Repository}, not JpaRepository: no delete or update methods exist. */
public interface AuditEntryRepository extends Repository<AuditEntry, Long> {

    AuditEntry save(AuditEntry entry);

    @Query("""
            select a from AuditEntry a
            where (:q is null or :q = ''
                   or lower(a.actor) like lower(concat('%', :q, '%'))
                   or lower(a.action) like lower(concat('%', :q, '%'))
                   or lower(a.details) like lower(concat('%', :q, '%')))
              and (:entityType is null or a.entityType = :entityType)
            order by a.occurredAt desc, a.id desc
            """)
    Page<AuditEntry> search(@Param("q") String q, @Param("entityType") String entityType, Pageable pageable);

    long count();
}
