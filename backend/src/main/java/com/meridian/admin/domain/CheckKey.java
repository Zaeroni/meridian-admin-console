package com.meridian.admin.domain;

/** The fixed set of verification checks every application carries. */
public enum CheckKey {
    LICENSE_FORMAT("License number format is valid for the license state", true),
    LICENSE_NOT_EXPIRED("License is not expired", true),
    DOCUMENTS_UPLOADED("All required documents are uploaded", true),
    DELIVERY_RADIUS_SET("Delivery radius is set and within limits", true),
    BUSINESS_NAME_MATCH("Business name matches the name on the license", false);

    private final String label;
    private final boolean automatic;

    CheckKey(String label, boolean automatic) {
        this.label = label;
        this.automatic = automatic;
    }

    public String label() {
        return label;
    }

    public boolean automatic() {
        return automatic;
    }
}
