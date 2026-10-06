package com.hrportal.service;

import com.hrportal.domain.DocumentStatus;
import com.hrportal.domain.DocumentType;
import com.hrportal.domain.Employee;
import com.hrportal.domain.EmployeeDocument;
import com.hrportal.domain.EmployeeStatus;
import com.hrportal.domain.Role;
import com.hrportal.repository.EmployeeDocumentRepository;
import com.hrportal.security.CurrentUser;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Employee documents.
 * <pre>
 *   employee uploads a proof ─► PENDING ─(HR verifies)─► VERIFIED
 *                                       └(HR rejects, with a reason)─► REJECTED ─► employee uploads again
 *   HR uploads a letter for the employee ─► ISSUED
 * </pre>
 * Files are checked by their content (PDF, PNG or JPEG only, at most 5 MB), stored under a random
 * key and only ever shown to the employee themselves and to HR.
 */
@Service
@Transactional
public class EmployeeDocumentService {

    public static final long MAX_BYTES = 5L * 1024 * 1024;

    private final EmployeeDocumentRepository documents;
    private final EmployeeService employees;
    private final DocumentStorage storage;
    private final NotificationService notifications;
    private final AuditService audit;
    private final CurrentUser currentUser;
    private final Clock clock;

    public EmployeeDocumentService(EmployeeDocumentRepository documents, EmployeeService employees,
                                   DocumentStorage storage, NotificationService notifications, AuditService audit,
                                   CurrentUser currentUser, Clock clock) {
        this.documents = documents;
        this.employees = employees;
        this.storage = storage;
        this.notifications = notifications;
        this.audit = audit;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /** A file as it arrived: the name the browser sent and the bytes. */
    public record Upload(String originalName, byte[] content) {
    }

    /** One line of the onboarding checklist: the newest upload of a required type, if any. */
    public record ChecklistItem(DocumentType type, EmployeeDocument latest) {
        public boolean isDone() {
            return latest != null && latest.getStatus() == DocumentStatus.VERIFIED;
        }
    }

    public record Checklist(Employee employee, List<ChecklistItem> items) {
        public long done() {
            return items.stream().filter(ChecklistItem::isDone).count();
        }

        public int total() {
            return items.size();
        }

        public boolean isComplete() {
            return done() == total();
        }

        public List<ChecklistItem> outstanding() {
            return items.stream().filter(i -> !i.isDone()).toList();
        }
    }

    /** Either a link to the file in S3 or, for local storage, the bytes themselves. */
    public record Download(EmployeeDocument document, URI link, byte[] content) {
    }

    // ------------------------------------------------------------------ employee

    public EmployeeDocument upload(Employee employee, DocumentType type, Upload file) {
        if (!employee.isActive()) {
            throw new BusinessRuleException("Employees who have left cannot upload documents");
        }
        if (type == null || type.isFromHr()) {
            throw new BusinessRuleException("Choose which document you are uploading");
        }
        boolean alreadyWaiting = documents.findByEmployee(employee.getId()).stream()
                .anyMatch(d -> d.getType() == type && d.getStatus() == DocumentStatus.PENDING);
        if (alreadyWaiting) {
            throw new BusinessRuleException("Your " + type.getLabel().toLowerCase()
                    + " is already waiting for HR. You can upload it again if HR rejects it.");
        }
        EmployeeDocument d = store(employee, type, DocumentStatus.PENDING, file);
        audit.record("DOCUMENT_UPLOADED", "EmployeeDocument", d.getId(), describe(d));
        notifications.notifyHr(employee.getFullName() + " uploaded their " + type.getLabel().toLowerCase()
                + " for verification", "/hr/documents");
        return d;
    }

    @Transactional(readOnly = true)
    public List<EmployeeDocument> forEmployee(Long employeeId) {
        return documents.findByEmployee(employeeId);
    }

    @Transactional(readOnly = true)
    public Checklist checklist(Employee employee) {
        return checklist(employee, documents.findByEmployee(employee.getId()));
    }

    // ------------------------------------------------------------------ HR

    @Transactional(readOnly = true)
    public List<EmployeeDocument> pending() {
        return documents.findByStatus(DocumentStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public long pendingCount() {
        return documents.countByStatus(DocumentStatus.PENDING);
    }

    /** The checklist of every current employee, those with the most outstanding first. */
    @Transactional(readOnly = true)
    public List<Checklist> checklists() {
        Map<Long, List<EmployeeDocument>> byEmployee = documents.findByEmployeeStatus(EmployeeStatus.ACTIVE).stream()
                .collect(Collectors.groupingBy(EmployeeDocument::getEmployeeId, LinkedHashMap::new, Collectors.toList()));
        return employees.active().stream()
                .map(e -> checklist(e, byEmployee.getOrDefault(e.getId(), List.of())))
                .sorted((a, b) -> Long.compare(a.done(), b.done()))
                .toList();
    }

    public EmployeeDocument verify(Long documentId, String note) {
        EmployeeDocument d = requirePendingForReview(documentId);
        d.review(DocumentStatus.VERIFIED, currentUser.username(), LocalDateTime.now(clock), blankToNull(note));
        audit.record("DOCUMENT_VERIFIED", "EmployeeDocument", documentId, describe(d));
        notifications.notifyEmployee(d.getEmployee(), "HR verified your " + d.getType().getLabel().toLowerCase(),
                "/documents");
        return d;
    }

    public EmployeeDocument reject(Long documentId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("Tell the employee why the document was rejected");
        }
        EmployeeDocument d = requirePendingForReview(documentId);
        d.review(DocumentStatus.REJECTED, currentUser.username(), LocalDateTime.now(clock), clip(reason.trim(), 500));
        audit.record("DOCUMENT_REJECTED", "EmployeeDocument", documentId, describe(d) + ": " + d.getReviewNote());
        notifications.notifyEmployee(d.getEmployee(), "HR rejected your " + d.getType().getLabel().toLowerCase()
                + ": " + d.getReviewNote() + ". Please upload it again.", "/documents");
        return d;
    }

    /** HR gives the employee a letter. Also for people who have left (an experience letter, say). */
    public EmployeeDocument issue(Long employeeId, DocumentType type, Upload file) {
        if (type == null || !type.isFromHr()) {
            throw new BusinessRuleException("Choose which letter you are issuing");
        }
        Employee employee = employees.get(employeeId);
        EmployeeDocument d = store(employee, type, DocumentStatus.ISSUED, file);
        audit.record("DOCUMENT_ISSUED", "EmployeeDocument", d.getId(), describe(d));
        notifications.notifyEmployee(employee, "HR shared a document with you: " + type.getLabel(), "/documents");
        return d;
    }

    // ------------------------------------------------------------------ download

    /** Employees get their own documents, HR gets anyone's. HR looking at someone else's is audited. */
    public Download download(Long documentId) {
        EmployeeDocument d = get(documentId);
        boolean isOwner = d.getEmployeeId().equals(currentUser.principal().map(p -> p.getEmployeeId()).orElse(null));
        if (!isOwner && !currentUser.hasAtLeast(Role.HR_ADMIN)) {
            throw new AccessDeniedException("You can only open your own documents");
        }
        if (!isOwner) {
            audit.record("DOCUMENT_VIEWED", "EmployeeDocument", documentId, describe(d));
        }
        return storage.downloadLink(d.getDocumentKey(), d.getFileName(), d.getContentType())
                .map(link -> new Download(d, link, null))
                .orElseGet(() -> new Download(d, null, storage.get(d.getDocumentKey())));
    }

    @Transactional(readOnly = true)
    public EmployeeDocument get(Long documentId) {
        return documents.findById(documentId).orElseThrow(() -> new NotFoundException("Document not found"));
    }

    // ------------------------------------------------------------------ file checks

    /** The kinds of file accepted, recognised by their first bytes ("magic numbers"). */
    enum FileKind {
        PDF("application/pdf", "pdf", new byte[]{'%', 'P', 'D', 'F', '-'}),
        PNG("image/png", "png", new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'}),
        JPEG("image/jpeg", "jpg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});

        final String contentType;
        final String extension;
        private final byte[] magic;

        FileKind(String contentType, String extension, byte[] magic) {
            this.contentType = contentType;
            this.extension = extension;
            this.magic = magic;
        }

        static FileKind detect(byte[] content) {
            return Arrays.stream(values()).filter(k -> k.matches(content)).findFirst().orElse(null);
        }

        private boolean matches(byte[] content) {
            return content.length >= magic.length && Arrays.equals(content, 0, magic.length, magic, 0, magic.length);
        }
    }

    private EmployeeDocument store(Employee employee, DocumentType type, DocumentStatus status, Upload file) {
        byte[] content = file == null ? null : file.content();
        if (content == null || content.length == 0) {
            throw new BusinessRuleException("Choose a file to upload");
        }
        if (content.length > MAX_BYTES) {
            throw new BusinessRuleException("The file is larger than 5 MB. Please upload a smaller scan or photo.");
        }
        // the browser's name and type can be anything; the bytes decide what the file is
        FileKind kind = FileKind.detect(content);
        if (kind == null) {
            throw new BusinessRuleException("Only PDF, PNG and JPEG files can be uploaded");
        }
        // random key: nothing about the person or file can be guessed from it, and uploads never overwrite each other
        String key = "documents/" + employee.getEmployeeCode().replaceAll("[^A-Za-z0-9_-]", "_") + "/"
                + UUID.randomUUID() + "." + kind.extension;
        storage.put(key, content, kind.contentType);
        return documents.save(new EmployeeDocument(employee, type, status, cleanName(file.originalName(), kind),
                kind.contentType, content.length, key, currentUser.username(), LocalDateTime.now(clock)));
    }

    /** "C:\scans\my PAN<1>.PDF" → "my PAN_1_.PDF": no folders, no odd characters, sensible length. */
    static String cleanName(String original, FileKind kind) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1)
                .replaceAll("[^\\p{L}\\p{N} ._()-]", "_")
                .replaceAll("\\s+", " ")
                .trim();
        if (name.isEmpty() || name.chars().allMatch(c -> c == '.' || c == '_')) {
            name = "document";
        }
        // keep the extension only if it matches the real content ("photo.pdf" that is a PNG → "photo.pdf.png")
        if (!name.toLowerCase().matches(".*\\." + (kind == FileKind.JPEG ? "jpe?g" : kind.extension) + "$")) {
            name = name + "." + kind.extension;
        }
        return name.length() <= 120 ? name : name.substring(0, 100) + "…" + name.substring(name.length() - 19);
    }

    // ------------------------------------------------------------------ helpers

    private EmployeeDocument requirePendingForReview(Long documentId) {
        EmployeeDocument d = get(documentId);
        if (d.getStatus() != DocumentStatus.PENDING) {
            throw new BusinessRuleException("This document has already been reviewed (" + d.getStatus().getLabel() + ")");
        }
        if (d.getEmployeeId().equals(currentUser.principal().map(p -> p.getEmployeeId()).orElse(null))) {
            throw new BusinessRuleException("You cannot verify your own documents. Another HR admin has to.");
        }
        return d;
    }

    private static Checklist checklist(Employee employee, List<EmployeeDocument> newestFirst) {
        List<ChecklistItem> items = DocumentType.requiredTypes().stream()
                .map(t -> new ChecklistItem(t, newestFirst.stream().filter(d -> d.getType() == t).findFirst().orElse(null)))
                .toList();
        return new Checklist(employee, items);
    }

    private static String describe(EmployeeDocument d) {
        return d.getEmployeeName() + ": " + d.getType().getLabel() + " (" + d.getFileName() + ", " + d.getSizeLabel() + ")";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : clip(s.trim(), 500);
    }

    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
