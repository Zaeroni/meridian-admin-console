package com.meridian.admin.config;

import com.meridian.admin.domain.Role;
import java.util.UUID;

/** The authenticated caller, as placed in the security context by the JWT filter. */
public record AuthUser(UUID id, String email, String fullName, Role role) {
}
