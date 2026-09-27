package com.hrportal.service;

import com.hrportal.domain.AuditEntry;
import com.hrportal.repository.AuditEntryRepository;
import com.hrportal.security.CurrentUser;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the audit trail. Entries join the caller's transaction, so an entry is only
 * stored if the change it describes is actually committed.
 */
@Service
public class AuditService {

    private final AuditEntryRepository entries;
    private final CurrentUser currentUser;
    private final Clock clock;

    public AuditService(AuditEntryRepository entries, CurrentUser currentUser, Clock clock) {
        this.entries = entries;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional
    public void record(String action, String entityType, Long entityId, String details) {
        recordAs(currentUser.username(), action, entityType, entityId, details);
    }

    @Transactional
    public void recordAs(String actor, String action, String entityType, Long entityId, String details) {
        String trimmed = details != null && details.length() > 2000 ? details.substring(0, 1997) + "..." : details;
        entries.save(new AuditEntry(LocalDateTime.now(clock), actor, action, entityType, entityId, trimmed));
    }

    @Transactional(readOnly = true)
    public Page<AuditEntry> search(String q, String entityType, int page) {
        String type = entityType == null || entityType.isBlank() ? null : entityType;
        return entries.search(q == null ? null : q.trim(), type, PageRequest.of(Math.max(page, 0), 50));
    }

    /** Collects "field: old → new" fragments for an update. */
    public static final class Changes {
        private final List<String> parts = new ArrayList<>();

        public Changes add(String field, Object before, Object after) {
            if (!Objects.equals(normalise(before), normalise(after))) {
                parts.add(field + ": " + display(before) + " → " + display(after));
            }
            return this;
        }

        public boolean isEmpty() {
            return parts.isEmpty();
        }

        @Override
        public String toString() {
            return String.join("; ", parts);
        }

        private static Object normalise(Object value) {
            if (value instanceof java.math.BigDecimal bd) {
                return bd.stripTrailingZeros();
            }
            if (value instanceof String s && s.isBlank()) {
                return null;
            }
            return value;
        }

        private static String display(Object value) {
            Object v = normalise(value);
            if (v instanceof java.math.BigDecimal bd) {
                return bd.toPlainString();
            }
            return v == null ? "(empty)" : v.toString();
        }
    }
}
