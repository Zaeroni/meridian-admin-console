package com.meridian.admin.domain;

public enum DocumentType {
    PHARMACY_LICENSE("Pharmacy license"),
    BUSINESS_REGISTRATION("Business registration"),
    OWNER_ID("Owner ID");

    private final String label;

    DocumentType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
