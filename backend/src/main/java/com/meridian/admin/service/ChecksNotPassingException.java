package com.meridian.admin.service;

import java.util.List;

/** Thrown when an approval is attempted while a required check is not passing. */
public class ChecksNotPassingException extends ConflictException {

    private final List<String> blockers;

    public ChecksNotPassingException(List<String> blockers) {
        super("Cannot approve: " + blockers.size() + " required check(s) are not passing");
        this.blockers = blockers;
    }

    public List<String> getBlockers() {
        return blockers;
    }
}
