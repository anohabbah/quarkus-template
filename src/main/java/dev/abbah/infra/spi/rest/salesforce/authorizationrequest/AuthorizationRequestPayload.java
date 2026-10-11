package dev.abbah.infra.spi.rest.salesforce.authorizationrequest;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * The records exchanged with the Salesforce authorization-requests Apex REST endpoint.
 */
public interface AuthorizationRequestPayload {

    record Request(UUID requestId, String requestHash, String type, String requesterEmail, String subject, String description) {
    }

    record Response(@Nullable String caseId, @Nullable String caseNumber) {
    }

    record Error(@Nullable String errorCode, @Nullable String message) {
    }
}
