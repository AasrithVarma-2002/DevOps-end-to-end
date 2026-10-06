package com.hrportal.domain;

import java.util.Arrays;
import java.util.List;

/**
 * Kinds of employee document. Employees upload proofs (the required ones make up the onboarding
 * checklist); HR uploads letters for the employee.
 */
public enum DocumentType {
    ID_PROOF("Identity proof (Aadhaar or passport)", Source.EMPLOYEE, true),
    PAN_CARD("PAN card", Source.EMPLOYEE, true),
    ADDRESS_PROOF("Address proof", Source.EMPLOYEE, true),
    EDUCATION("Highest education certificate", Source.EMPLOYEE, true),
    BANK_DETAILS("Bank details (cancelled cheque or passbook)", Source.EMPLOYEE, true),
    PREVIOUS_EMPLOYMENT("Relieving letter from previous employer", Source.EMPLOYEE, false),
    OTHER("Other document", Source.EMPLOYEE, false),

    OFFER_LETTER("Offer letter", Source.HR, false),
    APPRAISAL_LETTER("Appraisal letter", Source.HR, false),
    EXPERIENCE_LETTER("Experience / relieving letter", Source.HR, false),
    HR_LETTER("Other letter from HR", Source.HR, false);

    /** Who uploads this kind of document. */
    public enum Source { EMPLOYEE, HR }

    private final String label;
    private final Source source;
    private final boolean required;

    DocumentType(String label, Source source, boolean required) {
        this.label = label;
        this.source = source;
        this.required = required;
    }

    public String getLabel() {
        return label;
    }

    public boolean isFromHr() {
        return source == Source.HR;
    }

    /** Part of the onboarding checklist: every employee should have a verified copy. */
    public boolean isRequired() {
        return required;
    }

    public static List<DocumentType> employeeTypes() {
        return Arrays.stream(values()).filter(t -> !t.isFromHr()).toList();
    }

    public static List<DocumentType> hrTypes() {
        return Arrays.stream(values()).filter(DocumentType::isFromHr).toList();
    }

    public static List<DocumentType> requiredTypes() {
        return Arrays.stream(values()).filter(DocumentType::isRequired).toList();
    }
}
