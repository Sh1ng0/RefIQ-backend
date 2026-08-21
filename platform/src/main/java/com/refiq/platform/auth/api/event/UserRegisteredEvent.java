package com.refiq.platform.auth.api.event;

import java.util.UUID;

/**
 * Represents a domain event emitted upon the successful creation of new credentials.
 * <p>
 * Serves as a contract (logical foreign key) between the Auth module and the User module.
 * </p>
 */
public record UserRegisteredEvent(
    UUID accountId,
    String userName,
    String contactEmail
) {}